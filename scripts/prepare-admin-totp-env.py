#!/usr/bin/env python3
"""Add one stable MFA encryption key privately to the existing reviewed runtime configuration."""
import json, os, re, secrets, shutil, stat, sys, tempfile
from pathlib import Path
from datetime import datetime, timezone
if os.geteuid() != 0:
    raise SystemExit('Run as root on the control server')
os.umask(0o077)
config = Path(sys.argv[1] if len(sys.argv) > 1 else '/root/nsec-agent-channel/compose.repair.json')
def private(path):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != 0 or info.st_mode & 0o077:
        raise SystemExit('STOP: configuration must be a root-owned private regular file')
private(config)
service = json.loads(config.read_text())['services']['backend']
environment = service.get('environment') or {}
if 'ADMIN_TOTP_ENCRYPTION_KEY' in environment:
    if re.fullmatch(r'[a-fA-F0-9]{64}', environment['ADMIN_TOTP_ENCRYPTION_KEY'] or ''):
        print('Existing runtime MFA key retained; nothing changed')
        raise SystemExit(0)
    raise SystemExit('STOP: remove or privately correct the inline MFA key override first')
entries = service.get('env_file') or []
if isinstance(entries, str): entries = [entries]
candidates = []
for entry in entries:
    path = Path(entry if isinstance(entry, str) else entry['path'])
    if not path.is_absolute(): path = config.parent / path
    private(path)
    if any(line.startswith('JWT_SECRET=') for line in path.read_text().splitlines()): candidates.append(path)
if len(candidates) != 1:
    raise SystemExit('STOP: could not identify one protected backend environment file')
path = candidates[0]
lines = path.read_text().splitlines()
indices = [i for i,line in enumerate(lines) if line.startswith('ADMIN_TOTP_ENCRYPTION_KEY=')]
if len(indices) > 1: raise SystemExit('STOP: duplicate MFA configuration entries')
if indices:
    value = lines[indices[0]].split('=', 1)[1]
    if re.fullmatch(r'[a-fA-F0-9]{64}', value):
        print('Existing MFA key retained; nothing changed')
        raise SystemExit(0)
    if value: raise SystemExit('STOP: invalid existing MFA key; no rotation performed')
backup_dir = Path('/root/nsec-backups'); backup_dir.mkdir(mode=0o700, exist_ok=True)
os.chmod(backup_dir, 0o700)
backup = backup_dir / ('backend-before-mfa-' + datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S-%f') + '.env')
shutil.copy2(path, backup); os.chmod(backup, 0o600)
entry = 'ADMIN_TOTP_ENCRYPTION_KEY=' + secrets.token_hex(32)
if indices: lines[indices[0]] = entry
else: lines.append(entry)
with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, delete=False) as stream:
    stream.write('\n'.join(lines) + '\n'); stream.flush(); os.fsync(stream.fileno()); temporary = stream.name
os.chmod(temporary, 0o600); os.replace(temporary, path)
print('MFA encryption key configured privately; no secret printed and no container restarted')
