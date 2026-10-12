# Nsec VPN repair audit — release remains NO-GO

This audit reads the public `xtrommm/Nsec-VPN` monorepo at base commit
`fe277573da260bb31ee9b4652ccf9afca0811950`. Android, backend, admin, agent,
installer and docs all live here; these are not six independently verified
repositories. Other private repositories were not available to this session.
User-provided node facts are accepted as facts, not independently re-tested.
No SSH session, production database, admin session or physical phone was available.
Changes are local on `fix/e2e-working-vpn`. GitHub branch creation returned
HTTP403, `Resource not accessible by integration`; no remote branch/PR exists.
This is a concrete reviewable repair candidate, not a verified working deployment.

## 1. Chain map

Paths below are relative to this monorepo. Line references in findings refer
only to the immutable BASE commit above; changed files naturally have new lines.

| Hop | Exact files/functions | Data and behavior |
|---|---|---|
| Admin input | `admin-panel/src/screens/NodesScreen.tsx`, node form/validation/save callbacks; `admin-panel/src/App.tsx`, server CRUD and health callbacks; `admin-panel/src/lib/nodeValidation.ts::validateNode` | Endpoint, public key, management origin/credential, geography, private subnet, DNS/MTU/capacity. No guessed geography. Test-node and explicit key synchronization. |
| API/database | `backend/src/routes/admin.ts`, `POST/PUT /servers`, `/servers/:id/ping`, new `/servers/:id/sync-public-key`; `backend/src/types.ts`, node Zod schemas; `backend/prisma/schema.prisma`, `ServerNode`, `Device`, `PeerSession`; `backend/prisma/migrations/*` | One node record. The legacy column `agentSecretHash` now stores only an env:VARIABLE reference. Actual credential material is resolved from backend environment and is never newly stored in DB; legacy raw rows must be migrated and rotated. References remain omitted from public node responses. |
| Public list | `backend/src/routes/servers.ts`, `GET /servers`; `backend/src/services/nodeHealth.ts::testNode/refreshNodeHealth` | Enabled + healthy + checked within120 seconds. Authenticated agent key/UDP port/observed public IPv4 must match the endpoint. Admin health cannot prove Internet forwarding. |
| Peer negotiation | `backend/src/middleware/auth.ts::authenticateDevice`; `backend/src/routes/vpn.ts`, `POST /connect`; `backend/src/services/peerProvisioning.ts::provisionPeer`; `backend/src/services/ipPool.ts::allocateIp` | Authenticate/tier-check, device and node transaction locks, capacity/IP reservation, confirm agent registration/persistence, then create/update session and return config. Agent/DB failures trigger compensation, never successful config. |
| Agent | `backend/src/services/agentClient.ts::request/addPeer/removePeer/getNodeHealth`; `agent/src/index.ts::auth/serialize/add/remove/saveInterface/saveLeases/start` | Signed request and signed response, bounded timeouts, argument-array execFile, durable expiring leases and wg-quick save. Actual kernel key/port/handshake counts, no fabricated health. |
| Android transport/cache | `app/src/main/java/com/nsec/vpn/core/network/NetworkModule.kt`; `data/api/NsecVpnApi.kt`; `data/repository/VpnRepository.kt::refreshServers/authenticateDevice/fetchTunnelConfig` | HTTPS `https://api.nsecvpn.com/api/v1/`; live list → DTO → model. No Room database or JSON/asset server cache in this implementation. Only live-fetched in-memory servers and local settings/favorites persist through their existing mechanisms. On failure retain real in-memory data or show error. |
| Android tunnel | `data/local/KeyStoreManager.kt::getOrCreateWireGuardKeyPair`; `vpn/TunnelConfigBuilder.kt::build`; `vpn/TunnelNegotiation.kt::establish`; `vpn/FailoverPolicy.kt::next`; `vpn/WireGuardTunnelManager.kt::startConnect/connectWithFailover/waitForHandshakeOrTimeout/startStatsMonitoring/recoverStaleTunnel/teardown`; official `GoBackend.VpnService` | Same stable device-generated client keypair supplies API public key and tunnel private key. API confirmation first, validated IPv4 full-route config pinned to the agent-confirmed public IPv4 (avoids selecting another DNS address), sole real WireGuard TUN, Connecting until fresh real handshake. Notification companion never creates another TUN. |
| State/UI | `MainActivity.kt`, state observer and ad/Connected navigation; `ui/screens/HomeScreen.kt`, `ConnectedScreen.kt`, `SpeedTestScreen.kt`; `ui/components/ServerCard.kt`; `tiles/VpnTileService.kt`; `vpn/NsecVpnService.kt`; debug-only `app/src/debug/.../DebugDiagnosticsScreen.kt` | Real public node IP/name/location after handshake; clear during reconnect; failover updates actual node. Actual counters, no fabricated throughput/ping. Debug tunnel address/handshake/counters are excluded from release UI. Tile/notification follow the shared state. |

