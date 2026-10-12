# App polish and location pools

Base inspected: `6dcfe74` on main. No schema migration, new dependency, secret or signing change.

## Audit evidence

- `app/src/main/java/com/nsec/vpn/data/repository/VpnRepository.kt:116` — Catalog retains raw nodes, which is needed for latency and failover, but the screen rendered every raw node.
- `app/src/main/java/com/nsec/vpn/ui/screens/ServerListScreen.kt:65` — Server list filtered raw nodes without location aggregation.
- `app/src/main/java/com/nsec/vpn/data/repository/VpnRepository.kt:235` — Unused grouping helper also summed per-node totalNodes metadata, inflating a 10-node location to 100.
- `app/src/main/java/com/nsec/vpn/vpn/FailoverPolicy.kt:7` — Three-node failure limit also limited capacity fallback; a tenth available node could be missed.
- `app/src/main/java/com/nsec/vpn/vpn/WireGuardTunnelManager.kt:81` — Selected stale representative was tried first, with no same-location latency/load reranking.
- `backend/src/routes/vpn.ts:13` — Only the requested node was provisioned; capacity at that node did not trigger backend location selection.
- `app/src/main/java/com/nsec/vpn/ui/screens/SpeedTestScreen.kt:68` — Screen performed local history reads and rendered recent results before its ad.
- `app/src/main/java/com/nsec/vpn/ui/screens/SpeedTestScreen.kt:155` — Large gauge and repeated 36dp gaps pushed ads below the fold.
- `app/src/main/java/com/nsec/vpn/data/repository/VpnRepository.kt:86` — Verified JSON parsing happened in the repository constructor on the UI startup path.
- `app/src/main/java/com/nsec/vpn/ui/components/RewardOffer.kt:11` — Reward label used sentence case rather than the requested word capitalization.

## Behavior

- One location row per country/city (city comparison is case-insensitive). Add all ten Singapore nodes with the same country code and city, distinct node names/endpoint/key/agent records, correct maxPeers, enabled and healthy. No CloudPanel is required on a node. Other cities remain separate locations.
- Raw nodes stay in the authenticated catalog and verified cache. Aggregation counts actual nodes once and favorites cover a whole location; actual connected node/IP remain the source of connection details. Free devices rank free nodes; VIP devices may use both free and VIP nodes at that location. A location containing only VIP nodes retains its locked badge for free devices.
- Availability is based on registered active peer reservations, not CPU, bandwidth, or concurrent browser usage. Existing per-node maxPeers/IP allocation and advisory transaction locks remain authoritative. SQL aggregation counts reservations without downloading every device lease.
- Score = measured latency in ms + 2 times capacity percentage. Unknown latency gets a 999ms score; no invented ping. Full nodes are skipped and concurrent full responses fall through all eligible candidates, not just the first three. The backend probes up to three failing agents; Android caps connection/handshake attempts at three. Failed real node IDs are excluded on the next request, even when the backend selected a different node from the clicked representative.
- The full-location error is shown only for capacity exhaustion. Agent/UDP/handshake failures are distinct; excluded unreachable nodes do not produce a false all-full message. An existing device reservation may be reused even at capacity.
- Speed gauge is 190dp rather than 240dp, with smaller gaps and weighted metric columns. Cancellation/timeout/VPN-route verification remain. Recent history UI and new result persistence are removed; existing historical preferences are left untouched and unused. No browser is opened.
- Source labels and human-facing errors use initial capitals while keeping acronyms, units, protocol identifiers, user/remote content, ad SDK copy, uppercase strings, header wording, timer values and mottos intact. The audit JSON lists exact text replacements. The requested reward example now reads `45 Minutes Of VIP` only when backend config supplies 45.
- Theme-aware startup surface uses the existing logo and a drawn 12-star EU flag with exactly `Made in Europe`. It is shown briefly (250ms) on cold Activity creation, after Android's system splash, not on tab navigation or restored rotation. Server fetching starts in parallel, without waiting for a network result. This requested brand line does not claim every node is geographically in Europe.
- Catalog JSON restoration runs on Dispatchers.IO; refresh awaits it before replacing it with live data. Location aggregation is remembered between unchanged catalogs/tiers. Native ad placement after the first location also permits an ad for catalogs smaller than eight locations, subject to existing ad eligibility and fill. No promise of a fixed load time or no-scroll behavior on every device/font size.

## API and rollout

`POST /api/v1/vpn/connect` adds optional `locationSelection` (boolean), `nodeLatencies` (up to 1000 `{nodeId,pingMs}` records; integer 0–60000ms), and `excludedNodeIds` (up to 1000 bounded IDs). Node IDs and measurements only influence ranking/exclusion inside the authenticated device's eligible country/city; they never grant access or capacity. Config response is unchanged and identifies the actual node.

New HTTP 503 code: `ALL_SERVERS_FULL`; Android displays `All Servers Is Full, Try Again Later`. Older requests omit `locationSelection` and retain the single-node endpoint behavior and existing errors. Install the updated backend before the new APK to get authoritative pool fallback; old backends can ignore extra fields and cannot provide the new guarantee.

1. Publish the branch and open the printed PR; wait for all CI jobs before merging.
2. Let the existing CI-gated backend image/deployment complete. No manual SQL migration is needed. Do not deploy the separate public marketing website for this change.
3. Open the printed isolated project folder in Android Studio, sync, and build/install both app and speedtest-helper via `scripts/install-android-debug.ps1` or Android Studio. Keep the same signing identities; do not uninstall the main app to bypass a signature error.
4. Keep the rewarded duration set to 45 in Remote Config & Ads if that is the offer you want.

## Verification

Passed locally: backend strict TypeScript checking and build; 98 backend tests in 15 suites; six Android backup-policy Python tests; git diff --check. New location-group/ranking and light/dark splash Compose tests were added. Android Gradle/unit/lint/APK checks could not start because this environment's Java cannot reach the Gradle distribution. No physical-phone performance, multiple-node traffic or ad-fill verification is claimed. CI and these device checks are still required:

- Confirm uppercase headers/mottos and timer/unit formatting are unchanged, reward says 45 Minutes when configured, and labels/dialog copy are capitalized. Inspect large fonts and both themes.
- Force-stop and reopen: EU flag/origin line is visible; relaunch/rotate/navigation do not repeat it unexpectedly.
- Use speed test through the signed helper: ping/jitter/down/up and cancel work, ad appears sooner when eligible, no history section is rendered and no new history is persisted.
- Add ten healthy SG/Singapore node records. App shows one location with 10 nodes. Connect: diagnostics and `wg show` identify the actual selected node, with a handshake and traffic.
- On an isolated staging pool, fill nine nodes up to maxPeers and connect a new test device; the tenth must be selected. Fill all ten, connect a new device, expect HTTP 503 ALL_SERVERS_FULL and no tunnel start. Do not use production customers to run exhaustion tests.
- Simulate a handshake failure: next attempt must exclude that actual node, not merely the original row ID. Stop an agent: show agent/offline error, not all-full. Free device must never acquire a premium-only peer; expired VIP must be treated as free.
- Check saved real catalog appears during offline startup, refresh updates it, location favorite persists, and load updates during connection. Ad-free/VIP users continue to suppress ads by the existing policy.
