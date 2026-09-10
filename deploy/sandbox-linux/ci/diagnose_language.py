#!/usr/bin/env python3
"""Measure the existing five-second language tests; never turn failed samples into passes."""
import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import resource
import shlex
import shutil
import signal
import subprocess
import tempfile
import time
import zipfile

from command import install_signal_handlers, run

ROOT = Path(__file__).resolve().parents[3]
ENGINE = ROOT / 'apps/judge-engine'
SOURCE = ENGINE / 'internal/judge/language/languages_e2e_test.go'
USAGE = re.compile(r'compile language=java usage=\{ExitCode:(-?\d+) Signal:(\d+) CPUNs:(\d+) '
                   r'MemoryBytes:(\d+) ClockNs:(\d+) Reason:(\w*) .*?\} waitError=(.*)')


def java_usage(text):
    matches = USAGE.findall(text)
    if len(matches) != 1:
        raise ValueError('expected exactly one Java compile observation')
    values = matches[0]
    keys = ('exitCode', 'signal', 'cpuNs', 'memoryBytes', 'clockNs')
    return dict(zip(keys, map(int, values[:5])), reason=values[5], waitError=values[6])


def java_source():
    text = SOURCE.read_text()
    section = text.split('func TestEndToEndJavaWithInnerClass(t *testing.T)', 1)[1]
    return section.split('src := `', 1)[1].split('`', 1)[0]


def optional_file(name):
    path = Path(name)
    return path.read_text()[:16384] if path.is_file() else None


def pressure():
    return {name: optional_file('/proc/pressure/' + name) for name in ('cpu', 'io', 'memory')}


def reap_children():
    """Only this diagnostic's adopted children; unreaped child PIDs cannot be reused."""
    reaped = 0
    deadline = time.monotonic() + 3
    while True:
        while True:
            try:
                pid, _ = os.waitpid(-1, os.WNOHANG)
            except ChildProcessError:
                return reaped
            if pid == 0:
                break
            reaped += 1
        children = Path(f'/proc/{os.getpid()}/task/{os.getpid()}/children').read_text().split()
        for pid in children:
            try:
                os.kill(int(pid), signal.SIGKILL)
            except ProcessLookupError:
                pass
        if time.monotonic() >= deadline:
            raise RuntimeError('diagnostic children did not exit')
        time.sleep(.01)


def measure(argv, log, timeout, *, cwd, env=None):
    before = resource.getrusage(resource.RUSAGE_CHILDREN)
    started = time.monotonic()
    record = dict(log=log.name, pressureBefore=pressure())
    try:
        run(argv, log, timeout, cwd=cwd, env=env)
        record['completed'] = True
    except (RuntimeError, TimeoutError) as error:
        record.update(completed=False, error=str(error))
    after = resource.getrusage(resource.RUSAGE_CHILDREN)
    record.update(wallSeconds=time.monotonic() - started,
                  cpuSeconds=after.ru_utime + after.ru_stime - before.ru_utime - before.ru_stime,
                  pressureAfter=pressure())
    return record


def wrappers(directory, tools, *, delayed=False, base_env=None):
    directory.mkdir()
    for name in ('javac', 'jar'):
        # Only the compiler/archiver JVM receives the candidate flag, never the user program.
        flag = '' if delayed else ' -J-XX:TieredStopAtLevel=1'
        delay = 'sleep 6\n' if delayed and name == 'javac' else ''
        path = directory / name
        path.write_text('#!/bin/sh\n' + delay + 'exec ' + shlex.quote(tools[name]) + flag + ' "$@"\n')
        path.chmod(0o700)
    env = dict(os.environ if base_env is None else base_env)
    env['PATH'] = str(directory) + os.pathsep + env['PATH']
    return env


def sample(binary, output, label, env=None, *, all_languages=False):
    pattern = '^TestEndToEnd' if all_languages else '^TestEndToEndJavaWithInnerClass$'
    log = output / (label + '.log')
    item = measure([binary, '-test.v', '-test.count=1', '-test.run=' + pattern], log, 30,
                   cwd=ENGINE, env=env)
    text = log.read_text()
    item.update(label=label, usage=java_usage(text))
    item['passed'] = item['completed'] and '--- SKIP:' not in text and '--- FAIL:' not in text
    return item