Environment inventory: debug/release, no product flavors; both use the same
hardcoded HTTPS API. Admin calls `/api/v1/admin`; Vite development proxy targets
localhost4000; production admin nginx proxies backend4000. Deployment compose
uses PostgreSQL and backend/admin containers; node installation is separate.
Release cleartext is blocked. No certificate pinning implementation was found.
Remote app/ad config comes from `/config`; preference overrides and real public
DNS choices remain local. Unused Firebase/Google Services/secrets-plugin scaffolding was removed after confirming there are no Firebase calls or custom secret BuildConfig fields in app source; no fake google-services.json was introduced. Compile-time/default ad identifiers and signing
configuration were deliberately left unchanged.

Public check performed here: GET `/api/v1/config` at the configured HTTPS API
returned200, valid `{success:true,data:...}`, version minimum/latest1 and
forceUpdate=false. This proves that public route responds, not that the live
server list, peer API, installed APK or database matches this checkout.

## 2. Root causes and hypothesis results

| Finding and severity | BASE file/line evidence | Why it matters / repair |
|---|---|---|
| **Critical: no real Android WireGuard engine** | `app/src/main/java/com/nsec/vpn/vpn/WireGuardTunnelManager.kt:32–59,102` | Custom NsecVpnBackend stores state only; it does not transmit WireGuard. Replaced with official GoBackend. This directly explains false connection and no packets/handshake. |
| **Critical: raw TUN with no packet engine** | `app/src/main/java/com/nsec/vpn/vpn/NsecVpnService.kt:60–90` | Hardcoded10.8.0.2 TUN creation does not implement WireGuard; exceptions were swallowed. Rebuilt as notification service; official backend owns sole TUN. |
| **Critical: handshake timeout returns success** | manager`:283–303`; Connected state`:246`; service notification`:154` | Fake UP state and unconditional true after timeout could publish Connected/ad with no handshake. Real peer statistics, 15-second failure, teardown and capped failover now gate success. |
| **High: invented traffic/diagnostics** | manager`:348–372`; `ui/screens/SpeedTestScreen.kt:72–78`; `HomeScreen.kt:261`; `ui/components/ServerCard.kt:113` | Random transfer/speed/jitter and fixed ping numbers cannot prove VPN traffic. Replaced with actual counters and explicit unavailable ping; throughput screen samples another app's tunneled traffic. |
| **Critical: mock management bypass** | `backend/src/routes/vpn.ts:135–155,222` | http://mock skipped actual agent registration yet returned config. Removed. Every connect needs authenticated durable confirmation. |
| **High: memory-only/stale peer reuse** | `backend/src/routes/vpn.ts:82–100`; `agent/src/index.ts:61–67` | Existing DB sessions returned config without restoring missing kernel peers. wg-quick save errors were swallowed. Every connect registers/confirms again; save failures return503; startup restores valid lease metadata. |
| **High: allocation race and bad uniqueness** | `backend/src/services/ipPool.ts:48–79`; `routes/vpn.ts:122`; `backend/prisma/schema.prisma:106` | Selection transaction ended before session creation; simultaneous requests could choose same address. Added PostgreSQL advisory locks and active-only unique indexes with migration duplicate guards. |
| **High: silent subnet fallback** | `backend/src/services/ipPool.ts:15–16,39` | Invalid CIDR silently became10.8.0.*. Now invalid/private non-/24 subnet fails validation. /24 is explicitly the currently supported mode, not arbitrary CIDR support. |
| **High: wrong node identity after cluster selection** | `backend/src/routes/vpn.ts:65–79` | Backend could substitute another node while UI retained requested identity. Backend now honors exact node; config carries actual node metadata; client performs bounded same-location failover. |
| **High: health false positive/no identity check** | `backend/src/services/agentClient.ts:129–131`; `agent/src/index.ts:110–126` | HTTP200 with online=false could become online=true; original health lacked actual node key/port/IP. Strict signed health, matching key/port/resolution and fresh-list filtering added. |
| **Critical: disconnect key trusted from caller** | `backend/src/routes/vpn.ts:223` | A device's peer ID could be paired with another public key. Removal now uses only its authenticated stored session key. |
| **Critical: UUID-only device account takeover** | `backend/src/routes/auth.ts:19–35` | Knowing UUID could retrieve new credentials for that identity. V2 device-held registration secret/proof and serialized registration added; old unproved registration returns426/409. Blocking is rechecked inside peer transaction; block/connect race covered. |
| **High: defaults/exposed credentials** | `agent/src/index.ts` fallback; backend JWT defaults in index/auth/admin/middleware; `deployment/install-wireguard-node.sh:207`; `backend/src/routes/admin.ts:121,176` | Default signing/shared credentials, printed installer credential, raw node management credential returned and audited. Defaults/printing removed, responses/audits sanitized. Rotate deployed values and protect DB. No values reproduced here. |
| **High: installer destroys existing identity** | `deployment/install-wireguard-node.sh:54,129` | Rewrites configuration and has a second embedded agent, including swallowed save failures. Installer preserves existing interface/key/peers, builds authoritative agent source, sets scoped persistent firewall services. |
| **High: management network exposure** | `deployment/install-wireguard-node.sh:22–24,61–62,170`; `agent/src/index.ts:131` | Port51821 is management TCP, never WG UDP. Host restriction added; Alibaba rule remains manual. HMAC integrity/authentication does not encrypt HTTP; private management network or HTTPS reverse proxy remains a production prerequisite. |
| **High: vault not Keystore and plaintext fallback** | `app/src/main/java/com/nsec/vpn/data/local/KeyStoreManager.kt:26–60,161` | Encryption key stored beside ciphertext and encryption errors could return plaintext. Android Keystore AES/GCM, fail-closed storage and stable derived keypair added. Legacy AES data is readable for migration; corrupted existing identity/key ciphertext fails instead of silently generating a replacement. Device ID and token writes are committed atomically under the shared vault lock; hardware-backed storage is not guaranteed on every device. |
| **Critical: simulated paid access** | `data/billing/PlayBillingManager.kt:44–50`; `backend/src/services/billing.ts:40–59`; MainActivity local-duration granting | App/backend could grant VIP without real Google proof. Actual BillingClient purchase/restore + subscriptionsv2 verification, acknowledgement, expiry and purchase-to-device binding added. Play Console and real-purchase checks remain manual. |
| **High: reward granted without an ad** | `data/monetization/AdMobManager.kt:178,200,204` | Missing ad/error still invoked reward; arbitrary promo text also unlocked access. Legacy local rewarded-expiry preferences can no longer grant paid status; only registration/profile/purchase responses authorize it, with expiry checks. No invented entitlement; signed Google SSV and replay-safe transaction grants added. Promo service is not configured and now fails explicitly rather than granting fabricated access. |
| **High: no-logs claim contradicted code** | `backend/src/index.ts:8,42`; `backend/src/db.ts:8`; nginx defaults; docs architecture privacy statements | HTTP/Pino/Prisma/proxy logging could retain source request metadata; claim of RAM-only public keys conflicted with DB. Removed request/query logging, sanitized errors/audits and corrected persisted-lease, hardware-ID and local-reset/deletion claims in the app, README and privacy/deployment docs. Cloud/CDN/provider logging remains unverified. |
| **High: false kill-switch status** | `app/src/main/java/com/nsec/vpn/ui/screens/SettingsScreen.kt:92,296` | Opening Android VPN settings saved an enabled flag without confirming lockdown. Replaced the misleading toggle with an actual settings action; disclose the excluded-ad conflict. |
| **High: TOTP ignored** | `backend/src/routes/admin.ts:47–84` (login without TOTP); `backend/prisma/schema.prisma:26–27` | Added server-side TOTP enforcement if enabled (seed resolved only from an env reference; legacy seeds require private migration), server-side actual admin role/existence checks and HS256 restriction. Enrollment UI is not implemented; operator must establish safe enrollment before publishing. |

