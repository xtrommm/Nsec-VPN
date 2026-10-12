# Admin, promotions and rewarded VIP update

Branch: `fix/admin-rewards-control-panel`. nPerf and speed testing are deferred; this update does not replace the speed-test screen or change tunnel routing, signing, ad-unit constants, or node keys.

## Behavior

A completed SDK rewarded-ad callback activates local ad-free time immediately and submits an authenticated, one-use reward ticket to the backend. The backend grants `VIP` for the configured duration, default 30 minutes, enabling premium nodes. Dismissed/failed ads do not call the reward claim. Preparation runs before displaying the ad. Failed nonterminal claims are retained privately and retried on restart and with capped retry frequency. Completed claims are idempotent; tickets are device-bound and expire after ten minutes. Repeat grants have a ten-minute cooldown and device request limits. Longer paid/lifetime VIP is never shortened. Promo redemption grants time-limited `VIP` as well.

**Security tradeoff:** immediate VIP trusts the client SDK completion callback. Tickets, ownership checks, limits and Google SSV reconciliation do not cryptographically prove that an unmodified client watched an ad. A modified client can fabricate completion. This is the user's requested instant-grant policy, replacing the earlier ad-free-only choice. Google signed SSV still validates callback signatures, timestamps, configured ad units and ticket/device binding; duplicate Google transactions are idempotent. For stronger fraud prevention, VIP must wait for signed SSV or use a reviewed integrity-backed claim flow.

## New console

Responsive dark/light console with real overview metrics, node create/edit/delete, enabled/premium/capacity/DNS/MTU settings, actual health-test details and public-key sync; device search/pagination/block/VIP controls; campaign creation/expiry/redemption limits/disable; AdMob units and independent native/banner/interstitial/rewarded controls; reward duration; announcements, maintenance/version policies, audit trail and aggregate CSV export. JWTs are session-scoped and sign-in supports existing TOTP. Server-side roles are rechecked from the database. Code hashes and redemption counters are persisted; raw promo codes are only returned on creation and are excluded from audit records.

The console grants manual VIP for one year, matching the existing backend policy, and labels that duration explicitly. Super Admin is required to mutate nodes, settings, promotions and VIP tiers; ordinary Admin can read and block devices. No browsing history, traffic content or source-IP records were added. Active peer leases are labeled as leases rather than live handshakes.

## API compatibility

New authenticated device endpoints:
- `POST /api/v1/billing/rewarded/prepare` -> `data.ticketId`.
- `POST /api/v1/billing/rewarded/claim`, body `{ticketId}` -> `data.tier`, `data.passExpiresAt`.
- `POST /api/v1/promos/redeem`, body `{code}` -> `data.tier`, `data.passExpiresAt`.

New admin endpoints: `GET /api/v1/admin/promos`, `POST /api/v1/admin/promos` (label, optional code, passMinutes, maxRedemptions, nullable ISO expiresAt, active), `PUT /api/v1/admin/promos/:id` (active only). Creation returns the one-time raw `code` plus campaign metadata; list responses exclude hashes and codes.

`GET/PUT /api/v1/admin/config` add nativeAdsEnabled, bannerAdUnitId, nativeAdUnitId, interstitialAdUnitId, rewardedAdUnitId, rewardedPassMinutes. `GET /api/v1/config` adds corresponding `ads` fields and `app.maintenanceMode`. Optional new fields preserve older app parsing. Old SSV callbacks without tickets remain supported and grant bounded VIP; old clients still wait for SSV. Config and tier writes now require Super Admin. Tier writes accept FREE/VIP; legacy unbounded REWARDED_PASS manual grants are rejected. Config writes reject unknown fields, invalid versions and announcements without content.

