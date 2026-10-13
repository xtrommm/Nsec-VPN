# Security and core repair checkpoint — 2026-10-13

Branch: `fix/deep-audit-security-core`. Audit base: `89a2fc3a19a51944ade35429d6e747a2410141c0`. Publication base: `6eef29f96a2333f05c29a823dd32219c93217949`.

The newer main commit changes only `.gitignore` from `/build` to `build/`. It was reviewed and merged into the repair branch without rewriting the existing repair commits. The v2 package preserves this change and updates the publication guard accordingly. Pushing a branch does not create a PR unless authenticated GitHub CLI is installed; otherwise use the exact comparison link printed after a successful push.

The full audit is [DEEP-AUDIT-2026-10-13.md](DEEP-AUDIT-2026-10-13.md). This checkpoint is not completion of the whole requested audit/fix/features programme. It contains reviewed repairs and clearly identified outstanding work. No deployment or main-branch update was performed. GitHub branch creation was rejected with HTTP 403, `Resource not accessible by integration`; a local Git bundle and publisher preserve the conventional commits for the owner's authenticated Git client.

## Findings addressed

| Findings | Result |
|---|---|
| A01 | Client reward claims never grant VIP. Signed SSV consumes the owned ticket, enforces event time, duration, cooldown and replay protection. Claims return 202 until verified; local SDK-earned ad-free time remains immediate. The app polls for up to 30 seconds and retains pending claims. Configure actual AdMob SSV before rollout. |
| A02–A03 | Commit the address and uncertain key before mutation. On ambiguous failure retain both keys; release only after confirmed removal. Reconcile abandoned pending registrations after two minutes. Renewals preserve the address/one device-node record but rotate the disconnect handle. |
| A04, A33 | Conditional server expiry and maximum verified VIP expiry preserve concurrent grants. The app ignores older profile responses after a newer grant and synchronizes entitlement display changes. |
| A05, A13 | Serialize client registration/refresh/recovery, reuse a concurrently renewed token, and preserve cancellation/transient failures. Older APKs must update to gain this protection; server replay detection remains enforced. |
| A06 | Validated opt-in proxy CIDRs and separate readiness route. Actual proxy topology must still be configured by the operator. |
| A07 | Explicit profile-service errors, fail-closed authentication errors and malformed agent-body rejection. |
| A08, A11 | Serialize activation/reconnect/cleanup, join cancellation before new work, retain tracked peer/notification state if DOWN cannot be confirmed, and show an operation error. Permanent process-death restoration and durable leak protection remain open. |
| A10, A12, A14 | Validate dual-stack hosts through their pinned public IPv4, baseline counters, and repeat eligibility checks after Android permission returns. |
| A15 | Split-exclusion edits now clearly require reconnect. Applying them automatically while connected is deferred to device-tested lifecycle work. |
| A16–A18, A29 | Protect probes against changed credentials/subnet/capacity/DNS, return successful saved node writes with an explicit probe error, accept registered legacy IDs in admin lookup, and neutralize CSV formula prefixes. |
| A19 | Speed helper deadline watchdog and late loss-callback protection added. Validate blocked writes/background/network loss on a real device. |
| A20–A21 | Refill native ad pool, use load events, use media layouts for video, recreate changed templates, and hide failed bindings. SDK/device verification remains required. |
| A34 | Installer now upgrades runtimes older than Node 22. The live Node 20 agent still needs a manual maintenance upgrade. |
| A23–A28, A32 | Correct unsupported README claims, reduce mapping to O(n), coalesce requests, scope config polling to foreground, reduce expiry wakeups, clamp pagination, preserve modal focus, repair error tile action and remove unused boot permission. Obsolete admin screens remain for a separate reachability cleanup. |

## Checks actually run