**Explicitly not concluded from source:** production stored private endpoint,
wrong port, stale server public key, bad DNS name, cloud ingress, token/clock
mismatch, or installed APK/version mismatch. No live DB/node/device evidence was
available. Enter47.85.109.170:51820 and the CURRENT key only after rotation/test.
Server forwarding/NAT/internet are ruled out as the primary fault by the user's
verified facts, not by commands run here. Never substitute172.30.148.192 or51821.

Client key swap/regeneration BETWEEN API and builder is not supported by the
base path: one KeyPair supplied both values; regeneration per attempt existed
but was not itself evidence of mismatch. Stable persisted key use improves
reconnect/idempotency. In the supplied BASE Home/Connected source there was no
server-IP label; I cannot identify the exact installed APK's alleged dummy-IP
origin from that source. The raw tunnel address was hardcoded in its service,
and diagnostics/latency were fabricated. New UI has one confirmed public-IP
source and never labels the tunnel /32 as server IP.

The own-app exclusion is retained. Official GoBackend protects its outer UDP
sockets. The builder uses only disallowed packages; empty user exclusions means
all OTHER apps use VPN. The Nsec app intentionally remains excluded. There is
no production fake server asset/seed/Room fallback found in the inspected path.
UI input placeholders/design-preview comments are not fabricated runtime data;
cryptographic randomness and toast IDs are also not mock VPN measurements.

