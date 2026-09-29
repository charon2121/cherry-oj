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
    # 期望的包集合从清单派生，不再单独抄一份：抄两份就会各自漂移，
    # 而漂移的表现是「测试搬了家，两边都还以为跑过」。
    packages = {event['Package'] for event in events
                if event.get('Action') == 'pass' and 'Test' not in event}
    expected = set(manifest()['requiredGoTests'])
    if packages != expected:
        raise ValueError('missing Linux unit package: ' +
                         ', '.join(sorted(expected - packages) or sorted(packages - expected)))
    tests = {(event.get('Package'), event.get('Test')) for event in events if event.get('Action') == 'pass'}
    for package, required in manifest()['requiredGoTests'].items():
        for name in required:
            if (package, name) not in tests:
                raise ValueError('required Linux test did not execute: ' + package + '/' + name)


def executor_suite(path):
    """执行器自己的真实内核测试（unittest -v）：每个必需用例都要实际通过，不允许 skip 或失败。"""
    text = path.read_text()
    if re.search(r'\.\.\. (skipped|FAIL|ERROR)', text) or not re.search(r'^OK$', text, re.MULTILINE):
        raise ValueError('executor suite contains skip/failure or did not finish')
    for name in ('test_identity_and_filesystem', 'test_invalid_requests_are_refused', 'test_stale_group_is_reclaimed',
                 'test_missing_command_is_platform_failure', 'test_artifacts_are_bounded_regular_files',
                 'test_links_cannot_be_created'):
        if not re.search(r'^' + name + r' .*\.\.\. ok$', text, re.MULTILINE):
            raise ValueError('missing executor suite completion: ' + name)


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
