#!/usr/bin/env python3
"""Exec an owned Java process with private environment/stdin, without command-line secrets."""
import argparse
import json
import os
from pathlib import Path

from business_config import SERVICES
from report import ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('service', choices=SERVICES)
    parser.add_argument('--java', required=True)
    parser.add_argument('--bootstrap', action='store_true')
    args = parser.parse_args()
    env = json.loads((args.directory / 'environment.json').read_text())
    # Inherit tool paths only; explicitly exclude runner/application-local settings.
    env.update(PATH=os.environ['PATH'], LANG='C.UTF-8')
    argv = [args.java, '-Xmx256m', '-jar', str(ROOT / 'apps/server' / (args.service + '-service') /
            'target' / (args.service + '-service-0.0.1-SNAPSHOT.jar'))]
    if args.bootstrap:
        # UserServiceApplication selects the non-web one-shot lifecycle from argv.
        argv.append('--cherry.auth.mode=bootstrap')
        env.update(CHERRY_AUTH_MODE='bootstrap', CHERRY_AUTH_BOOTSTRAP_USERNAME='ci_admin',
                   MANAGEMENT_ENDPOINT_HEALTH_VALIDATE_GROUP_MEMBERSHIP='false')
        with (args.directory / 'bootstrap-password').open('rb') as password:
            os.dup2(password.fileno(), 0)
    os.execve(args.java, argv, env)


if __name__ == '__main__':
    main()