## 3. Fixes and rebuilds per component

Build tools were aligned to the AGP9.1 default Gradle9.3.1 with a verified distribution checksum. The WireGuard library was updated to released1.0.20260102; its handshake/statistics API was inspected and ARM64 LOAD alignment was verified as0x4000 (16KiB). APK-level native validation and actual devices remain required. Lint cleanup preserves original branding with a legacy XML inset, removes duplicate notification bitmaps and unused colors, improves ad badge readability, uses explicit timer locale and updates Android test libraries. Release lint and warnings-as-errors are enabled. Two scoped lint exceptions are justified: prepared VPN apps qualify for systemExempted without exact alarms (official Android documentation), and credential commits need their Boolean failure result, which KTX edit discards.

One branch/one proposed draft PR because all components share the monorepo:
`fix/e2e-working-vpn`; title **Restore real WireGuard negotiation, persistence
and verified connection state**. PR publication is blocked403, not complete.
`scripts/open-repair-pr.sh` provides the authorized branch-only push/draft-PR
commands once the operator has working write access. No main/force push.

Android's packet path and notification service were rebuilt because the old
backend had no engine to repair. Shared UI/state, tile, notification, themes,
exclusions, failover, ads and billing surfaces remain. WireGuard builder is one
validated implementation; real handshake/counters replace simulated signals.
Agent/installer were rebuilt around one authoritative source because duplicate
embedded agents and swallowed persistence could not give reliable confirmation.
Backend/admin are coordinated repairs with migratable additive model changes.

