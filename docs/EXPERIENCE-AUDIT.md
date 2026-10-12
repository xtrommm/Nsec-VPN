# Experience audit before implementation

Audited source: main `31d2a68aaa7d5bf58564ad9e498743801cbeaf24`. References below refer to that immutable revision, before edits. Findings are confirmed source defects or requested missing capabilities; live deployment configuration and device behavior have not been independently measured in this audit.

| Finding | Evidence | Effect / planned correction |
| --- | --- | --- |
| Connection footer explicitly hidden | app/src/main/java/com/nsec/vpn/MainActivity.kt:204–240 (`activeSubScreen == null`) | Connected is a subscreen and loses main navigation; retain shell, select Home for status, clear subscreen on tab selection. |
| Troubleshooting fabricates success | app/src/main/java/com/nsec/vpn/ui/screens/TroubleshootScreen.kt:35–51, 83–104 | No network calls; delayed unconditional true, false UDP/DoT/MTU claims. Replace with measured observations and explicit unverified results. |
| DNS changes affect saved choice only | app/src/main/java/com/nsec/vpn/data/repository/VpnRepository.kt:314–317; MainActivity.kt:332–333 | Does not reconfigure an existing tunnel. DNS overrides are captured at connect. |
| Automatic and Custom DNS missing; IPv6 resolver rejected | app/src/main/java/com/nsec/vpn/data/model/VpnModels.kt:61–66; vpn/TunnelConfigBuilder.kt:15–21 | Four fixed IPv4 providers only. IPv6-only resolvers cannot work on this IPv4 node; validate addresses, explain unsupported routes instead of leaking. |
| DNS UI incorrectly calls selection encrypted | app/src/main/java/com/nsec/vpn/ui/screens/SettingsScreen.kt:232 | Builder configures ordinary DNS inside encrypted WireGuard, not DNS-over-TLS. |
| Cold cache is always empty | app/src/main/java/com/nsec/vpn/data/repository/VpnRepository.kt:64–75 | No persisted real server catalog; users must wait for authentication/API after each process start. |
| Startup serializes requests and ping work | MainActivity.kt:142–147; data/repository/VpnRepository.kt:129–141 | Config, servers, entitlement, then bounded ICMP probes increase startup completion time. Parallel refresh and lazy probes; measure perceived target on device. |
| Server tab repeats full refresh | MainActivity.kt:165–168 | Redundant refreshes can race or await probes. Deduplicate refresh and preserve immediate cached catalog. |
| Home duplicate header | app/src/main/java/com/nsec/vpn/ui/screens/HomeScreen.kt:51,64–68 | Node/location/IP above logo requested for removal; keep actual connection details on status screen. |
| Settings lacks support, server requests and installation ID | app/src/main/java/com/nsec/vpn/ui/screens/SettingsScreen.kt:141–185 | Missing functionality; stable ID already generated in KeyStoreManager.kt:70–74, use that exact identifier. |
| Settings dead button and silent browser failure | SettingsScreen.kt:154–158,169–173 | Website errors swallowed; App Version has empty onClick and hardcoded version. |
| Speed test exits app | app/src/main/java/com/nsec/vpn/data/network/VpnSpeedTest.kt:20–36; ui/screens/SpeedTestScreen.kt:74–78 | Browser launcher plus aggregate tunnel counters, not a built-in throughput test. Own app UID is excluded from tunnel, so ordinary in-app HTTP would measure ISP route. Transport architecture requires explicit resolution. |
| Promos lack CRUD, scheduling and usage detail | backend/src/routes/promos.ts:16–58; services/promotions.ts:6–12; admin-panel/src/components/ControlPromos.tsx:10–30 | Only create/list/active toggle; no delete, edit duration, start date, bulk, search or redemption list. |
| Promo use can be consumed without benefit | backend/src/services/promotions.ts:27–32 | Redemption/count committed before early return for already-active VIP. Add exact expiry extension for finite VIP; refuse lifetime VIP without consumption. |
| Codes cannot be copied after creation | backend/prisma/schema.prisma:193–205; routes/promos.ts:12–13 | Only SHA256 stored; non-recoverable. New codes encrypted at rest, existing codes retain hashes and may be restored only with matching operator-supplied code. |
| Remote config save sends entire fetched record | admin-panel/src/components/ControlSettings.tsx:14–17 | Fragile when backend adds fields: strict schema rejects unknown fields. Use explicit write DTO and structured validation feedback. |
| Settings refresh does not reload settings | admin-panel/src/App.tsx:31–36,42–43,75 | Global Refresh reloads servers/stats only, cannot retry a failed config fetch. Use page-specific refresh. |
| Config only fetched once; failures silent | app/src/main/java/com/nsec/vpn/data/config/RemoteConfigManager.kt:29–65; MainActivity.kt:142–143 | Admin changes do not reach already-open app, and no visible config-fetch error. Add refresh/status. |
| Config kill switch absent, maintenance/update values unused | backend/src/types.ts:85–103; MainActivity.kt (no maintenance/force-update consumers) | Placement toggles exist but no global ads control; version/maintenance are delivered but not enforced. |
| Admin stale-response races | admin-panel/src/App.tsx:31–45 | Late requests can repopulate data after logout or overwrite newer search/page response. Add session/request guards and stale response cancellation. |
| Node edit sends extra fields | admin-panel/src/components/ControlNodes.tsx:19–20,31–33 | Spreading full node leaks id/health/time into write payload; old permissive schema strips them. Construct explicit DTO. |
| No node reorder | backend/prisma/schema.prisma:75–104; routes/admin.ts:129–132 | No sort order; introduce additive sortOrder and transactional complete-order endpoint. |
| Device search UI differs from API | admin-panel/src/App.tsx:86; backend/src/routes/admin.ts:255 | UI promises model search; backend filters only deviceId. Add validated search + exact identifier detail. |
| Expired entitlement race | backend/src/middleware/auth.ts:62–69 | Unconditional downgrade based on earlier read can overwrite concurrently-granted VIP. Conditional update on original tier/expiry. |
| Dashboard counts expired rows | backend/src/services/stats.ts:46–47 | VIP/active lease counts ignore expiry. Filter current unexpired entitlements/leases. |
| Audit and export handlers lack async failure catch | backend/src/routes/admin.ts:452–477 | Express 4 does not route rejected async handlers automatically. Catch errors with stable response. |
| Audit writes can silently disappear | backend/src/routes/admin.ts:30–45 | Helper suppresses database errors. Make administrative database mutations and their audit entries atomic where possible. |

