#!/usr/bin/env python3
"""Approve one reviewed, already-migrated website release; never execute database migrations."""
import hashlib, json, os, re, subprocess, sys
from pathlib import Path
if os.geteuid() != 0 or len(sys.argv) != 2 or not re.fullmatch(r'[a-f0-9]{40}', sys.argv[1]):
    raise SystemExit('Run as root with one full release SHA')
os.umask(0o077)
sha = sys.argv[1]
image = 'ghcr.io/xtrommm/nsec-vpn-backend:sha-' + sha

def run(args):
    result = subprocess.run(args, capture_output=True, check=False)
    if result.returncode:
        raise SystemExit('STOP: approval check failed; no approval written')
    return result.stdout

run(['docker', 'pull', image])
metadata = json.loads(run(['docker', 'image', 'inspect', image]))[0]
labels = metadata.get('Config', {}).get('Labels') or {}
if labels.get('org.opencontainers.image.source') != 'https://github.com/xtrommm/Nsec-VPN' or labels.get('org.opencontainers.image.revision') != sha:
    raise SystemExit('STOP: image provenance does not match the release')
schema = run(['docker', 'run', '--rm', '--user', '0', '--network', 'none', '--entrypoint', 'cat', image, '/app/prisma/schema.prisma'])
query = '''SELECT CASE WHEN (SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='AppConfig' AND column_name IN ('nativeAdsEnabled','bannerAdUnitId','nativeAdUnitId','interstitialAdUnitId','rewardedAdUnitId','rewardedPassMinutes')) = 6 AND to_regclass('public."PromoCode"') IS NOT NULL AND to_regclass('public."PromoRedemption"') IS NOT NULL AND to_regclass('public."RewardTicket"') IS NOT NULL THEN 'MIGRATED' ELSE 'MISSING' END;'''
result = run(['docker', 'exec', 'nsecvpn-postgres', 'sh', '-c',
    'exec psql -X -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"', 'sh', query])
if result.strip() != b'MIGRATED':
    raise SystemExit('STOP: reviewed control-panel migration is not present')
state = Path('/var/lib/nsecvpn-deploy')
state.mkdir(mode=0o700, parents=True, exist_ok=True)
p = state / 'approved-next-schema.json'
tmp = p.with_suffix('.tmp')
tmp.write_text(json.dumps({'sha': sha, 'schemaHash': hashlib.sha256(schema).hexdigest()}))
os.chmod(tmp, 0o600)
os.replace(tmp, p)
print('APPROVED: one exact release/schema; no containers restarted and no database data changed')