No ad IDs, signing configuration or third-party notices were changed. APK
versionCode2/versionName1.0.1 distinguish this repair. Existing test ad application
ID and release fallback to debug signing remain operator review items; do NOT
publish a debug-signed release. No live install/database edit occurred here.

## 4. Data fixes for the operator

`scripts/migrate-e2e.sql` / Prisma0002 add actual node health/config fields,
device proof hash, reward replay records, purchase claims and active-only lease
uniqueness. Duplicate active leases stop migration instead of deleting data.
Baseline0001 must be reconciled with the existing schema before marking applied;
use `scripts/verify-system.md`. Back up privately and rehearse on a restored DB.
The integration migration/concurrency harness is provided but was not run here.

`scripts/correct-node.sql` is targeted and reversible: supply the REAL node ID
and CURRENT PUBLIC key; inspect UPDATE1 before COMMIT. It records public-node
fields for reversal and changes only endpoint/key/health status. Use interactive
psql `\i`; it deliberately leaves the transaction open for COMMIT/ROLLBACK.
Supply a configured backend env:VARIABLE credential reference as well; raw legacy credentials are refused. No credential material, geography, agent origin or unrelated node is invented.
Never restore a compromised old key. Test node after key sync/correction.
Reconcile unmanaged legacy peers with database reservations before migration;
do not blindly clear all WG peers or reuse a reserved address.

## 5. API changes and compatibility

| Endpoint | Change / errors | Older client behavior |
|---|---|---|
| POST `/auth/register-device` and new `/auth/register-device-v2` | registrationSecret64hex required; optional legacy JWT registrationProof;426 APP_UPDATE_REQUIRED,409 DEVICE_PROOF_REQUIRED;403 DEVICE_BLOCKED; existing DB_ERROR | Explicit v2 registration switch. Old unproved registration fails closed; UUID-only compatibility would preserve the takeover. Existing valid access/refresh tokens still work. |
| GET `/auth/me` | New authenticated actual id/tier/passExpiresAt | Additive; new app polls server-confirmed entitlement. |
| GET `/servers` | Only enabled, healthy, recently checked nodes; ungrouped real endpoint/publicIPv4 fields | Existing list shape preserved; offline/mismatched nodes disappear. App still requests grouped=false. |
| POST `/vpn/connect` | Existing peerId/tunnelConfig fields retained. New endpointHost,endpointPort,nodeId,nodeName,locationName,publicIpv4; explicit DNS,MTU,allowedIps,current key/clientAddress | Old decoder ignores additive fields; old simulated engine cannot be fixed by API compatibility. New app requires real node display metadata. |
| Connect errors |404 SERVER_NOT_FOUND;409 NODE_DISABLED/KEY_ALREADY_REGISTERED;503 NODE_OFFLINE/SERVER_FULL;502 AGENT_UNREACHABLE;403 TIER_UPGRADE_REQUIRED/DEVICE_BLOCKED; validation400 VALIDATION_ERROR;500 INTERNAL_ERROR;429 CONNECT_RATE_LIMIT | Never config on failure. Same-location client failover or clear error. |
| POST `/vpn/disconnect` | Supplied clientPublicKey remains accepted but ignored; authenticated stored key removed;502 AGENT_UNREACHABLE leaves lease reserved | Existing request shape retained. |
| Admin GET/POST/PUT `/servers` | Credential omitted; agentSecret field now requires env:VARIABLE reference (raw value rejected400); additional configurable dnsServers,mtu,maxPeers and health/publicIPv4 fields; validation400 | Admin rebuild required for new fields/preview; existing CRUD shape retained. |
| POST `/admin/servers/:id/ping` | Signed health/identity/resolution/public preview | Existing health action richer. |
| POST `/admin/servers/:id/sync-public-key` | New explicit SUPER_ADMIN action; actual agent key only | Key changes are not silently trusted on schedule; mismatch quarantines until explicit sync. |
| Admin login | Optional totpCode;401 TOTP_REQUIRED when enabled | Enrolled admins need authenticator input and env-reference seed migration; UI updated. |
| PUT `/admin/devices/:id/block` | Boolean-only; block committed before peer cleanup;502 DEVICE_BLOCKED_PEER_REMOVAL_PENDING if agent fails; background retries | Clear partial-result error; never falsely claim revocation while agent unreachable. |
| POST `/billing/verify` | Real Google subscription verification/expiry/acknowledgement; one device identity per receipt; premium peer lease capped at entitlement expiry;400 SUBSCRIPTION_INVALID;409 PURCHASE_ALREADY_BOUND;500 BILLING_ERROR | No invented receipt accepted when credentials unavailable. Multi-device purchase migration is an explicit product decision. |
| GET `/billing/rewarded/ssv` | New Google signature-verified callback, per-device user_id, transaction replay guard;400 REWARD_VERIFICATION_FAILED | Client callback alone grants nothing; configure AdMob SSV. |
| Agent `/api/v1/agent/peers`, `/health` | v2 method/path/body-bound signatures plus signed responses, persisted=true confirmation, actual health/key/IP/port/handshake counts;400 invalid input;401 expiry/replay;403 signature;503 save/removal/health failure | Updated agent rejects v1 by default (426 AGENT_PROTOCOL_REQUIRED); ALLOW_LEGACY_AGENT_V1=true is an explicit protected temporary migration switch; backend requires v2 signed response and new confirmation, so deploy agent FIRST. Do not retain v1 support indefinitely without planning version cutoff. |