## Existing behavior checked, not a defect

Admin endpoints verify JWT and re-read roles from the database (`backend/src/middleware/auth.ts:88–125`). Device authentication reads the current database entitlement, rejects blocked devices, and does not trust a caller-supplied device ID. Promo redemption currently uses database transactions, advisory locks, a global count and a unique promo/device index. DNS choice already survives restarts via KeyStoreManager; the missing piece is active tunnel reconfiguration. Backend server list selects a bounded field set and filters enabled/recently healthy nodes. There is no evidence in this audit that live Remote Config saves always fail: code defects above explain specific retry/refresh and delivery gaps, but deployment-specific errors require actual responses.

## Migration choices and constraints

- Preserve existing minute grants exactly: store days as a decimal fraction, keep `passMinutes` for compatibility, and require whole days for newly-created campaigns. No automatic rounding or entitlement changes.
- Existing SHA256 codes cannot be reversed. Preserve them; mark their copy action unavailable until the administrator supplies the exact matching code. New retrievable codes require a dedicated environment encryption key, never stored in source or the database.
- Campaign delete is an irreversible disable/tombstone so redemption history and one-use evidence survive. It prevents new redemption and hides the campaign from normal lists; it never revokes already-earned VIP.
- No support email is invented: operator supplies it in Remote Config.
- IPv6 DNS must not be advertised as reachable on the current IPv4-only tunnel.
- No VPN throughput claims for ordinary HTTP from the excluded app. A real tunnel transport is required.

## Delivery sequence

Phase 1: admin/promos, server requests backend/admin, device detail, node order, schema and tests. Phase 2: Remote Config/Ads delivery. Phase 3: Android items 1–7 plus honest troubleshooting. No release readiness or live device test is implied by a source build.
