#!/usr/bin/env python3
"""Build real Java/Web artifacts with the repository toolchains, without starting services."""
import argparse
import json
import os
from pathlib import Path
import platform

from command import install_signal_handlers, run
from report import ROOT, digest, git_sha, harness_sha
from business_config import SERVICES


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if platform.system() != 'Linux' or os.geteuid() == 0:
        raise RuntimeError('business preparation requires the non-root Linux runner')
    args.output.mkdir(parents=True, exist_ok=False)
    install_signal_handlers()
    for name, argv in [('java', ['java', '--version']), ('node', ['node', '--version']), ('npm', ['npm', '--version'])]:
        run(argv, args.output / (name + '-version.log'), 10)
    # Tests run in the existing jobs; packaging is preparation, not claimed as Java test evidence.
    run(['./mvnw', '-B', '-ntp', 'package', '-DskipTests'], args.output / 'maven-build.log', 600,
        cwd=ROOT / 'apps/server', env=dict(os.environ, MAVEN_OPTS='-Xmx768m'))
    run(['npm', 'ci'], args.output / 'npm-install.log', 180, cwd=ROOT / 'apps/web')
    run(['npm', 'run', 'build'], args.output / 'web-build.log', 180, cwd=ROOT / 'apps/web')
    metadata = dict(sourceSha=git_sha(), harnessSha=harness_sha(),
                    jars={s: digest(ROOT / f'apps/server/{s}-service/target/{s}-service-0.0.1-SNAPSHOT.jar') for s in SERVICES},
                    web={str(p.relative_to(ROOT / 'apps/web/dist')): digest(p)
                         for p in sorted((ROOT / 'apps/web/dist').rglob('*')) if p.is_file()})
    (args.output / 'business-build.json').write_text(json.dumps(metadata, indent=2) + '\n')


if __name__ == '__main__':
    main()