| Component | Baseline | After repairs |
|---|---|---|
| Backend | 106 tests passed | 130 tests in 20 suites passed, including `--detectOpenHandles`; TypeScript lint and production build passed |
| Agent | 8 tests passed | 9 tests passed; TypeScript lint/build passed |
| Admin | 4 tests; lint/build passed | 4 tests; lint/build passed. Browser smoke tests were not run: Chromium executable is unavailable. |
| Deployment | — | 17 unit tests and one firewall test passed; shell syntax checks passed |
| Android config | — | 10 config tests and both backup-policy checks passed |
| Android/helper build + Kotlin/Robolectric tests | Not run here | Blocked: Gradle 9.8.1 download reports network unreachable; only JDK 17 is present, while CI uses JDK 21. Newly added Android tests are unverified here. |
| PostgreSQL migration/concurrency | Not run here | No PostgreSQL/Docker service is available locally. Extended real-DB integration tests are required in CI. |
| npm advisory scan | Backend/admin/agent: 0 | No dependency changes; zero advisories in each lockfile at audit time |

Runtime-owned npm `http-proxy` warnings and previously observed upstream Gradle/AGP/transitive-package deprecations are not hidden. A clean TypeScript build is not proof of a zero-warning Android build.

### Performance evidence

The previous mapper searched the old list once per node; the new mapper builds one ID index and maps once: O(n²) to O(n). Catalog requests within five seconds coalesce, manual refresh bypasses that window, and latency remains off the UI thread with bounded concurrency. Ad load polling every ten seconds is replaced by events; reward expiry wakes at most once per minute rather than once per second. Device startup, perceived load below 500 ms, battery and tunnel throughput have NOT been measured. Admin JS bundle increased from 52.71 kB to 53.04 kB (before gzip) because of added error handling; no bundle-size improvement is claimed.

## Rollout, migration and environment

1. Publish the branch and open a draft PR. Keep main and production unchanged until Android and real-PostgreSQL CI pass. The package publisher uses an isolated sibling checkout and does not modify or commit edits in the supplied project.
2. In Android Studio open the new `Nsec-VPN-deep-audit-repair` directory, select the embedded JDK 21 for Gradle, and Sync. Build both modules; the helper's signature must match the app. Installing only the main APK does not update the helper.
3. Configure live rewarded units in AdMob with SSV URL `https://api.nsecvpn.com/api/v1/billing/rewarded/ssv`. Ensure Cloudflare permits machine callbacks on this exact route without a browser challenge. Do not disable signature validation. Debug Google test-unit IDs do not prove production SSV.
4. Configure `TRUSTED_PROXY_CIDRS` with the actual trusted reverse-proxy ranges. For the known Docker gateway, `172.18.0.1/32` may be one hop, but verify CloudPanel/Cloudflare forwarding first. Empty means direct/untrusted headers. Never set universal trust. Add the value privately to the existing backend service environment in `/root/nsec-agent-channel/compose.repair.json`; do not paste that file or container environment into chat. Recreating the backend with its normal approved deployment applies the value.
5. Before the backend update, back up and apply the additive nullable-column migration. For the existing manually migrated deployment use the SQL below; do not initialize or drop the live database. The migration leaves all existing leases/data intact. A rollback must keep this column while reservations exist; rolling back to the old allocator reintroduces its safety bug.

On **CloudPanel**, after the branch exists:

```bash
(
set -eu
umask 077
mkdir -p /root/nsec-backups
backup="/root/nsec-backups/before-peer-reservations-$(date -u +%Y%m%d-%H%M%S).dump"
docker exec nsecvpn-postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
test -s "$backup"
docker exec -i nsecvpn-postgres pg_restore --list < "$backup" > /dev/null
git -C /opt/nsec-vpn fetch origin fix/deep-audit-security-core
git -C /opt/nsec-vpn worktree add --detach /opt/nsec-vpn-deep-audit-validation FETCH_HEAD
docker exec -i nsecvpn-postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /opt/nsec-vpn-deep-audit-validation/scripts/migrate-peer-reservations.sql
printf 'Backup archive checked and additive migration applied. No containers restarted.\n'
)
```

For a fresh **dedicated CI/test database**, use `npx prisma migrate deploy && npm run test:integration`. Never use `prisma db push` against production to bypass review.

After the PR is merged, CI passes on the exact main SHA and the backend image exists, approve the reviewed schema on CloudPanel:

```bash
(
set -eu
git -C /opt/nsec-vpn fetch origin main
release_sha=$(git -C /opt/nsec-vpn rev-parse origin/main)
git -C /opt/nsec-vpn worktree add --detach /opt/nsec-vpn-deep-audit-release "$release_sha"
python3 /opt/nsec-vpn-deep-audit-release/scripts/approve-peer-reservations-schema.py "$release_sha"
)
```

