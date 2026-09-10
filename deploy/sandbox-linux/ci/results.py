"""Require actual completion markers in addition to the command's exit status."""
import json
import re
from report import manifest


def json_lines(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line.startswith('{')]


def linux_units(path):
    events = json_lines(path)
    if any(event.get('Action') in ('skip', 'fail') for event in events):
        raise ValueError('Linux unit suite contains skip/failure')
    packages = {event['Package'].rsplit('/', 1)[-1] for event in events
                if event.get('Action') == 'pass' and 'Test' not in event}
    if packages != {'cgroup', 'helper', 'launcher', 'policy'}:
        raise ValueError('missing Linux unit package')
    tests = {(event.get('Package'), event.get('Test')) for event in events if event.get('Action') == 'pass'}
    for package, required in manifest()['requiredGoTests'].items():
        for name in required:
            if (package, name) not in tests:
                raise ValueError('required Linux test did not execute: ' + package + '/' + name)


def boundary(path):
    text = path.read_text()
    skipped = re.findall(r'--- SKIP: (\S+)', text)
    # TestExecFailureChild returns normally outside child mode; no required suite may skip.
    if skipped or '--- FAIL:' in text:
        raise ValueError('unexpected boundary skip/failure')
    for name in ('TestStartupBoundaries', 'TestOutputPathBoundary', 'TestCgroupCapabilityRefusal', 'TestExecStageFailures'):
        if not re.search(r'^--- PASS: ' + name + r' \(', text, re.MULTILINE):
            raise ValueError('missing boundary completion: ' + name)


def markers(path, *, sentinel=None, field='test', required=()):
    if sentinel and sentinel not in path.read_text().splitlines():
        raise ValueError('missing completion marker: ' + sentinel)
    observed = [event.get(field) for event in json_lines(path)]
    for name in required:
        if name not in observed:
            raise ValueError('missing executed case: ' + str(name))


def chain(path, mode):
    markers(path, sentinel='PASS ' + mode)
    markers(path, field='snapshot', required=('before', 'after'))
    if mode == 'smoke':
        markers(path, required=('compile', 'echo', 'cpu', 'tree', 'memory', 'output', 'empty', 'kill',
                               'nonzero', 'background', 'threads', 'hostfile', 'network', 'mount', 'ptrace',
                               'wall', 'symlink', 'magiclink', 'hardlink', 'zero-cpuNs', 'zero-clockNs',
                               'zero-memoryBytes', 'zero-maxProcesses', 'zero-output-empty',
                               'zero-output-writer', 'after-cancel'))
    elif mode == 'repeat':
        markers(path, required=('1000',))
        markers(path, field='completed', required=(1000,))
    elif mode == 'concurrency':
        markers(path, required=('parallel2',))
    else:
        raise ValueError('unknown chain mode')
