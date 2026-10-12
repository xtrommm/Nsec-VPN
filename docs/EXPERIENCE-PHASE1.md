# Admin experience and Remote Config: phase 1

Source baseline: `31d2a68aaa7d5bf58564ad9e498743801cbeaf24`. Read [the source audit](EXPERIENCE-AUDIT.md) first. This release implements the requested admin/promotions work and Remote Config/Ads refresh. Android navigation, support/request UI, DNS, installation ID, real server caching, Home cleanup and honest troubleshooting are being prepared separately. The browser speed test is not replaced in this phase. The user selected a separately installed, matching-signature helper package for the subsequent built-in VPN speed test; no helper APK is included yet.

## Implemented

- Responsive dark/light console with sidebar, profile/logout, server health overview, toast feedback, focus-contained dialogs and loading/empty/error states.
- Promotions: day-based creation, custom/generated and bulk codes (1–100), start/expiry scheduling, edits, enable/disable, delete confirmation, remaining uses, searchable paginated usage records and authenticated copy.
- Delete creates an irreversible tombstone. Redemption history and the code hash remain to prevent reuse/reset exploits. Existing VIP is not revoked. Deleted codes cannot be recreated.
- New codes use AES-256-GCM encryption and a dedicated environment key for later copying. Existing code hashes remain valid but cannot be reversed. Enter the original matching code in Edit to enable later copying. Codes themselves are not included in audit metadata or logs.
- A finite VIP pass is extended by the exact promotion duration. Lifetime VIP returns `PROMO_ALREADY_VIP` before consuming a use. Expired/disabled/deleted/not-started/exhausted/redeemed campaigns never grant access.
- Server-request API with authenticated owner binding, registered app version, plain-text validation, 10-minute database cooldown, admin status management and confirmed deletion. App submission UI follows in the next phase.
- Device lookup supports partial ID/model search and exact ID lookup, associated promo records and requests. Updates return only safe selected fields, never registration hashes.
- Server editing uses an explicit DTO and validation. Enable/disable and complete-list reordering are backed by transactions. Deletion disables the node before agent cleanup; failure keeps it disabled with a clear pending-cleanup error.
- Audits/search/export handle async failures. Most administrative database mutations and audit records commit together. Counts exclude expired VIP and peer leases.
- Remote Config writes only approved fields, validates client/server, records publish time, refreshes correctly and adds a global ad switch and configurable support address. A disabled global switch also stops new rewarded-ticket preparation. Existing signed SSV events remain eligible for verification.
- Android Remote Config periodically refreshes, accepts only successful validated responses, retains previously verified settings locally, reports refresh failures and observes the global ad switch. Production IDs remain remote-configurable; existing SDK constants, manifest application ID and signing configuration are untouched. Debug builds keep the existing Google test units.
- Maintenance prevents new backend peer allocation. Android checks verified settings before a manual connect; minimum-version/forced-update controls are enforced at that point. Existing tunnels are not forcibly revoked by maintenance.

## Exact, backward-compatible migration

`backend/prisma/migrations/20261012120000_experience/migration.sql` is the additive Prisma migration for databases already tracked by Prisma. On the actual server previously initialized without migration history, use `scripts/migrate-experience.sql`. Do not run `prisma db push`, reset the database or replay baseline migrations.

`PromoCode.passDays` is decimal, backfilled as `passMinutes / 1440`. A database trigger keeps both columns consistent when an older server writes minute-based codes during rollout. `passMinutes` remains the canonical exact redemption duration and is retained for older consoles/API consumers. For example, 30 minutes remains 30 minutes and displays as approximately 0.02083333 days. New consoles create whole-day campaigns; unchanged legacy durations stay untouched. Explicit duration edits affect future redemptions, never existing passes.

New columns: `PromoCode.passDays/codeCiphertext/startsAt/deletedAt`; `PromoRedemption.grantExpiresAt/durationMinutes`; `AppConfig.adsEnabled/supportEmail`; `ServerNode.sortOrder`. New `ServerRequest` model/status enum and lookup indexes. No client IP, browsing or DNS-query fields are introduced. Existing redemptions whose grant expiry was never recorded display "Legacy expiry unavailable"; no expiry is invented.

## Local checks

```bash
cd backend
npm ci
npm run prisma:generate
npm run lint
npm test -- --runInBand
npm run build
cd ../admin-panel
npm ci
npm run lint
npm test
npm run build
cd ..
python3 -m unittest discover -s tests/deployment -q
./gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug --no-daemon
```