Other existing endpoint errors remain unchanged. Input-validation details contain
field/messages, not submitted secrets. Refresh rotation is serialized by token
conditional update; registration and peer allocation use DB locks. Agent external
side effects cannot join a PostgreSQL transaction: compensation and bounded
lease expiry cover ambiguous timeout/crash, with manual reconciliation still
needed for legacy orphan peers. This is a limitation, not perfect distributed
atomicity.

After the rebuilt APK is actually available, set admin minimum version2 and
force-update; do not lock old users out before distribution. Expired/unavailable
legacy ownership proof needs recovery/re-registration policy, never a UUID
bypass. Changing JWT/agent keys has no dual-key grace implementation.

## 6. Verification results and required commands

Observed here: source/flow evidence above; public HTTPS config200; component
checks in section8; scoped history scan; shell syntax/static checks. NOT observed:
actual node key/DB records, production management authentication, peer persistence
on that node, a real phone handshake, exit IP, DNS/IPv6 behavior, failover/revocation,
real Play purchases or Google's live SSV callback.

Run first, privately on the node:

```sh
sudo bash scripts/node-health.sh
sudo bash scripts/manual-peer-test.sh
sudo watch -n1 'wg show wg0 latest-handshakes; wg show wg0 transfer'
```

Import the generated ROOT-PRIVATE config/QR into the OFFICIAL WireGuard app;
never paste its private key/config into chat. Chrome exit IPv4 must be47.85.109.170,
handshake recent/nonzero and counters rising. The script uses observed public
IPv4/current key/listen port/fresh temporary client key, explicit public DNS,
IPv4 full route and keepalive25. It prints protected file paths rather than
secret-bearing config text; cleanup removes peer and saves wg0. Official-app
success isolates Nsec software. Failure also can mean mobile UDP restrictions;
it is not exclusive proof of a cloud firewall fault.

Follow `scripts/verify-system.md` for exact admin/server-list/connect curl shapes,
peer restart/reboot persistence, tcpdump LOCAL ONLY, Chrome exit/DNS/IPv6, UDP
block, agent stop, node outage and connected-device revocation tests. Packet
captures contain personal metadata; never persist/upload raw capture or wg dump.
Paste only pass/fail, public IP/current public key/node identity, tunnel /32,
handshake age, aggregate RX/TX, API errors and redacted build/test failures.