def stages(work, output, label, tools, candidate):
    directory = work / label
    directory.mkdir()
    (directory / 'Main.java').write_text(java_source())
    flags = ['-J-XX:TieredStopAtLevel=1'] if candidate else []
    javac = measure([tools['javac'], *flags, 'Main.java'], output / (label + '-javac.log'), 5,
                    cwd=directory)
    record = dict(label=label, javac=javac)
    if not javac['completed']:
        return record
    classes = sorted(directory.glob('*.class'))
    record['classes'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in classes}
    if not {'Main.class', 'Main$Pair.class'}.issubset(record['classes']):
        raise ValueError('compiler omitted the inner class')
    jar = measure([tools['jar'], *flags, 'cf', 'Main.jar', *[p.name for p in classes]],
                  output / (label + '-jar.log'), 5, cwd=directory)
    record.update(jar=jar, combinedWallSeconds=javac['wallSeconds'] + jar['wallSeconds'])
    if jar['completed']:
        with zipfile.ZipFile(directory / 'Main.jar') as archive:
            for name, digest in record['classes'].items():
                entry = archive.getinfo(name)
                if entry.file_size > 1 << 20 or hashlib.sha256(archive.read(entry)).hexdigest() != digest:
                    raise ValueError('jar changed or omitted a compiled class')
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--replica', type=int, choices=(1, 2, 3), required=True)
    args = parser.parse_args()
    if platform.system() != 'Linux' or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise RuntimeError('diagnostic execution requires a disposable GitHub Linux VM')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    install_signal_handlers()
    # Go host commands create their own process groups. Adopt and reclaim any orphan
    # even if the outer diagnostic cancels the Go test process before its defers run.
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(36, 1, 0, 0, 0) != 0:  # PR_SET_CHILD_SUBREAPER, applies only to this process.
        raise OSError(ctypes.get_errno(), 'cannot own diagnostic descendants')
    report = dict(sourceSha=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                  replica=args.replica, testDeadlineSeconds=5, candidateAdopted=False,
                  diagnosticCompleted=False, baselinePassed=False, samples=[], stages=[], cleanup=False)
    tools = {name: shutil.which(name) for name in ('java', 'javac', 'jar', 'go')}
    if not all(tools.values()):
        raise RuntimeError('required toolchain missing')
    report['environment'] = dict(kernel=platform.release(), architecture=platform.machine(),
                                 image=os.environ.get('ImageVersion'), cpuCount=os.cpu_count(),
                                 cpuQuota=optional_file('/sys/fs/cgroup/cpu.max'),
                                 memory=optional_file('/proc/meminfo'), tools=tools,
                                 resolvedTools={k: str(Path(v).resolve()) for k, v in tools.items()},
                                 javaOptionsPresent={k: bool(os.environ.get(k)) for k in
                                                     ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')})
    work = None
    try:
        with tempfile.TemporaryDirectory(prefix='cherry-language-') as temporary:
            work = Path(temporary)
            host_temp = work / 'host'
            host_temp.mkdir()
            baseline_env = dict(os.environ, TMPDIR=str(host_temp))
            binary = work / 'language.test'
            run(['go', 'test', '-race', '-c', '-o', binary, './internal/judge/language'],
                output / 'build.log', 180, cwd=ENGINE, env=baseline_env)
            # No Java version/probe/prewarm before the first real test on this VM.
            report['samples'].append(sample(binary, output, 'first-original', baseline_env, all_languages=True))
            candidate = wrappers(work / 'candidate', tools, base_env=baseline_env)
            order = ('original', 'tier1') if args.replica % 2 else ('tier1', 'original')
            for number in range(2):
                for variant in order:
                    report['samples'].append(sample(binary, output, f'{variant}-{number}',
                                                     candidate if variant == 'tier1' else baseline_env))
            for variant in order:
                report['stages'].append(stages(work, output, 'stages-' + variant, tools, variant == 'tier1'))
            hashes = [item['classes'] for item in report['stages'] if 'classes' in item]
            report['identicalClasses'] = len(hashes) == 2 and hashes[0] == hashes[1]
            # Deliberate negative control: prove the unchanged five-second deadline and diagnostics work.
            delayed = wrappers(work / 'delayed', tools, delayed=True, base_env=baseline_env)
            control = sample(binary, output, 'deadline-control', delayed)
            report['deadlineControl'] = control
            if control['passed'] or control['usage']['reason'] != 'wall' or control['usage']['signal'] != 9:
                raise RuntimeError('deadline negative control failed')
            for name in ('java', 'javac', 'jar', 'go'):
                flag = 'version' if name == 'go' else '--version'
                run([tools[name], flag], output / (name + '-version.log'), 5, cwd=work)
            baseline = [r for r in report['samples'] if not r['label'].startswith('tier1')]
            report['baselinePassed'] = all(r['passed'] for r in baseline)
            report['diagnosticCompleted'] = True
    finally:
        try:
            report['reapedDescendants'] = reap_children()
            report['cleanup'] = work is None or not work.exists()
        finally:
            (output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: report[k] for k in ('diagnosticCompleted', 'baselinePassed', 'cleanup')}))
    if not report['baselinePassed'] or not report['cleanup']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