Optional browser smoke test: install Playwright in your development environment, install its Chromium browser, run `npm run build` and `npm run preview -- --host 127.0.0.1 --port 4173` in the panel. With `SHOT_DIR` set to a private temporary directory, run `node admin-panel/tests/browser-smoke.cjs`. Responses in that test are explicitly intercepted fixtures; they never ship in the panel or seed production. Browser smoke could not run in this environment because browser UNIX sockets are restricted. Android SDK/JDK installation was attempted, but the SDK manager could not download its required CLI; Android build/lint/device verification remains a CI requirement, not a claimed pass.

## CloudPanel rollout (after the branch is pushed and CI passes)

Use the CloudPanel terminal, not Windows PowerShell or the Alibaba node. Preserve `/root/nsec-agent-channel/compose.repair.json`, the SSH agent channel and PostgreSQL. No node reinstall is required.

1. Fetch the proposed branch and create an isolated worktree:

```bash
git -C /opt/nsec-vpn fetch origin fix/admin-experience-phase1
git -C /opt/nsec-vpn worktree add -b fix/admin-experience-server \
  /opt/nsec-vpn-experience FETCH_HEAD
```

2. Back up and validate the database archive, then migrate:

```bash
(
set -eu
umask 077
mkdir -p /root/nsec-backups
backup="/root/nsec-backups/before-experience-$(date -u +%Y%m%d-%H%M%S).dump"
docker exec nsecvpn-postgres sh -c \
  'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
test -s "$backup"
docker exec -i nsecvpn-postgres pg_restore --list < "$backup" > /dev/null
docker exec -i nsecvpn-postgres sh -c \
  'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < /opt/nsec-vpn-experience/scripts/migrate-experience.sql
python3 /opt/nsec-vpn-experience/scripts/configure-promo-encryption.py \
  /root/nsec-agent-channel/compose.repair.json
)
```

The configuration script retains an existing valid key, creates one privately only when safe, saves a root-only configuration backup and never restarts containers. It refuses to invent a replacement key if encrypted codes exist. Standard deployments must set `PROMO_CODE_ENCRYPTION_KEY` themselves (64 hex characters from a secure generator). Never paste its value into chat, screenshots or GitHub.

3. Merge the reviewed, green PR. Main CI publishes SHA-labelled images. Approve the exact full merged SHA after image publication:

```bash
python3 /opt/nsec-vpn-experience/scripts/approve-experience-schema.py FULL_MERGED_MAIN_SHA
```

The script checks provenance and actual migrated database columns/tables/duration consistency before approving that single schema/release. If automatic deployment stopped at `REVIEWED_DATABASE_MIGRATION_REQUIRED`, rerun **failed jobs only** after approval. The existing v2 deployment handler and secret/network preservation continue unchanged.

4. Check `/ready`, sign in to the console again and publish the real production ad unit IDs and support email. Install the updated Android build only after its CI and backend rollout pass.

## API additions and compatibility

All existing app paths remain. Promo redemption retains `{success,data:{tier,passExpiresAt}}`. Older app versions may show a generic error for the new specific promo errors until updated.

| Route | Change |
| --- | --- |
| GET /api/v1/admin/promos | Validated `page/limit/search/sort/status`; numeric days, status, remainingUses and codeAvailable; pagination. Default page size is now 20, so older admin consoles show only that page. |
| POST /api/v1/admin/promos | Accepts whole `passDays` or legacy exact `passMinutes`; optional startsAt; encryption key required. |
| POST /api/v1/admin/promos/bulk | 1–100 generated codes; whole days. |
| PUT /api/v1/admin/promos/:id | Label/duration/limits/dates/active; original matching code can restore encrypted retrieval. Hash/code identity cannot be changed. |
| DELETE /api/v1/admin/promos/:id | Tombstone and disable; history retained. |
| GET /api/v1/admin/promos/:id/code | Super Admin only; no-store, audited; raw code returned solely for deliberate copying. |
| GET /api/v1/admin/promos/:id/redemptions | Paginated device IDs, redemption dates and recorded grant expiry. |
| POST /api/v1/server-requests | Authenticated device; country/city/optional note. Owner and app version come from auth/database. |
| GET/PUT/DELETE /api/v1/admin/server-requests | Search/filter/page, status change, Super Admin deletion. |
| GET /api/v1/admin/devices | Validated pagination/search/sort, exact=true. |
| GET /api/v1/admin/devices/:installationId/details | Device info, latest 50 promo records and requests. |
| PUT /api/v1/admin/servers-order | Complete unique node ID list, transactional order; stale list returns 409. |
| GET /api/v1/admin/me | Authenticated current profile/role. |
| GET /api/v1/admin/audit-logs | Search/sort/pagination; credential-bearing historical metadata is not returned. |
| PUT /api/v1/admin/config | Adds adsEnabled/supportEmail; cross-field validation and audit atomic. |
| GET /api/v1/config | Adds updatedAt, support.email, ads.enabled; effective placement flags respect global switch for older clients too. HTTP no-store, bounded 5-second process cache. |
| POST /api/v1/vpn/connect | MAINTENANCE_MODE (503) prevents new allocation. |