Errors: REWARD_RATE_LIMIT, REWARD_PREPARATION_UNAVAILABLE, REWARD_TICKET_INVALID, REWARD_TICKET_EXPIRED, REWARD_COOLDOWN, REWARDED_ADS_DISABLED, REWARD_CLAIM_UNAVAILABLE, DEVICE_BLOCKED; PROMO_RATE_LIMIT, PROMO_INVALID, PROMO_EXPIRED, PROMO_ALREADY_REDEEMED, PROMO_EXHAUSTED, PROMO_REDEMPTION_UNAVAILABLE, PROMOS_UNAVAILABLE, PROMO_EXPIRY_INVALID, PROMO_CODE_EXISTS, PROMO_CREATE_FAILED, PROMO_NOT_FOUND; VERSION_RANGE_INVALID, ANNOUNCEMENT_CONTENT_REQUIRED. Existing auth/validation errors remain.

## AdMob setup

Enter your production ad-unit IDs in the console (four supported formats, `ca-app-pub-…/…`). Existing compiled IDs are unchanged. New release builds disable placements without a valid published production unit; debug builds keep safe Google test units. Changing units discards loaded ads and stale native retries. Clearing a field disables that release placement. An AdMob application ID (`…~…`) is a separate manifest/build value and cannot be replaced remotely by this control. Configure the real application ID in the APK before release.

AdMob rewarded-unit SSV URL: `https://api.nsecvpn.com/api/v1/billing/rewarded/ssv`. User ID is the random installation identity; custom data is the one-use ticket. The backend uses the published rewarded unit, with the existing environment value as a backward-compatible fallback. No credentials should be pasted into chat. Google test-ad callbacks demonstrate local/client rewards, not signed live-ad verification.

## Required rollout order — preserve the running VPN

1. Apply this overlay, commit only its reviewed paths, push the repair branch and open a PR to main. Keep the current site and node running. Android compilation/lint/unit tests must pass in CI or Android Studio before merge.
2. On CloudPanel fetch the branch into a NEW worktree; preserve `/opt/nsec-vpn`, its environment files, private Compose JSON and SSH channel. Take and validate a protected PostgreSQL dump before migration.
3. Apply `scripts/migrate-control-panel.sql` using `psql -v ON_ERROR_STOP=1` against the existing PostgreSQL container. It is an additive transaction with bounded lock/statement timeouts, adds AppConfig fields and promotion/reward-ticket tables, and does not alter devices, peers, node endpoints, keys or credentials. Existing data is retained. Do not run db push/reset or replay the old baseline migrations against the populated database.
4. Install the included updated deployment controller using `deployment/install-website-deployer.py --deploy-user nsecdeploy --legacy-command /usr/local/bin/nsecvpn-website-entry`. Confirm effective SSH ForceCommand remains `/usr/local/bin/nsecvpn-website-entry`. This keeps the known protected configuration and records the old running schema.
5. Merge after CI passes. Main CI publishes trusted SHA-tagged images. A new-schema deployment requires explicit approval: run `python3 scripts/approve-control-panel-schema.py FULL_MAIN_RELEASE_SHA` from the new server worktree after the images exist. This checks image provenance and the migrated database, and writes a root-only SHA/schema approval. It never migrates data or restarts services. Re-run the deployment's failed job if it reached the schema gate before approval.
6. The deployer checks the exact approval, backs up data, updates only backend/admin images and verifies readiness. It advances the schema baseline only after success, consumes the approval, and restores previous website images/config on rollout failure. Additive database changes remain backward compatible with the prior images. Do not drop tables or restore the database casually; retain the dump for reviewed recovery.
7. Sign in again, create a real promo, redeem it on the phone and verify VIP, no ads, expiry and premium-node connection. Complete a rewarded ad and verify VIP/expiry, cancelled ads grant nothing, duplicate claims cannot extend, and another device cannot claim the ticket. Set actual AdMob units and verify a signed live callback. Test the new expiry clock; local reward state fails closed on reboot/clock rollback, while the backend VIP entitlement survives and is refreshed from the API.

## Validation and publishing

Backend build/lint and 63 unit tests passed locally. Admin type check, production build and its two existing validation tests passed. Deployment helper checks passed. New Android reward-clock and API-fixture changes are included, but Android compile/lint/unit tests and live-phone reward/promo verification are pending; no Android SDK is present in this execution environment. Database integration and the new schema approval flow require CI/server checks. This is not a Play Store publishing approval. No site rollout, merge, server migration or secret/key change has been performed by this overlay.
