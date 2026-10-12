#!/usr/bin/env python3
"""Install the restricted website deployer without restarting application services."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import pwd
import re
import shlex
import shutil
import subprocess
import sys
import time

ENTRY = '/usr/local/bin/nsecvpn-website-entry'
CONTROLLER = '/usr/local/sbin/nsecvpn-deploy-website'
CONFIG = Path('/root/nsec-agent-channel/compose.repair.json')
STATE = Path('/var/lib/nsecvpn-deploy')


def replace_deployment_key(text, legacy_command):
    result, changed = [], 0
    for line in text.splitlines():
        match = re.search(r'command="((?:\\.|[^"\\])*)"', line)
        if match:
            try:
                tokens = shlex.split(match.group(1))
            except ValueError:
                tokens = []
            if legacy_command in tokens or ENTRY in tokens:
                line = line[:match.start()] + 'command="' + ENTRY + '"' + line[match.end():]
                if not re.match(r'(?:[^ ]*,)?restrict(?:,| )', line):
                    line = 'restrict,' + line
                changed += 1
        result.append(line)
    if changed != 1:
        raise ValueError('Exactly one matching forced deployment key is required')
    return '\n'.join(result) + '\n'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--deploy-user', required=True)
    parser.add_argument('--legacy-command', required=True)
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise SystemExit('Run as root')
    if not re.fullmatch(r'[a-z_][a-z0-9_-]*', args.deploy_user):
        raise SystemExit('Invalid deployment user')
    if not args.legacy_command.startswith('/'):
        raise SystemExit('Supply the existing forced-command executable path')
    os.umask(0o077)
    info = pwd.getpwnam(args.deploy_user)
    authorized = Path(info.pw_dir) / '.ssh' / 'authorized_keys'
    original = authorized.read_text()
    updated = replace_deployment_key(original, args.legacy_command)
    if CONFIG.stat().st_uid != 0 or CONFIG.stat().st_mode & 0o077:
        raise SystemExit('Private live Compose configuration must be root-owned, mode 600')
    model = json.loads(CONFIG.read_text())
    if set(('backend', 'admin-panel', 'postgres')) - set(model.get('services', {})):
        raise SystemExit('Live Compose configuration is incomplete')
    schema = subprocess.run(['docker', 'exec', '--user', '0', 'nsecvpn-backend', 'cat',
        '/app/prisma/schema.prisma'], capture_output=True, check=False)
    if schema.returncode:
        raise SystemExit('Cannot record the running backend schema')

    STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chown(STATE, 0, 0)
    os.chmod(STATE, 0o700)
    baseline = STATE / 'website-schema.sha256'
    fingerprint = hashlib.sha256(schema.stdout).hexdigest()
    if baseline.exists() and baseline.read_text().strip() != fingerprint:
        raise SystemExit('Existing schema approval differs; review the migration before approval')
    backups = Path('/root/nsec-backups')
    backups.mkdir(mode=0o700, parents=True, exist_ok=True)
    stamp = time.strftime('%Y%m%d-%H%M%S', time.gmtime())
    backup = backups / ('website-authorized-keys-' + stamp)
    backup.write_text(original)
    os.chmod(backup, 0o600)
    directory = Path(__file__).resolve().parent
    for source, target, mode in (
        ('deploy-website.py', CONTROLLER, 0o700),
        ('website-deploy-entry.py', ENTRY, 0o755),
    ):
        target_path = Path(target)
        if target_path.exists():
            shutil.copyfile(target_path, backups / (target_path.name + '-' + stamp))
        shutil.copyfile(directory / source, target_path)
        os.chown(target_path, 0, 0)
        os.chmod(target_path, mode)
    if info.pw_uid != 0:
        sudoers = Path('/etc/sudoers.d/nsecvpn-website-v2')
        if sudoers.exists():
            shutil.copyfile(sudoers, backups / ('website-sudoers-' + stamp))
        candidate_sudoers = backups / ('website-sudoers-candidate-' + stamp)
        candidate_sudoers.write_text(args.deploy_user + ' ALL=(root) NOPASSWD: ' + CONTROLLER + ' *\n')
        os.chmod(candidate_sudoers, 0o440)
        if subprocess.run(['visudo', '-cf', str(candidate_sudoers)], stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL, check=False).returncode:
            raise SystemExit('Sudoers validation failed; deployment key unchanged')
        shutil.copyfile(candidate_sudoers, sudoers)
        os.chown(sudoers, 0, 0)
        os.chmod(sudoers, 0o440)
    temporary = authorized.with_name('authorized_keys.website-v2')
    temporary.write_text(updated)
    os.chown(temporary, authorized.stat().st_uid, authorized.stat().st_gid)
    os.chmod(temporary, 0o600)
    os.replace(temporary, authorized)
    # An update of this installer must not silently approve a different schema.
    baseline.write_text(fingerprint + '\n')
    os.chmod(baseline, 0o600)
    print('INSTALLED: restricted deploy-v2 handler; website containers were not restarted')


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, KeyError):
        raise SystemExit('INSTALLATION_STOPPED: check deployment user, forced-command path and private configuration') from None