Chosen display option(a): backend public node IP + verified handshake + real
counters, with explicit external-app exit-IP proof. In-app IP lookup bypasses
VPN by design for ads and cannot prove VPN exit. No optional misleading lookup
was added. A handshake proves authenticated WG contact, not working upstream
Internet/DNS; rising counters + Chrome exit-IP/DNS tests are separate acceptance.

## 7. Manual items

1. Alibaba Security Group: inbound UDP51820 from0.0.0.0/0; TCP51821 only from
   actual backend egress IPv4/32. Preserve SSH restrictions. Verify ECS/public-IP
   mapping; never set private172.30.148.192 as endpoint.
2. Rotate the compromised node key using `scripts/rotate-node-key.sh` in a
   maintenance window, sync CURRENT public key, Test node and reconnect. Enter
   endpoint47.85.109.170:51820, actual location/name, subnet10.8.0.0/24, public
   resolvers1.1.1.1/1.0.0.1, MTU1420, actual agent origin and backend env:VARIABLE credential reference.
3. Rotate any deployed historical JWT/agent/admin defaults; review cloud/CDN/log
   retention, admin credential/2FA enrollment and protected DB/backups. Scoped
   61-commit scan found JWT fallback locations in backend index/middleware/auth/
   routes auth/admin and agent fallback in agent/src/index.ts. No tracked private
   PEM/keystore/.env found by these filename/regex checks; not a complete secret
   audit. Full metadata-only finding list is included in the review package.
4. Establish private or verified HTTPS management transport. HMAC over HTTP gives
   integrity/authentication, not confidentiality. Safe zero-downtime node key
   rotation needs a second independent healthy node; single wg0 cannot serve two
   server identities. JWT/agent key grace support remains a larger change.
5. Provide functioning GitHub write access or apply source package locally and
   use the supplied branch-only draft PR script. No PR URL is fabricated.
6. Rehearse migration on restored PostgreSQL, reconcile legacy peers, run database
   integration and Android checks, then complete physical phone/server evidence.
7. Play Console product/base-plan IDs, real verification service-account access,
   AdMob SSV unit/callback and rewarded duration must be configured. Ad IDs were
   untouched. Multi-device paid entitlement and promo issuance are product
   decisions; current receipt binding is one identity, promo fails unconfigured.
8. Preserve production signing and raise/store version policy after APK readiness.
   Review existing VIP/REWARDED rows against real receipts/rewards: earlier simulated grants cannot be distinguished from valid purchases from source alone; do not bulk-delete legitimate entitlements. Existing debug-signing fallback and test AdMob app ID require YOUR review;
   no signing or IDs were silently changed. Always-on lockdown can conflict with
   excluded ads; do not promise both without device verification.

## 8. Build/test status

Follow-up validation: backend type check, build and 48 unit tests pass; agent type check, build and 8 tests pass; admin type check, build and 2 tests pass. Deployment firewall regression passes (1 test). Recovered Android XML proves 50 tests with zero failures/errors; debug and release lint reports both say “No issues found”, and both APKs were assembled in the previous run. Android was not rerun after environment recovery because the temporary SDK/JDK were lost; no Android source changed during this follow-up. Initial npm runs emitted a workspace proxy-configuration warning; the final rerun removes the conflicting workspace npm HTTP-proxy environment variables without changing project configuration. No project lint/build warnings appeared in those Node checks. Previous npm dependency audits reported zero vulnerabilities for the three Node projects; an Android dependency vulnerability audit remains outstanding.

