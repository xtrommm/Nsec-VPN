# Backend audit and admin console rollout

This update changes backend/admin only. Existing Android APKs and API field names remain supported. Publish the reviewed branch and wait for GitHub CI, including the real PostgreSQL integration job. The archive does not deploy anything.

## 1. Publish from Windows PowerShell

Extract the fresh ZIP. Find and run `apply-backend-admin-audit.py` with the path to an existing Git project and `--publish`. It creates a separate `Nsec-VPN-backend-admin-audit` checkout, preserves Android/local edits, and pushes only `fix/backend-api-comprehensive-audit`. Click its printed compare URL to create the pull request if GitHub CLI is unavailable. Do not merge before preparing the additive migration below and CI passing.

## 2. Prepare on CloudPanel as root, before merging

```bash
(
set -eu
umask 077
mkdir -p /root/nsec-backups
backup="/root/nsec-backups/before-backend-admin-audit-$(date -u +%Y%m%d-%H%M%S).dump"
docker exec nsecvpn-postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
test -s "$backup"
docker exec -i nsecvpn-postgres pg_restore --list < "$backup" > /dev/null
git -C /opt/nsec-vpn fetch origin fix/backend-api-comprehensive-audit
# Keep an existing directory; use a fresh timestamped path every time.
repair_dir="/opt/nsec-vpn-backend-admin-audit-$(date -u +%Y%m%d-%H%M%S)"
git -C /opt/nsec-vpn worktree add --detach "$repair_dir" FETCH_HEAD
docker exec -i nsecvpn-postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$repair_dir/scripts/migrate-backend-audit.sql"
python3 "$repair_dir/scripts/prepare-admin-totp-env.py"
printf 'Backup validated; additive migration and private MFA key prepared. Source: %s\n' "$repair_dir"
)
```

The only schema change is nullable `AdminUser.totpLastStep INTEGER`; existing rows/data are retained. Apply it even if you will not use MFA, because the new Prisma client reads it. The key helper adds one stable 256-bit ADMIN_TOTP_ENCRYPTION_KEY to the existing root-only backend env file referenced by `/root/nsec-agent-channel/compose.repair.json`. It backs up the file, never prints the key, and never rotates a valid existing key. If your configuration differs it stops; do not paste environment files into chat. New container creation by deployment loads the key; `docker restart` alone does not reload env files.

## 3. Merge, wait for images, approve exact release, deploy

After merge and successful Build Images workflow, run:

```bash
(
set -eu
git -C /opt/nsec-vpn fetch origin main
release_sha=$(git -C /opt/nsec-vpn rev-parse origin/main)
release_dir="/opt/nsec-vpn-backend-admin-release-$(date -u +%Y%m%d-%H%M%S)"
git -C /opt/nsec-vpn worktree add --detach "$release_dir" "$release_sha"
python3 "$release_dir/scripts/approve-backend-audit-schema.py" "$release_sha"
)
```

Approval pulls the exact image, checks its revision/schema and the database column, and approves only that SHA/schema. It does not restart containers. If the image is not found, wait for Build Images; do not bypass approval. Re-run the existing Deploy Production workflow for that merged main revision. Keep the encrypted SSH agent channel and existing private runtime model. Confirm `/ready` and OCI image revisions as in prior deployments.

## 4. Authenticator setup

Sign into the panel, open **Account Security → Set Up Authenticator**, enter the current admin password, and scan the private QR using Google Authenticator/Microsoft Authenticator or another TOTP application. Enter its current six-digit code and choose **Verify & Enable**. The code is generated on the authenticator phone, not emailed. Setup expires after 10 minutes. Keep an encrypted authenticator backup. A consumed code cannot be reused; wait for the next 30-second code before another login. Enabling protection invalidates older sessions without MFA; the enabling session receives a replacement token.

Legacy `env:VARIABLE` TOTP seeds continue to work. Keep the new encryption key stable and securely backed up. Losing or rotating it without re-encrypting seeds locks encrypted accounts out. MFA disable/recovery is deliberately an operator-controlled action, not a public bypass.

## 5. Promo recreation and config editing

Deleting a promo keeps its campaign/redemptions/grants for accounting but frees its text. Creating the same text starts a **new campaign**, with independent usage limits; a device that redeemed the old campaign may redeem the new one. Existing deleted records are archived under lock on reuse. Deletion does not revoke already granted VIP. Existing days/minutes duration storage is unchanged by this update.

