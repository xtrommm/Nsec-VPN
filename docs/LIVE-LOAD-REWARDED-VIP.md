# Live node load and rewarded VIP — 2026-10-12

Baseline: main `10531f80cba7024c07b84f0366faaf41f2e112de`. Branch: `fix/live-load-reward-vip`.

## Root causes and implementation

- Original `MainActivity.kt:326` passed `tunnelManager.activeServer` to Home. That object is the connection-time snapshot, so refreshing the catalog did not update its load. Home/Connected/Servers now resolve display metadata by the actual connected `nodeId` in the current catalog; established keys/routes/public-IP proof are unchanged. Failover uses the actual node rather than the previously selected location.
- Original `VpnRepository.kt:138-140` retained the old selected object when its ID was still present. The selected row now receives that ID's updated data. Latency is carried forward only for the same endpoint; periodic polling does not restart every node's ping probe.
- Original `MainActivity.kt:201` refreshed servers when opening Servers, not periodically while connected. A STARTED-lifecycle loop now refreshes every 15 seconds while the app is visible, stops in the background, and also refreshes on connection/failover. Requests still use the existing de-duplicating mutex and authenticated API.
- Original `backend/src/index.ts:118` sampled node health once a minute. Health refresh is now every 30 seconds with existing bounded concurrency/de-duplication. Peer cleanup keeps its original 60-second interval.
- Original `nodeHealth.ts:24` rounded one peer on a 250-slot node down to 0%. The displayed capacity percentage now rounds occupied slots up, capped at 100. **This is registered WireGuard peer-slot occupancy, not CPU/bandwidth or an active-user count.** No client IP or browsing data is collected for it.
- The server API adds optional `loadUpdatedAt`, backed by the actual stored node health timestamp. Grouped catalogs use the oldest included sample. App-side timestamps older than 90 seconds, invalid, or absent show Unavailable rather than a pretend live number. Updates normally reflect the next backend sample plus app poll (up to roughly 45 seconds); no hard latency promise is made under outages/slow probes.

## New VIP experience

Home has a prominent compact reward card immediately below Connect. It displays the configured number of minutes, both **Ad-free** and **VIP servers**, and a direct Watch ad & unlock action. A new scrollable Material 3 bottom sheet places the reward first and keeps monthly/yearly Google Play plans below it. Both themes use semantic colors. Loading, disabled ads, pending activation and existing VIP each have an explicit state. Busy buttons cannot be requested twice; no-fill/show failures and incomplete ads give feedback.

The existing SDK reward callback and authenticated one-use backend claim flow are preserved. Ad-free protection is activated on SDK completion; VIP access is shown only after backend entitlement confirmation, with existing retry behavior on failure. This update does not add a cryptographic proof of viewing to the existing claim API or change its verification policy. Signing, ad IDs, billing product IDs and the app's ad-traffic exclusion are unchanged. Unsupported 10Gbps/unblocking/priority-routing guarantees and the unsupported 50%-off badge were removed.

### Required operator setting: 45 minutes

In the existing **VPN admin panel** (not the public website C-panel):

1. Open Remote Config & Ads.
2. Set **Rewarded pass minutes** to **45**.
3. Keep global ads and rewarded ads enabled and a valid real rewarded unit configured.
4. Save and reopen/wait for app config refresh.

The existing backend already grants `rewardedPassMinutes`; a regression verifies a 45-minute grant. No database migration is needed. The app deliberately displays 30 if the real backend is still configured for 30; it will not advertise 45 while granting 30. Do not change reward duration while testing a currently playing ad. Longer existing VIP entitlements are not shortened; rewards do not silently stack.

## API compatibility and deployment

GET `/api/v1/servers`: additional `loadUpdatedAt` (ISO timestamp or null), both grouped/non-grouped. All existing fields remain; no new request field/error code. Older clients can ignore this field. A new client against an older backend shows Unavailable for freshness until the backend update is deployed. No Prisma/DB schema, agent, or public website change.

Publish/open PR, wait for all CI, then merge and let the existing gated backend deployment finish. Set the reward duration to 45, then build/install the updated Android app. Existing speed-test companion stays compatible and can be installed alongside it from the same project/signature.

## Verification

Local backend TypeScript type-check and build passed. All **86 tests in 14 suites** passed, including real server-response timestamp serialization/group handling, peer-capacity rounding/validation and configured 45-minute reward grant. Existing six backup-policy regressions and 16 deployment regressions pass.

Added Android regressions cover current versus captured load, failover node identity, stale/missing timestamps, selected-row refresh, configured offer versus unverified/unavailable/pending/VIP states, displayed benefits, click handling and busy-button rejection. Full Android tests/lint/build were attempted but blocked before Gradle configuration by Java's unavailable network access. These Android/Compose tests have not run locally. GitHub CI and phone visual checks are required.

Phone checklist: connect and remain connected, change real node occupancy, observe Home without disconnecting; fail over and check correct node; stop backend/probes and verify stale data becomes Unavailable; background app and ensure polling stops. Set duration 45, finish one ad, verify ad-free state, backend VIP expiry and premium node access; close an ad early, no-fill, kill API during claim and retry; inspect both themes and small screens; check paid plans open Google Play without invented prices.