PostgreSQL integration, Docker builds, Ubuntu installs/reboots, real billing/reward flows, physical-device foreground service, DNS/IPv6 and failover tests have NOT run. The recovered release APK is a validation artifact, not a production-signed release. ELF inspection of every packaged native library confirms 16KiB LOAD alignment on arm64-v8a and x86_64; 32-bit WireGuard libraries use 4KiB. APK zip alignment and device acceptance remain separate checks. Compose now requires BACKEND_ENV_FILE for per-node, billing and reward environment values; no credential material is stored in node records. TypeScript lint commands
are compiler checks, not an added comprehensive ESLint ruleset. Unit/mock tests
prove code contracts; they are not real database/WireGuard integration. Docker
images and Ubuntu install/reboot were not executed here. Newly added CI supplies
PostgreSQL16 migration/concurrency/rollback and Android lint/tests/build, but CI
cannot run until the branch is published. Android lint reports are clean; full release readiness is not established.

## 9. Suggestions

Implemented low-risk improvements: health/freshness gating, bounded same-location
failover/reconnect, idempotent leased peers, verified node identity/publicIP,
response HMAC/replay rejection, safe command arrays, actual diagnostics,
per-device connect rate limiting, secure device proof, signed rewarded grants,
real purchase verification, source-IP logging removal and vulnerability updates.

Proposed larger work: management mTLS/private network; safe TOTP enrollment and
recovery; true dual-key grace for JWT/agents; node replacement orchestration and
outbox-based orphan reconciliation; per-device Play Integrity registration;
monitoring of node aggregate health only; local Unbound with verified DoT
upstreams and explicit resolver capability; WireGuard preshared keys with safe
lifecycle; explicit subscription multi-device policy and verified promo service; Google Play RTDN renewal/refund synchronization (current purchase/restore verifies live Google data, but background renewal/revocation needs a verified webhook).
Benefits are stronger identity/privacy/recovery; costs include client/server
rollout, secret lifecycle, billing ownership migration and operational support.
Unbound and PSKs were not silently installed or claimed as working.

## 10. Publishing decision

**NO-GO.** The main false-tunnel cause is proven and a coordinated source repair
is prepared, but required database and real-device/node acceptance are
not all proven. The compromised node key, actual firewall/management transport,
2FA enrollment, live billing/SSV, production signing/ad setup and rollout remain
operator gates. Do not deploy this candidate as a claimed working production VPN
until the report's remaining checks pass. No main push, deployment or history
rewrite was performed.

### Source references for engine/platform contracts

- [Official WireGuard Android GoBackend](https://github.com/WireGuard/wireguard-android/blob/1.0.20260102/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java): outer socket protection and single-peer default-route family blocking.
- [Official WireGuard Statistics](https://github.com/WireGuard/wireguard-android/blob/1.0.20260102/tunnel/src/main/java/com/wireguard/android/backend/Statistics.java): real per-peer handshake timestamp and transfer counters.
- [Google Play Billing integration](https://developer.android.com/google/play/billing/integrate): real purchase flow/verification and acknowledgement.
- [Google AdMob server-side reward verification](https://developers.google.com/admob/android/ssv): signed raw-query verification and public signing-key distribution.
- [Android AGP9.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes), [Kotlin Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html): build-tool compatibility review.

### Follow-up corrections and remaining leak risk

The public `GET /api/v1/legal/privacy` and `/legal/terms` response shapes remain unchanged, with content version1.1.0: durable operational records and installation identity are now described accurately, invented operator identity/effective date and unverified 10Gbps guarantees are removed. The operator must approve final policy/contact/retention information before publishing. Two response regression tests cover these corrections. Admin metrics show unavailable until received, health results do not fabricate missing counters, and the dashboard labels its DB count as active peer leases rather than proven live tunnels.

During handshake timeout/failover teardown, the TUN can be absent while a replacement peer is negotiated. Without OS lockdown, included apps may use the normal network during that interval. This candidate must not be advertised as leak-proof across reconnects. Always-on/lockdown may block the deliberately excluded advertising app; this product trade-off requires an explicit decision and physical-device evidence. Within an established full IPv4 tunnel, official GoBackend blocks the unconfigured IPv6 family; this is a code contract, not a completed device leak test.