New specific errors: `PROMO_NOT_STARTED`, `PROMO_ALREADY_VIP`, `PROMO_ENCRYPTION_NOT_CONFIGURED`, `PROMO_CODE_UNAVAILABLE`, `PROMO_LEGACY_CODE_UNAVAILABLE`, `PROMO_CODE_IMMUTABLE`, `PROMO_LIMIT_BELOW_USAGE`, `PROMO_DATE_RANGE_INVALID`, `PROMO_DURATION_CONFLICT`, `PROMO_EXPIRY_INVALID`, `PROMO_NOT_FOUND`, `PROMOS_UNAVAILABLE`, `PROMO_CREATE_FAILED`, `PROMO_UPDATE_FAILED`, `PROMO_DELETE_FAILED`, `PROMO_USAGE_UNAVAILABLE`, `REQUEST_COOLDOWN` (429 + Retry-After), `REQUEST_NOT_FOUND`, `REQUESTS_UNAVAILABLE`, `REQUEST_UPDATE_FAILED`, `REQUEST_DELETE_FAILED`, `REQUEST_SUBMISSION_FAILED`, `INVALID_QUERY`, `INVALID_DEVICE_ID`, `DEVICES_UNAVAILABLE`, `DEVICE_NOT_FOUND`, `DEVICE_DETAILS_UNAVAILABLE`, `DEVICE_UPDATE_FAILED`, `SERVER_ORDER_STALE`, `SERVER_ORDER_FAILED`, `NODE_PEER_REMOVAL_PENDING`, `AUDIT_UNAVAILABLE`, `EXPORT_UNAVAILABLE`, `CONFIG_SAVE_FAILED`, `MAINTENANCE_MODE`. Existing validation/config/version/entitlement errors retain their meaning. Infrastructure failures return 503 rather than masquerading as missing records.

## Acceptance checklist across requested items

- [ ] Admin: all tabs, login/TOTP, role restriction, dark/light/mobile, search/sort/page, loading/error/retry, confirms and logout stale-response handling.
- [ ] Promos: custom, bulk, schedule, expiry, exact migrated minutes, edit, copy, delete, usage; duplicate/concurrent/device/capacity failures; VIP expiry extends correctly and billing refresh preserves recorded promo grant.
- [ ] Ads/config: load/retry/edit/save/reopen; global off, each placement off, real release IDs, debug test IDs, interval, live app refresh and last-updated display. Signed SSV and immediate client claims keep their existing trust trade-off.
- [ ] Item 1: shared navigation on connected/disconnected/reconnect, correct selected tab and back behavior (next app phase).
- [ ] Item 2: support mail/copy; request validation/cooldown/success/error and admin visibility (API/admin ready; app UI next phase).
- [ ] Item 3: persisted Automatic/providers/custom; actual active DNS and connected change; DNS leak test in included browser. IPv6-only resolvers require actual IPv6 node support (next app phase).
- [ ] Item 4: stable installation ID matches admin lookup; copy confirmation (next app phase).
- [ ] Item 5: separately signed helper installed and included, actual VPN network + exit-IP verification, ping/jitter/download/upload/gauge/cancel/timeouts/history; no browser; ads remain on ordinary route (not implemented in this phase).
- [ ] Item 6: cached real catalog visible before refresh, offline state, stale-node API rejection, lazy probes and perceived-load timing on the phone (next app phase; no fabricated <500ms benchmark).
- [ ] Item 7: duplicate Home location/IP header removed; status screen retains verified node IP (next app phase).
- [ ] Troubleshooting: no unconditional PASS; verified handshake/counters/API checks and explicitly unmeasured DNS/MTU (next app phase).

## Suggestions, not added

Separate paid/reward/promo entitlement records would improve revocation and accounting beyond the current shared tier/expiry model. Hardware-backed registration integrity can reduce reinstall farming; per-install limits do not prevent new installations. Admin 2FA enrollment UI, monitoring/alerts and data-retention settings require product choices. No extra feature is enabled without a decision.