This writes one exact approval only; it does not deploy. Run the existing CI-gated website deployment afterwards. If a worktree path already exists, inspect/reuse it; do not delete an unknown directory. Approval must come from the source of this repair release; if later schema changes are merged, review those before approval. No new runtime dependencies were added.

The supplied VPN node runs Node 20.20.2; Node 20 is EOL according to the [official schedule](https://github.com/nodejs/Release). Upgrade its agent runtime during maintenance (on the **VPN node**, not CloudPanel); this does not rotate WireGuard keys or change the tunnel config:

```bash
(
set -eu
curl -fsSL https://deb.nodesource.com/setup_22.x -o /root/nsec-node22-setup.sh
bash /root/nsec-node22-setup.sh
apt-get install -y nodejs
node --version
systemctl restart nsec-agent
systemctl is-active nsec-agent
)
```

Expect a supported v22 runtime and an active agent, then recheck authenticated health through the existing SSH channel. Review Ubuntu 20.04 extended-security coverage separately; do not perform an unattended distribution upgrade during VPN repair.

## API compatibility

- `POST /api/v1/billing/rewarded/prepare`: cooldown can return 429 `REWARD_COOLDOWN`.
- `POST /api/v1/billing/rewarded/claim`: 202 `{success:false,error:"REWARD_VERIFICATION_PENDING"}` until signed verification; existing 200 tier/expiry fields remain. Older APKs retain a pending ticket and retry more slowly. No client-supplied ticket can create a grant.
- `GET /api/v1/billing/rewarded/ssv`: only verified events grant; legacy signed events without custom_data remain supported. New tickets must match ownership/event-time bounds. Duplicate transaction callbacks remain idempotent.
- `POST /api/v1/vpn/connect`: same config fields. New errors: 409 `PEER_RESERVATION_CHANGED`, 503 `PEER_CLEANUP_PENDING`. Successful renewal changes `peerId` while retaining one device/node lease and its address; clients must use the latest handle. Unknown errors on old APKs show generic negotiation failure.
- `POST /api/v1/vpn/disconnect`: same request/response fields. Stale/foreign handles do nothing; uncertain removals return 502 and keep reservations.
- `GET /api/v1/auth/me`: 503 `PROFILE_UNAVAILABLE`; 404 `DEVICE_NOT_FOUND` instead of successful null.
- Device/admin auth: outages return 503 `AUTH_UNAVAILABLE`/`ADMIN_AUTH_UNAVAILABLE`, not misleading 401s. Recovered/expired credentials still fail closed.
- Admin node create/update: same node fields plus optional top-level `healthCheckError`; successful writes remain 201/200 despite probe failure. Update outages use 503 `NODE_UPDATE_FAILED`; true missing records use 404.
- Admin device-details/request filters use the registration ID format, retaining UUID support and adding legacy safe IDs. No promo duration or redemption schema changes.

## Security checklist and remaining work

Covered in code/tests: signed rewarded grants, owned/idempotent claims, plan/device checks, address reservation safety, latest disconnect handles, stale-result guards, conditional entitlement expiry, refreshed-role admin authorization, input validation, command argument safety, HTTP-secret redaction/no access logging, Keystore-protected local keys, release cleartext blocking, and backup exclusion.

Still required before publishing:

- A08: choose and device-test durable always-on/lockdown and process-death restoration. The current main-app ad/API exclusion is intentional; OS lockdown may affect excluded traffic. Do not claim an app-level kill switch survives process death.
- A09: implement UMP consent gating/privacy options and configure real AdMob privacy messages/target audience. This changes ad eligibility and requires owner configuration and device verification.
- A22: choose retention/deletion periods and implement safe cleanup. Decide Play RTDN/refund reconciliation; current entitlements can persist until expiry after a refund.
- A30: decide forced-version rollout for older devices before adding server-side version enforcement/migration. Current backend maintenance enforcement remains; forced update is a client guard.
- A31: keep the existing encrypted SSH agent channel. Public HTTP management remains a source-restricted legacy install option, not an encrypted channel. New nodes need private/SSH or HTTPS/mTLS before production.
- Verify CloudPanel/NGINX/Cloudflare/provider logging, backups/retention and published privacy identity/contact. A source audit cannot certify provider no-logs settings.
- Keep the compromised old node key retired and remove its protected forensic backup when no longer needed. Rotate any credentials independently suspected leaked; the pattern-based Git scan did not prove arbitrary history secrets absent.
- Run Android lint/tests/build, PostgreSQL migration/integration, browser interaction checks and real phone/node failure/leak checks. No staging deployment was performed here.

## Ranked improvements / decisions

| Rank | Improvement | Effort | Value | Risk | State |
|---|---|---|---|---|---|
| 1 | Coalesced cache refresh, linear mapping, event-driven ads, truthful errors | Small | High | Low | Implemented |
| 2 | Durable peer reservations and stale-disconnect protection | Medium | High | Medium migration | Implemented; integration/rollout pending |
| 3 | Always-on restoration and durable kill-switch UX | Large | High | High: routing/ad exclusion/background services | Owner decision and phone test required |
| 4 | Consent/privacy options and deletion/retention controls | Medium | High | Medium: operator policy/ad eligibility | Required next phase |
| 5 | Play RTDN/refund reconciliation | Medium | High | Medium: external credentials/entitlement provenance | Owner setup/decision required |
| 6 | Health alerts, maintenance scheduling and admin MFA enrollment | Medium | High | Medium: external alert channel/auth setup | Proposed; no messages or new collection added |
| 7 | Built-in DNS/IPv6/WebRTC leak verification through the helper | Large | High | Medium: measurement privacy/provider selection | Proposed; included-browser checks remain necessary |
| 8 | Untrusted-Wi-Fi auto-connect and additional protocols | Large | Medium | High: location permissions/server/protocol changes | Proposed; ask before building |

Favorites, smart location pools, split exclusions, DNS selection, session statistics, announcements, maintenance and client forced-update controls already exist; they are not new features delivered by this patch.

## Testing checklist

- Authenticate/register, launch concurrent profile/catalog requests with an expired access token, recover with PIN, and confirm stable backend identity and entitlement.
- Claim a prepared reward before watching: expect 202 and no VIP write. Complete a real signed rewarded event: local ad-free starts immediately, VIP follows SSV, replay does not extend it. Disable global/placement ads and test blocked/expired/foreign tickets.
- Connect/disconnect rapidly; change DNS while connecting; repeat disconnect then reconnect. Confirm one current lease and current handle, no IP reuse after uncertain agent timeouts, and pending cleanup only after acknowledged key removal.
- Test promo invalid/expired/not-started/exhausted/already-used errors, concurrent final redemption, longer paid/admin VIP preservation, and an old profile response arriving after redemption.
- Add/edit/disable/reorder/delete nodes; stop the agent during a saved edit. Expect a saved unhealthy node with an explicit probe error, not a false create failure. Search a registered legacy ID and verify request filters.
- Delete the last promo on page 2 and confirm page 1 loads. Check modal keyboard focus while saving. Run current browser smoke tests after installing a test browser.
- Change DNS, restart app, reconnect, and confirm active resolvers. Verify included-browser DNS/IPv6/WebRTC results; Private DNS/DoH may select its own resolver through the tunnel.
- Show text and video native ads on Home/Servers/Connected/Speed Test; rotate, use 200% font size, repeat refreshes, enable VIP/global kill switch, and confirm ads disappear correctly. Ads remain outside the VPN.
- Run the in-app speed helper, verify exit IP, cancel, lose Wi-Fi, block upload temporarily and wait for the deadline. No external browser should open; no stale completion should be saved.
- On the real node use `sudo wg show wg0 latest-handshakes` and `sudo wg show wg0 transfer` while browsing in included Chrome. Expect a recent handshake, rising counters and the node's public exit IP. Do not paste full dumps, client endpoints, private configs or tokens.
- Test Wi-Fi/mobile switching, backgrounding, process kill, unreachable node and UDP blocking. Inspect actual exit IP during a VPN drop; current OS lockdown is the durable protection and its excluded-app behavior needs verification.

**Publishing decision: NO-GO** until the outstanding security/product setup and unrun checks above are resolved. Passing unit tests is not proof of end-to-end VPN traffic or absence of all bugs.