Remote Config, Ads & Rewards, and Announcements now have separate tabs. Each saves only its own fields. Editors submit optional `expectedUpdatedAt`; stale saves return CONFIG_CHANGED, requiring a reload instead of overwriting another administrator's changes. Older app/panel clients omitting that field continue to use the existing API.

## 6. Rollback

Keep the schema column on rollback; it is harmless to older code. Before any new encrypted MFA enrollment, the previous image/env backup can be restored using the existing deployment rollback procedure. **After encrypted MFA enrollment, do not revert to an older backend that understands only env-reference seeds**: prefer rolling back the panel while retaining the compatible backend, or perform controlled seed migration/recovery with an operator. Do not restore an old compromised WireGuard key or weaken the schema guard. Never drop the column while the new backend is running. A whole database backup restore would discard later data and requires a planned maintenance window; it is not the routine rollback.

## 7. Runtime database least privilege (separate operator operation)

The checked-in compose initializes the database owner; actual deployed runtime-role privileges are not observable here. `scripts/backend-runtime-role.sql` creates a CRUD-only `nsecvpn_runtime` login and grants future objects created by the current migration owner. Run using the owner, choose its password privately with psql `\password nsecvpn_runtime`, and update DATABASE_URL in the protected runtime env privately. Recreate the backend and verify `/ready`, promo/reward/device/node operations. Keep the original owner credentials only for backups/migrations. Rollback by restoring the protected previous DATABASE_URL and recreating the backend; do not delete live data. Run grants as the actual table/migration owner if different from POSTGRES_USER.

## 8. Testing checklist

- Sign-in: wrong password/code rejected; normal device bearer rejected on admin routes; ADMIN cannot mutate SUPER_ADMIN-only resources; after MFA enrollment old non-MFA sessions fail.
- Promo: create/delete/recreate same text; check old redemption history/VIP survives; invalid/expired/used-up/already redeemed codes show specific errors. Concurrent last-use redemption is tested by CI against PostgreSQL.
- Devices: exact/partial search, blocked device denied, owned recovery/status only; replaying a rotated refresh token revokes its epoch and expires its leases.
- Nodes: VIP/free/name edits retain healthy visibility; endpoint/key edits re-probe; blocked devices remove pending/current peer keys, with uncertain cleanup tracked.
- Config: save Ads, App Controls, Announcements independently; open two tabs, save one, then stale-save the other and confirm CONFIG_CHANGED; live app receives config via its existing refresh behavior.
- Requests: authenticated creation, cooldown, status changes, delete role guard, pagination/search.
- Panel: test narrow/wide screen, both themes, sidebar/drawer, keyboard focus, empty/error tables, destructive confirmations, QR/manual setup, token replacement/logout. Browser screenshots/visual QA were unavailable in the execution environment.
- Backend: readiness, invalid JSON/large-body errors, provider temporary failure 503, no credential/client-IP logging, graceful shutdown. Existing CloudPanel/Cloudflare access-log retention and proxy trust require operator inspection.

Read-only endpoint timings: `python3 scripts/backend-latency.py https://api.nsecvpn.com --samples 20 --output latency-after.json`. Set NSEC_DEVICE_TOKEN/NSEC_ADMIN_TOKEN privately in your shell to include protected reads; never paste them or shell history. The script rejects redirects, does not record bodies or credentials, and reports p50/p95 plus status counts. A baseline must be collected on the previous release under comparable load; historical timing numbers cannot be reconstructed. Do not benchmark state-changing production endpoints repeatedly.

## Local setup

Node 22+, PostgreSQL 16 for integration. Backend: `npm ci`, `npx prisma generate`, `npm run lint`, `npm test -- --runInBand --detectOpenHandles`, `npm run build`. In a dedicated database whose name contains `test` or `_ci`, set DATABASE_URL and a fresh test JWT_SECRET, run `npx prisma migrate deploy`, then `npm run test:integration`. Integration harnesses refuse production-looking database names and create isolated test records. Admin: `npm ci`, `npm run lint`, `npm test`, `npm run build`. New dependency: qrcode (+ development @types/qrcode); QR generation is local. New env: ADMIN_TOTP_ENCRYPTION_KEY; no Android or node-agent update is required.
