#!/usr/bin/env python3
"""Replace website images while preserving the reviewed, private live Compose model."""
import copy
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time
import urllib.request

CONFIG = Path('/root/nsec-agent-channel/compose.repair.json')
STATE = Path('/var/lib/nsecvpn-deploy')
BACKUPS = Path('/root/nsec-backups')
REGISTRY = 'ghcr.io/xtrommm/nsec-vpn'
SOURCE = 'https://github.com/xtrommm/Nsec-VPN'
SERVICES = ('backend', 'admin-panel')


class DeployError(Exception):
    """Only constant, non-sensitive error codes are exposed."""


def release_sha(value):
    if not re.fullmatch(r'[a-f0-9]{40}', value):
        raise DeployError('INVALID_RELEASE_SHA')
    return value


def private_file(path):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != 0 or info.st_mode & 0o077:
        raise DeployError('PRIVATE_CONFIG_PERMISSIONS_REQUIRED')


def atomic_json(path, value):
    temporary = path.with_name(path.name + '.tmp')
    with temporary.open('w') as stream:
        os.fchmod(stream.fileno(), 0o600)
        json.dump(value, stream)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def command(args, code, *, output=None):
    result = subprocess.run(args, stdout=output if output else subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    if result.returncode:
        # Do not expose Docker/Compose errors: they can contain environment values.
        raise DeployError(code)
    return result.stdout if output is None else b''


def compose_args(path):
    return ['docker', 'compose', '--env-file', '/dev/null', '-p', 'deployment',
            '-f', str(path)]


def resolved_config(path):
    return json.loads(command(compose_args(path) + ['config', '--format', 'json'],
                              'COMPOSE_CONFIG_INVALID'))


def proposed_config(current, backend_digest, admin_digest):
    result = copy.deepcopy(current)
    for service, image in zip(SERVICES, (backend_digest, admin_digest)):
        result['services'][service]['image'] = image
        result['services'][service]['pull_policy'] = 'never'
        result['services'][service].pop('build', None)
    return result


def assert_preserved(before, after):
    left, right = copy.deepcopy(before), copy.deepcopy(after)
    for service in SERVICES:
        for key in ('image', 'pull_policy', 'build'):
            left['services'][service].pop(key, None)
            right['services'][service].pop(key, None)
    if left != right:
        raise DeployError('LIVE_CONFIGURATION_CHANGED')


def verified_digest(metadata, image, sha):
    labels = metadata.get('Config', {}).get('Labels') or {}
    if labels.get('org.opencontainers.image.revision') != sha or labels.get(
            'org.opencontainers.image.source') != SOURCE:
        raise DeployError('IMAGE_PROVENANCE_MISMATCH')
    for digest in metadata.get('RepoDigests', []):
        if re.fullmatch(re.escape(image.split(':sha-')[0]) + r'@sha256:[a-f0-9]{64}', digest):
            return digest
    raise DeployError('IMAGE_DIGEST_MISSING')


def loopback_port(model, service, target):
    for binding in model['services'][service].get('ports', []):
        if (isinstance(binding, dict) and binding.get('target') == target
                and binding.get('host_ip') == '127.0.0.1'
                and binding.get('protocol', 'tcp') == 'tcp'):
            return int(binding['published'])
    raise DeployError('LOOPBACK_WEBSITE_PORT_REQUIRED')


def wait_ready(model, timeout=60):
    api = 'http://127.0.0.1:' + str(loopback_port(model, 'backend', 4000)) + '/ready'
    admin = 'http://127.0.0.1:' + str(loopback_port(model, 'admin-panel', 80)) + '/'
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with opener.open(api, timeout=3) as response:
                health = json.load(response)
            with opener.open(admin, timeout=3) as response:
                good_admin = response.status == 200
            if health.get('status') == 'READY' and health.get('database') == 'CONNECTED' and good_admin:
                return
        except (OSError, ValueError):
            pass
        time.sleep(1)
    raise DeployError('WEBSITE_READINESS_TIMEOUT')


def restore_website(rollback):
    command(compose_args(rollback) + ['up', '-d', '--no-deps', '--no-build',
            '--pull', 'never', *SERVICES], 'WEBSITE_ROLLBACK_FAILED')
    wait_ready(resolved_config(rollback))


def deploy(sha):
    private_file(CONFIG)
    previous = json.loads(CONFIG.read_text())
    before = resolved_config(CONFIG)
    # The channel is already configured separately; a release must never replace it.
    command(['systemctl', 'is-active', '--quiet', 'nsec-agent-channel'], 'AGENT_CHANNEL_OFFLINE')
    baseline = (STATE / 'website-schema.sha256').read_text().strip()
    current_schema = command(['docker', 'exec', '--user', '0', 'nsecvpn-backend', 'cat',
                             '/app/prisma/schema.prisma'], 'CURRENT_SCHEMA_UNAVAILABLE')
    if hashlib.sha256(current_schema).hexdigest() != baseline:
        raise DeployError('CURRENT_SCHEMA_BASELINE_MISMATCH')
    digests = []
    for component in ('backend', 'admin'):
        image = REGISTRY + '-' + component + ':sha-' + sha
        command(['docker', 'pull', image], 'IMAGE_PULL_FAILED')
        metadata = json.loads(command(['docker', 'image', 'inspect', image],
                                      'IMAGE_INSPECTION_FAILED'))[0]
        digests.append(verified_digest(metadata, image, sha))
    candidate_schema = command(['docker', 'run', '--rm', '--user', '0', '--network', 'none',
        '--entrypoint', 'cat', digests[0], '/app/prisma/schema.prisma'], 'RELEASE_SCHEMA_UNAVAILABLE')
    if hashlib.sha256(candidate_schema).hexdigest() != baseline:
        # Existing production was upgraded manually. Never re-run an absent baseline
        # migration or db push; schema changes require a reviewed migration first.
        raise DeployError('REVIEWED_DATABASE_MIGRATION_REQUIRED')

    candidate = CONFIG.with_name('compose.next.json')
    rollback = CONFIG.with_name('compose.rollback.json')
    atomic_json(candidate, proposed_config(previous, *digests))
    assert_preserved(before, resolved_config(candidate))
    atomic_json(rollback, previous)
    stamp = time.strftime('%Y%m%d-%H%M%S', time.gmtime())
    backup = BACKUPS / ('website-' + sha + '-' + stamp + '.dump')
    with backup.open('xb') as stream:
        os.fchmod(stream.fileno(), 0o600)
        command(['docker', 'exec', 'nsecvpn-postgres', 'sh', '-c',
                 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc'],
                'DATABASE_BACKUP_FAILED', output=stream)
    if backup.stat().st_size == 0:
        raise DeployError('DATABASE_BACKUP_EMPTY')
    with backup.open('rb') as stream:
        result = subprocess.run(['docker', 'exec', '-i', 'nsecvpn-postgres',
            'pg_restore', '--list'], stdin=stream, stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL, check=False)
    if result.returncode:
        raise DeployError('DATABASE_BACKUP_INVALID')

    try:
        command(compose_args(candidate) + ['up', '-d', '--no-deps', '--no-build',
                '--pull', 'never', *SERVICES], 'WEBSITE_START_FAILED')
        wait_ready(resolved_config(candidate))
        atomic_json(CONFIG, json.loads(candidate.read_text()))
    except (DeployError, OSError, ValueError):
        restore_website(rollback)
        atomic_json(CONFIG, previous)
        raise DeployError('RELEASE_FAILED_PREVIOUS_WEBSITE_RESTORED') from None
    atomic_json(STATE / 'website-release.json', {'sha': sha, 'backend': digests[0],
                                              'admin': digests[1], 'timestamp': stamp})
    temporary = STATE / 'current.env.tmp'
    temporary.write_text('NSEC_IMAGE_TAG=sha-' + sha + '\n')
    os.chmod(temporary, 0o600)
    os.replace(temporary, STATE / 'current.env')
    print('DEPLOYED: ' + sha + '; backend and admin ready; database and credentials preserved')


def main():
    os.umask(0o077)
    try:
        if os.geteuid() != 0 or len(sys.argv) != 2:
            raise DeployError('ROOT_AND_ONE_RELEASE_SHA_REQUIRED')
        sha = release_sha(sys.argv[1])
        STATE.mkdir(mode=0o700, parents=True, exist_ok=True)
        BACKUPS.mkdir(mode=0o700, parents=True, exist_ok=True)
        with (STATE / 'website-deploy.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            deploy(sha)
    except DeployError as error:
        print('DEPLOYMENT_STOPPED: ' + str(error), file=sys.stderr)
        return 1
    except Exception:
        # Never print exception values, config models, SQL results or stack traces.
        print('DEPLOYMENT_STOPPED: INTERNAL_DEPLOYMENT_ERROR', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
