# Audit follow-up — 13 October 2026

Branch: `fix/audit-followup-disconnect`. Base: `ab205632074378714c1e5ad7c0e0d4c07a6ed81d` (merged PR #16).
This report supplements DEEP-AUDIT-2026-10-13.md and supersedes its deferred-code list. Code implementation does not establish real-phone, Google Console or live deployment success.

## Findings closed in source

Original file/line evidence is retained in DEEP-AUDIT-2026-10-13.md; references there describe the audited revision rather than new line numbers.

| Issue | Severity | Cause and evidence | Implemented correction |
|---|---|---|---|
| A35 disconnect delay | Medium | Base WireGuardTunnelManager.kt:225–247 awaited notifyDisconnect at line 234 before publishing Disconnected at line 247; VpnRepository.kt:240–252 could refresh/retry a remote request. | Confirm local DOWN first; enqueue independently bounded 5-second remote cleanup in TunnelOperations.kt. Repeated disconnect taps disabled. No network wait holds navigation completion or the operation mutex. |
| A08 interrupted tunnel | High | Original manager had no durable process restoration; notification service was not persistent. | Encrypted real node/location intent, GoBackend always-on callback, application/service restart restoration, fresh backend peer/config and handshake gate. Manual disconnect clears intent. |
| A09 ad consent | High | AdMobManager.kt:60–68 initialized/requested without UMP eligibility. | UMP 4.0.0 authoritative consent refresh, privacy options, SDK initialization only when allowed, cached ad destruction when permission changes. |
| A15 active split changes | Medium | VpnRepository.kt:393–411 persisted exclusions without changing active routing. | Serialized cancel/join/reconnect applies new exclusions. Main app remains excluded. |
| A22 retention | Medium | Schema authentication/reward history lacked bounded deletion. | Hourly bounded 90-day cleanup, transactional eligibility recheck and exceptions protecting VIP, purchases, recovery and abuse prevention. |
| A22 refunded subscription | Medium | No push reconciliation; subscriptions could remain until expiry. | Encrypted owned receipts, periodic Google re-verification, authenticated RTDN, independent promo/reward grants, premium lease shortening/revocation. Restore Purchases verifies owned subscriptions without purchasing again. |
| Paid/promo overlap exploit | High | Adding promo time after paid expiry could preserve paid time after refund. | Independent non-purchase grant extended separately; paid and promo expiries overlap by maximum. Transactional regression coverage. Legacy unattributed time is conservatively preserved. |
| A28 obsolete UI | Low | Legacy admin screens and sidebar were unreachable. | Verified actual import graph and removed eight obsolete components/screens. Current console retained. |
| A30 forced update | Medium | Backend accepted numeric version without enforcing peer creation. | Persist appVersionCode; send X-App-Version-Code; enforce APP_UPDATE_REQUIRED during initial and transactional provisioning checks. |
| A31 management transport | High | Public agent HTTP exposed unencrypted management traffic despite HMAC. | Reject public HTTP agent origins; private/loopback HTTP requires encrypted/private transport. Fresh installs bind loopback. Existing private Docker-to-SSH channel remains compatible. |

No logo, ad unit IDs, signing settings or third-party notices were changed. No dummy production data was added.

## Compatibility, API and settings

- New `X-App-Version-Code` request header; new nullable Device.appVersionCode. HTTP 426 `APP_UPDATE_REQUIRED` only when the existing forceUpdate switch is enabled. Keep it OFF until compatible builds are published. Version is self-reported, not device attestation.
- `POST /api/v1/billing/rtdn`: Google authenticated Pub/Sub push; 204 accepted, 400 invalid event, 403 unauthorized, 503 unconfigured/retryable verification. Notifications cannot grant VIP directly: current Google state is queried.
- Billing verification adds receipt protection failures: 503 `BILLING_TOKEN_ENCRYPTION_REQUIRED`, 409 `PURCHASE_ALREADY_BOUND`, 400 `PURCHASE_PRODUCT_MISMATCH`, and 503 `BILLING_VERIFICATION_UNAVAILABLE` for other verification failures. Existing device-owned receipt binding is preserved.
- Promo duration remains configured in days. Promo/reward VIP and paid VIP now overlap independently rather than appending promo time to a paid subscription end. This is the policy selected by the owner.
- `RETENTION_DAYS=90` default; expired records become eligible after 90 days. Replay/ownership hashes, recovery-protected and blocked devices are deliberately retained. This is not a promise to erase all account data at 90 days.
- New `PLAY_TOKEN_ENCRYPTION_KEY`: 64 hexadecimal characters, retain permanently while receipt ciphertext exists. Never paste this key, receipts, service-account JSON or environment contents into chat.
- Optional RTDN configuration: `PLAY_RTDN_AUDIENCE=https://api.nsecvpn.com/api/v1/billing/rtdn` and `PLAY_RTDN_SERVICE_ACCOUNT_EMAIL` set to your actual push service account email. Existing `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` must authorize subscription verification.
- New Android dependency: Google User Messaging Platform 4.0.0. Configure real messages in AdMob Privacy & Messaging. The VPN itself remains usable if consent refresh fails.

## Publish from Windows

Extract the ZIP. Run apply-audit-followup.py with your existing Git project root and `--publish`. It creates a separate checkout; it never stages or changes the original working files. GitHub CLI creates a draft PR when authenticated; otherwise click the printed comparison URL and Create Pull Request. Direct GitHub writes were unavailable in this session, so this package is not yet pushed.

The bundled Nsec-VPN-Android-Studio folder is the complete source at the package commit, including main app and speedtest-helper. For subsequent Git updates, use the isolated checkout printed by the updater. Open that folder in Android Studio, sync Gradle, and build both modules. A successful helper test requires the helper installed on the phone with compatible signing.

## CloudPanel migration preparation BEFORE backend rollout

Run in the CloudPanel root shell after the branch is pushed. This backs up the database and applies only the additive reviewed schema. If the destination already exists, inspect it; do not overwrite it.

```bash
(
set -eu
umask 077
mkdir -p /root/nsec-backups
backup="/root/nsec-backups/before-audit-followup-$(date -u +%Y%m%d-%H%M%S).dump"
docker exec nsecvpn-postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup"
test -s "$backup"
docker exec -i nsecvpn-postgres pg_restore --list < "$backup" > /dev/null
git -C /opt/nsec-vpn fetch origin fix/audit-followup-disconnect
git -C /opt/nsec-vpn worktree add --detach /opt/nsec-vpn-audit-followup FETCH_HEAD
docker exec -i nsecvpn-postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /opt/nsec-vpn-audit-followup/scripts/migrate-audit-followup.sql
python3 /opt/nsec-vpn-audit-followup/scripts/configure-billing-encryption.py /root/nsec-agent-channel/compose.repair.json
)
```

Migration is idempotent/additive: Device.appVersionCode, eight PurchaseClaim receipt/provenance columns and cleanup indexes. One-time backfill preserves existing VIP; no expiry is erased. Prisma copy is backend/prisma/migrations/20261013070000_audit_followup/migration.sql. Do not also run destructive db push/reset. Private configuration helper creates a private backup, preserves an existing key and does not restart containers.

After CI passes and PR merges, WAIT until Build Images publishes the exact merged main image. Then:

```bash
(
set -eu
git -C /opt/nsec-vpn fetch origin main
release_sha=$(git -C /opt/nsec-vpn rev-parse origin/main)
git -C /opt/nsec-vpn worktree add --detach /opt/nsec-vpn-audit-followup-release "$release_sha"
python3 /opt/nsec-vpn-audit-followup-release/scripts/approve-audit-followup-schema.py "$release_sha"
)
```

Approval intentionally fails if the image is missing, provenance differs, DB migration is absent or source schema differs. Only after APPROVED, rerun the existing deployment workflow for that exact release. Preserve the authoritative Compose JSON and SSH channel. Retain additive columns on rollback; restoring old backend code loses the new protections. Keep the verified backup.

## External configuration still required

1. AdMob: publish applicable consent messages under Privacy & Messaging. Device-test consent acceptance, denial, privacy change and offline retry. This does not reinstate the removed custom VPN Accept policy dialog.
2. Google Play: configure subscription API credentials. Create a Pub/Sub topic for RTDN, grant Google Play its documented publisher access, enable the topic in Play Console monetization settings, and add an authenticated push subscription to the exact RTDN URL. Configure push service-account email and matching OIDC audience in the private backend environment. Test in Play's license-testing environment before production.
3. Review older unattributed VIP records. Historical token hashes cannot reconstruct purchase tokens or provenance; migration conservatively preserves existing grants. Restore Purchases can reverify receipts available to Play, but cannot magically identify historical grants. Do not erase valid legacy VIP to guess attribution.
4. Optional OS always-on/Block Connections Without VPN is user-selected. Android may block the intentionally excluded ad/API traffic under lockdown. This is not an unconditional app-level kill switch; force-stop/OEM restrictions and connectivity switches need phone tests.
5. New public HTTP agent nodes must use SSH/private protected networking or HTTPS before backend rollout. Existing http://172.18.0.1:51823 encrypted channel is retained.

References: Google AdMob Android privacy documentation; Google Play RTDN reference; Google Cloud authenticated Pub/Sub push documentation. Follow current provider configuration instructions rather than inventing Console values.

## Verification completed and limits

| Component | Results |
|---|---|
| Backend | Lint/build passed; 149 tests in 25 suites passed. New live PostgreSQL integration case added but not executed locally. |
| Admin | Lint/build passed; five tests passed. |
| Agent | Lint/build passed; nine tests passed. |
| Deployment | 23 tests and one firewall test passed; migration copies matched; shell/Python syntax checks passed. |
| Android source checks | Ten configuration tests and both backup-policy checks passed. |
| Android build/lint/device | NOT verified locally: Gradle download blocked by network; local JDK17 differs from CI JDK21. Run CI and phone checks. |

No before/after phone latency numbers were measured. The blocking HTTP wait is removed from disconnect completion; remaining UI performance must be measured on your device. No live Google or node rollout was performed in this session.

## Short testing checklist

1. Build/sync main app and helper; install both. Connect and verify real handshake/counters and browser exit IP.
2. Disconnect from Connected screen with backend temporarily unreachable: local VPN must stop without awaiting HTTP cleanup; repeated taps must not launch parallel operations. Reconnect and repeat with working backend.
3. While connected change DNS and split exclusions; confirm fresh handshake and selected routing. Check DNS and IPv6 leakage externally. Kill process normally (not force-stop), reopen and test restoration; also Wi-Fi/mobile transition and Android always-on.
4. UMP: no ad requests before authorization/SDK readiness; change privacy choice and verify cached ads removed. VPN works when consent network fails. Reward still obtains backend-verified VIP.
5. Redeem promo while paid VIP exists; in Play test environment revoke/refund paid entitlement, verify only independent promo time survives. Premium peer must expire when all VIP ends. Duplicate RTDN must not duplicate VIP; invalid OIDC denied.
6. Clear app data, recover existing device with its recovery flow; Restore Purchases must reverify owned receipts, never open a purchase flow or bind another device's receipt.
7. Admin: current nodes/config/promos/requests/device actions still work; public HTTP agent rejected. Enable forced update only for a deliberate test: old/missing client version blocked, new version allowed.
8. Retention: test aged records on a disposable DB; preserve active VIP/purchases/PIN/replay records, remove eligible expired records. Do not age production records for testing.

## Release decision

Source fixes complete for the deferred issues above. Production publication remains NO-GO until full Android/PostgreSQL CI passes, additive migration and exact image approval are complete, required provider configuration is done, and the short real-phone checks pass. A bug-free guarantee would be unsupported.
