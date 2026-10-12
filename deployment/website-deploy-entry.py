#!/usr/bin/env python3
"""Forced SSH command; no shell, arbitrary arguments, or legacy deployment."""
import os
import re
import shlex
import sys

CONTROLLER = '/usr/local/sbin/nsecvpn-deploy-website'


def parse_command(value):
    try:
        parts = shlex.split(value)
    except ValueError:
        raise ValueError('INVALID_DEPLOY_COMMAND') from None
    if len(parts) != 2 or parts[0] != 'deploy-v2' or not re.fullmatch(r'[a-f0-9]{40}', parts[1]):
        raise ValueError('INVALID_DEPLOY_COMMAND')
    return parts[1]


def main():
    try:
        sha = parse_command(os.environ.get('SSH_ORIGINAL_COMMAND', ''))
    except ValueError:
        print('Only deploy-v2 followed by a release SHA is permitted.', file=sys.stderr)
        return 126
    if os.geteuid() == 0:
        os.execv(CONTROLLER, [CONTROLLER, sha])
    os.execv('/usr/bin/sudo', ['sudo', '-n', '--', CONTROLLER, sha])


if __name__ == '__main__':
    sys.exit(main())
