#!/usr/bin/env python3
"""Add a private promo encryption key to the authoritative Compose JSON; no restart."""
import json
import os
import re
import secrets
import stat
import subprocess
import sys
from pathlib import Path
if os.geteuid() != 0 or len(sys.argv) != 2:
    raise SystemExit('Run as root with the absolute authoritative Compose JSON path')
os.umask(0o077)
p = Path(sys.argv[1])
if not p.is_absolute() or p.is_symlink() or not p.is_file():
    raise SystemExit('STOP: an existing regular absolute config file is required')
info = p.stat()
if info.st_uid != 0 or stat.S_IMODE(info.st_mode) & 0o077:
    raise SystemExit('STOP: authoritative config must be root-owned and private')
config = json.loads(p.read_text())
services = config.get('services', {})
name = next((n for n, s in services.items() if s.get('container_name') == 'nsecvpn-backend'), None)
if name is None:
    raise SystemExit('STOP: expected backend container is not in this config')
env = services[name].get('environment', {})
if isinstance(env, list):
    env = dict(item.split('=', 1) for item in env if '=' in item)
if not isinstance(env, dict):
    raise SystemExit('STOP: unsupported environment format')
previous = env.get('PROMO_CODE_ENCRYPTION_KEY')
already_in_config = bool(previous)
if not previous:
    result = subprocess.run(['docker', 'inspect', 'nsecvpn-backend'], capture_output=True, text=True)
    if result.returncode:
        raise SystemExit('STOP: cannot check the running backend key; configuration unchanged')
    current = json.loads(result.stdout)[0].get('Config', {}).get('Env', [])
    previous = next((item.split('=', 1)[1] for item in current if item.startswith('PROMO_CODE_ENCRYPTION_KEY=')), None)
    if previous:
        env['PROMO_CODE_ENCRYPTION_KEY'] = previous
if previous:
    if not re.fullmatch(r'[a-fA-F0-9]{64}', str(previous)):
        raise SystemExit('STOP: existing key format is invalid; never replace a key without reviewing stored codes')
    if already_in_config:
        print('RETAINED: existing private promo encryption key; no containers restarted')
        sys.exit(0)
else:
    result = subprocess.run(['docker', 'exec', 'nsecvpn-postgres', 'sh', '-c',
        'exec psql -X -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \'SELECT count(*) FROM "PromoCode" WHERE "codeCiphertext" IS NOT NULL;\''], capture_output=True, text=True)
    if result.returncode or result.stdout.strip() != '0':
        raise SystemExit('STOP: encrypted codes already exist or migration is missing; retain their original key')
backup = p.with_name(p.name + '.before-promo-key.' + secrets.token_hex(4))
with backup.open('x') as stream:
    stream.write(p.read_text())
os.chmod(backup, 0o600)
env['PROMO_CODE_ENCRYPTION_KEY'] = previous or secrets.token_hex(32)
services[name]['environment'] = env
tmp = p.with_name(p.name + '.promo-key.' + secrets.token_hex(4))
with tmp.open('x') as stream:
    json.dump(config, stream, indent=2)
os.chmod(tmp, 0o600)
os.replace(tmp, p)
print('CONFIGURED: private promo key retained in authoritative config; activates on next deployment')
print('Private configuration backup: ' + str(backup))
