# Changelog

## Unreleased — Audit follow-up

- Add an explicit, bounded Restore Google Play Purchases flow without purchase UI or client-only grants; document separate promo/paid coverage selected by the owner.

- Cover consent-aware ad flow and match restored location names without case sensitivity.

- Add encrypted Play receipts, authenticated RTDN and bounded authoritative subscription rechecks; preserve non-purchase VIP and legacy unattributed grants, reject receipt/product reuse and stale responses, expire premium peers when the last grant is revoked, and retain self-reported numeric build metadata.
- Add guarded billing-key configuration and exact-image schema approval scripts; update privacy disclosure for encrypted receipts and 90-day retention.

- Remove eight legacy admin modules unreachable from the current entry point; preserve the working console routes and license files.

- Add bounded hourly 90-day retention cleanup with transaction-time eligibility checks and preserved entitlement/replay/recovery/peer protections.

- Reject public cleartext agent management origins and default new node agents to loopback; retain private encrypted-channel compatibility.

- Enforce existing forced-update settings during peer reservation and registration, retain backward compatibility while disabled, and stop failover on update-required responses.

- Restore interrupted connections from an encrypted node intent with fresh negotiation, clear intent on manual disconnect, register the WireGuard always-on callback, and apply split-tunnel changes through a serialized reconnect.

- Gate all ad placements on UMP authorization and SDK initialization, invalidate cached ads when choices change, and provide privacy options/retry controls in Settings.

- Complete local disconnect without waiting for remote peer removal; bound asynchronous cleanup and disable repeated Connected-screen disconnect taps.

## Unreleased — Deep audit

- Preserve main's recursive build-directory ignore update in the v2 repair package and document branch publication versus pull-request creation.

- Add an isolated Git-bundle publisher for connector write failures; preserve original local edits and provide draft-PR creation without main updates or deployment.

- Require Node 22 rather than retaining the EOL Node 20 runtime during node installation; add the runtime decision regression and existing-node maintenance instructions.

- Add a tested-checkpoint report with API compatibility, migration/schema approval commands, ranked proposals and remaining publishing blockers.

- Correct unsupported privacy/compliance/latency/disclosure documentation claims; distinguish operational records from traffic logs and list publishing verification gaps.

- Serialize tunnel activation/cleanup, retain notification/peer state on failed shutdown, expose operation errors, and add cancellation/shutdown regressions. Add a speed-helper deadline watchdog and suppress stale loss callbacks.

- Fix dual-stack endpoint validation and first-sample throughput, share permission-return eligibility checks, synchronize displayed grants, clarify split-exclusion reconnect requirements, recover error tile actions and remove the unused boot permission.

- Ignore older profile results after a newer grant is applied; add a delayed-profile/promo regression.

- Coalesce duplicate catalog refreshes, preserve explicit refresh, index old node metadata once and scope configuration polling to the foreground; retain exact reward expiry scheduling.

- Use monotonic native-ad refresh timing and reduce ad-free expiry polling wakeups.

- Refill released native ads, replace loading polls with state events, use media layouts for video, recreate changed templates and test the actual layout policy instead of a duplicated test implementation.

- Clamp promo/usage/audit pages after results shrink and preserve modal keyboard focus while operations are busy.

- Guard health results against stale credentials/capacity/DNS, return committed node writes with explicit health errors, support legacy safe device IDs in admin lookup, and neutralize spreadsheet formula injection.

- Serialize Android authentication/recovery and reuse concurrent renewal results; propagate cancellation and retain pending rewards during transient rate limits. Add concurrent-refresh and cancelled-refresh regressions.

- Add opt-in CIDR-based proxy trust, separate readiness from user API quotas, and reject malformed agent request bodies with safe errors.



- Protect entitlement expiry against concurrent grants/recovery, preserve longer VIP on purchase verification, and handle profile database failures without unhandled async rejections.

- Commit durable peer reservations before agent mutation; track uncertain keys, compensate only under locks, reconcile abandoned reservations, and rotate disconnect handles on renewal. Add the nullable pending-public-key migration and regression/integration tests.

- Require signed AdMob verification before rewarded VIP; make claims read-only, guard ticket ownership/event time, prevent replay/expiry extension, and poll pending verification without delaying local ad-free time.

- Record the current main commit, security/core/admin/performance findings, dependency/history scan scope, and device/operator verification limits before source fixes.

## Unreleased — Device VIP recovery and native splash

- Add authenticated recovery-PIN setup and Device ID + PIN restoration of existing VIP/promo ownership without extending expiry.
- Add persistent failed-proof locks, credential rotation, auth-epoch enforcement for access/refresh tokens, and safe old-peer lease cleanup.
- Add a Settings recovery sheet and update the displayed identity after recovery.
- Move Europe/EU branding onto the original native day/night launch window and remove the extra Compose splash and delay.
- Initially renamed Accept & Continue to Continue; a later update removed the extra in-app disclosure. Android VPN permission remains.
- Add an additive recovery migration, exact schema-approval helper, privacy disclosures, regression tests and rollout guide.

## Unreleased — App polish and location pools

- Capitalize source labels and messages while preserving headers, mottos, uppercase copy, timers, units and external content.
- Compact the speed-test gauge and layout; remove history display and result persistence.
- Group nodes by country/city, rank eligible nodes by latency/capacity, retry capacity misses across the pool, and report exhausted locations distinctly.
- Exclude actual failed nodes during failover, preserve verified connected-node details, and keep device/tier/transaction checks authoritative.
- Move catalog restoration off the UI thread, memoize location grouping, and make existing native ads reachable in small catalogs.
- Add theme-aware cold-start Europe branding, backend/Android regressions, and rollout documentation without a schema migration.

## Unreleased — Compact Home

- Replace the tall Home reward promotion with a compact duration/benefits row and direct Watch ad action. Tap the card for full VIP options.
- Reduce Home spacing and the connect control footprint so the existing server selector and native ad appear earlier.
- Keep the full VIP sheet, reward duration, entitlement checks, and ad configuration unchanged.

## Unreleased — Live load and rewarded VIP

- Refresh foreground server metadata without disconnecting; display the actual failover node and reject stale load.
- Report timestamped peer capacity from backend health samples, refresh every 30 seconds, and keep cleanup cadence unchanged.
- Replace VIP dialog with a reward-first bottom sheet and direct Home reward card showing backend-configured duration, ad-free access and VIP servers.
- Keep paid plans and add clear busy/no-fill/incomplete/pending/active feedback; remove unsupported speed and discount claims.
- Add regression coverage, 45-minute operator configuration steps, and two-app Google Play signing/release documentation.


## Unreleased — Android CI backup policy repair

- Added explicit Android 12+ and legacy no-backup policies to the speed-test helper, fixing debug/release DataExtractionRules lint failures.
- Replaced the main app’s empty/template backup policies with explicit cloud/device storage exclusions.
- Removed two unnecessary nullable calls reported by the Kotlin compiler.
- Added an early backup-policy CI guard, six regressions, and reports for both Android modules and build variants.
- Kept lint strict and all test/build/deployment gates enabled. See docs/ACTIONS-CI-REPAIR.md for evidence and verification limits.


## Unreleased — Charcoal appearance update

- Reskinned dark mode with neutral charcoal surfaces, brighter text and theme-aware navigation/server accents.
- Replaced the compact server-request dialog with a scrollable bottom sheet, validation feedback, character counter and submitting state.
- Documented support-email publishing through Remote config & ads.

## Unreleased — Android experience phase 2

- Fixed Connection Status footer, selected tab, Back behavior and saved navigation.
- Added configured support email/copy, authenticated server requests/cooldown, and stable installation ID/copy.
- Applied persisted Automatic/preset/Custom DNS to new and reconnecting tunnels, with explicit IPv4-node limits and active DNS display.
- Restored verified real server catalogs before background refresh; made entitlement and ping work asynchronous.
- Removed duplicate node/location/IP text above the Home logo.
- Replaced browser speed tests with VPN-bound, signature-protected companion measurements, cancellation, gauge and five-result history.
- Replaced invented Troubleshooting passes with measured observations and explicit unverified checks.
- Added paired APK installation and CI coverage; full Android/device verification remains pending.

## Unreleased — admin experience phase 1

### Added
- Source audit with immutable file/line evidence.
- Day-based promo CRUD, bulk creation, schedules, encrypted retrieval, usage records and deletion tombstones.
- Authenticated server request API and management page; exact/partial device lookup and details.
- Server reordering, audit pagination, modern responsive admin console and global ad control.
- Additive migration, private promo-key configuration and exact-release schema approval scripts.

### Fixed
- Promo redemption consuming a code without a finite-VIP extension.
- Concurrent expiry cleanup overwriting a renewed entitlement, and billing overwriting a recorded promo grant.
- Sensitive fields in device update responses; missing audit/error handling and expired dashboard counts.
- Remote Config retry/save/delivery gaps, whole-record form submissions and stale admin responses.

### Changed
- Existing minute promo durations are preserved exactly as fractional days; new console campaigns use whole days.
- Promo deletion retains one-use evidence; copies of legacy hash-only codes require the original code.
- Remote settings cache only validated live responses and refresh while the app is open.

### Pending
- App experience items 1–7 and honest Troubleshooting assistant (in progress).
- Separate signed speed-test helper, chosen by the user, plus on-device VPN-path verification.

### Remote Config client delivery
- Validated API responses persist locally and refresh every 30 seconds while the activity is open.
- Failed refreshes report a visible error and retain verified settings; invalid or unsuccessful responses are rejected.
- Global ads controls, support contact and publish timestamp have backward-compatible optional DTO fields.
- Connect waits for verified settings and respects maintenance/minimum-version controls.

---

Historical release entries are retained verbatim below; consult the current audit and setup guide for verified behavior.

# Changelog

All notable changes to the Nsec VPN project are documented in this file following the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) standard.

## [1.3.0] - 2026-10-12

### Added
- **In-App VPN Prominent Disclosure Dialog**: Implemented an explicit M3 prominent disclosure dialog detailing `VpnService` usage, zero-logs policies, and traffic routing prior to requesting system permissions, strictly meeting Google Play policy mandates.
- **AES-GCM KeyStore Encryption**: Upgraded `KeyStoreManager` with AES-128 GCM encryption and randomized IVs to secure JWT access/refresh tokens and ephemeral WireGuard private keys stored on device.
- **Play Billing Integration**: Connected `PlayBillingManager` to the VIP Upgrade Modal dialog to execute live Google Play Store subscription purchase flows (`nsecvpn_premium_monthly` and `nsecvpn_premium_yearly`).
- **JWT Token Auto-Refresh**: Implemented seamless HTTP 401 Unauthorized handling in `VpnRepository` to trigger `api.refreshToken` and retry requests without user intervention or authentication loops.
- **Monotonic Connection Timer**: Migrated connection timer calculations in `WireGuardTunnelManager` to `SystemClock.elapsedRealtime()`, preventing device clock manipulation from altering stats or timer displays.
- **Comprehensive Audit Test Suite**: Added `AuditFixesTest.kt` unit test suite covering credential encryption, token refresh, disclosure acceptance, and ping metric accuracy.

### Changed / Refactored
- **R8 Minification & Resource Shrinking**: Enabled `isMinifyEnabled = true` and `isShrinkResources = true` in `app/build.gradle.kts` release build type with updated ProGuard rules for WireGuard, Retrofit, and Moshi.
- **Honest Latency Metrics**: Refactored ping testing in `VpnRepository` to return real socket latency or `null` on unreachable gateways, eliminating fake random ping generation.
- **Gated Interstitial Ads**: Enforced strict `isUserInitiatedConnect` flag validation so auto-connect at launch or QS Tile actions never present full-screen interstitial ads.

### Fixed
- **Split Tunneling Package Safety**: Added `PackageManager` validation to verify disallowed packages exist on device before passing to `VpnService.Builder`, preventing package resolution crashes.

## [1.2.0] - 2026-10-11

### Added
- **Smart Native Ads Integration**: Built `NativeAdManager` and reusable `SmartNativeAd` Compose component supporting compact, medium, and large layouts with shimmer/loading states, custom XML layout templates (`native_ad_compact.xml`, `native_ad_medium.xml`, `native_ad_large.xml`), and test ad fallbacks across `HomeScreen`, `ServerListScreen`, and `SpeedTestScreen`.
- **AdMob Traffic Bypass / Split Tunneling**: Implemented automatic package and domain exclusion rules in `WireGuardTunnelManager` and `NsecVpnService` to route Google Mobile Ads SDK traffic outside the VPN tunnel, preventing ad request timeouts and policy flags.
- **Android 16 KB Page Alignment**: Enabled legacy JNI packaging (`useLegacyPackaging = true`) in `app/build.gradle.kts` to satisfy Android 15/16 16 KB ELF alignment requirements for `libwg-go.so`.
- **Automated Native Ad & Split Tunneling Tests**: Added `SmartNativeAdTest.kt` for native ad component lifecycle and layout verification, and `SplitTunnelingTest.kt` for package/domain bypass validation.

### Changed / Refactored
- **Ad Layout Modernization**: Replaced legacy static `AdBannerView` banners across `HomeScreen`, `ServerListScreen`, and `SpeedTestScreen` with dynamic `SmartNativeAd` views.

### Fixed
- **SmartNativeAd NullPointerException**: Fixed null safety during `AndroidView` initialization in `SmartNativeAd` when layout inflation occurs off the main thread or prior to view attachment.
- **Emulator AdMob Stability**: Configured `@xml/gma_ad_services_config.xml` and manifest surface sync properties to resolve AdMob binder crashes on emulator instances.

## [1.1.0] - 2026-10-10

### Added
- **Dark Mode Integration**: Full dark mode support across Android client with dynamic System/Light/Dark switching via Settings.
- **Theme-Aware Badges & Cards**: Dynamic M3 color scheme mapping for server cluster node badges, status alerts, and contrast optimization.
- **Android CI Workflow**: Comprehensive GitHub Actions CI job for Android (`testDebugUnitTest` and `assembleDebug`) with JDK 17.
- **Executable Gradle Wrapper**: Added executable `gradlew` and `gradlew.bat` in repository root for standardized CI and local builds.

### Fixed
- **WireGuard UAPIOpen Permission Denied (`/data/data/com.wireguard.android`)**: Resolved by establishing the TUN interface via Android's `VpnService.Builder` in `NsecVpnService` with custom `NsecVpnBackend`, bypassing the broken hardcoded UAPIOpen directory path in `libwg-go.so`.
- **AdMob AdServices Binder Crash**: Added `@xml/gma_ad_services_config.xml` and manifest properties to disable unneeded measurement service bindings on emulators.
- **SurfaceSyncGroup / AdActivity Synchronization**: Configured hardware acceleration and window flags for `com.google.android.gms.ads.AdActivity`.
- **GitHub Dependabot Security Vulnerabilities**: Added package overrides and lockfile remediation for High-severity transitive dependency alerts (`braces`).
- **CI Test Teardown / Hanging**: Configured `--detectOpenHandles --forceExit` and test environment flags for Jest test runs in GitHub Actions.
- **Repository Hygiene (.gitignore)**: Added `node_modules/`, `dist/`, `.build-outputs/`, and logs to `.gitignore` to prevent repository bloat and GitHub push rejections.

## [1.0.0] - 2026-10-09

### Added
- **Backend Architecture**: Production-grade Node.js/TypeScript backend with PostgreSQL and Prisma ORM.
- **WireGuard Node Agent**: Linux daemon supporting parameterized `wg` management with HMAC-SHA256 signatures and replay defense.
- **Web Admin Console**: Complete React 18 / Tailwind administration portal with live node health checks, device management, and audit logs.
- **Google Play Billing Verification**: Integrated subscription state verification supporting ACTIVE, IN_GRACE_PERIOD, ON_HOLD, PAUSED, CANCELLED, and EXPIRED states.
- **Dynamic IP Pool Management**: Concurrency-safe, retry-backed IPv4 host allocation supporting arbitrary server subnets.
- **Audit Logging**: Immutable system event logging for administrative operations.
- **Automated CI/CD**: GitHub Actions workflow running tests and builds across backend, agent, and admin panel.

### Security & Hardening
- **IDOR Defense**: Enforced strict device ownership verification on peer session terminations (`/vpn/disconnect`).
- **Token Reuse Defense**: Automatic revocation of all active device sessions upon detection of revoked refresh token reuse (RFC 6749).
- **Zero-Logs Guarantee**: Strict removal of any IP address logging or personal identifier storage.
- **Parameterized Execution**: Complete elimination of shell injection risks in the WireGuard node agent.
- **Non-Root Containers**: Docker images configured with non-root runtime users.

### Fixed
- **Compilation Failure**: Fixed invalid long literal `1000L` in `src/services/billing.ts`.
- **Missing TypeScript Config**: Added `tsconfig.json` for the admin panel.
- **Peer Persistence**: Configured `SaveConfig = true` and `wg-quick save` to preserve dynamic peers across server reboots.
- **Server Load Calculation**: Implemented dynamic load percentage updates on peer connections and disconnections.
update

### Audit follow-up delivery
- Document exact migration/image approval, provider configuration, compatibility and runtime verification limits.
- Add an isolated Git publisher and complete Android Studio transport package for the audit follow-up branch.

- Fix Android billing setup indentation reported by PR #17 lint.

### Remove owner-requested AdMob consent UI
- Remove UMP forms, settings callbacks and direct UMP dependency without changing ad unit IDs.
- Initialize the SDK off the UI thread, preserve readiness gating and use non-personalized ad requests; retain backend-verified rewards and remote ad controls.

### Disconnect navigation and rendering
- Retain the Home layout/state behind connection details without hidden ads or pulse work; return on the tap rather than waiting for teardown navigation.
- Show Disconnecting accurately, disable reconnect until DOWN and remove delayed connect-control colour feedback.
- Reject stale interstitial navigation after disconnect and defer native load work past the first rendered frame.
- Add Compose retention/disabled-control and connection-state presentation regression tests.

### Disconnect update delivery
- Add a guarded separate-checkout publisher and document video evidence, SDK/privacy trade-offs, runtime limits and phone test steps.
- Add SDK initialization idempotence regression coverage.

- Align the EU flag with Made in Europe on the native splash; preserve logo size and theme colours.

### Reward-offer startup
- Bootstrap validated remote-config cache off Main before Home, capped at 250ms without waiting for the live API.
- Separate real reward duration/enablement from SDK readiness to prevent the offer changing during initialization.
- Replace uncached startup marketing/loading text with a compact static skeleton; show a clear failure state.
- Keep local ad-free reward status visible before SDK initialization; add cache, offer and UI regression coverage.
- Isolate the Robolectric boot-count fixture for the pre-initialization ad-free reward regression.

### Reward startup delivery
- Package the startup and splash alignment corrections with an isolated Git publisher, runtime verification limits and a phone checklist.

- Refresh reward-startup transport against merged splash PR #19; use a separate final checkout and v2 updater to avoid stale files.


## UI audit baseline
See [UI audit](docs/audit/UI-AUDIT.md) for the source-only findings and explicit device measurement limits. UI-only repair work uses `fix/ui-smoothness-audit`; no database or VPN changes are planned.


### UI state and rendering
Live tunnel statistics are isolated to connection details; tab state is saved, safe insets are consumed once, background animations/native-ad refreshes stop, and connect taps have visible feedback. VPN operations and ad IDs are unchanged.


### Responsive UI and honest app lists
Server controls and skeletons now share one lazy list with saved search/filter state, empty results and 48dp filters. Location badges and speed metrics wrap; recovery forms accommodate the keyboard. Split tunneling displays only real installed apps, with loading/retry/empty states. Server requests have bounded UI waiting.


### DNS presentation and splash branding
The DNS picker uses selectable provider cards, active resolver status and inline custom validation. Saves report automatic application without a manual reconnect instruction. Existing tunnel rebuilding is unchanged. The EU flag follows the splash text at the same size and baseline. No new dependencies, environment variables or migrations.


### Status accessibility
Small success/warning labels use theme-aware foreground colors while preserving brand fills. Device visual checks at large font sizes remain required.


### UI regression and profiling checks
New Compose regressions cover scoped live stats, saved server search, DNS selection/error/status and honest app choices. A device test covers connect/disconnect presentation. The privacy-limited `scripts/profile-ui.py` records actual frame deadlines/startup/memory; parser tests run in CI. See [UI verification](docs/audit/UI-VERIFY.md) for low-end, large-font and day/night checks. Android checks remain pending where no SDK/device is available.


### UI repair publication
The UI-only repair can be imported with `scripts/publish-ui-audit.py` and its accompanying reviewed Git bundle. Publishing uses a new branch, preserves the original checkout and requires unchanged audited main. See [PR summary](docs/audit/UI-AUDIT-PR.md); no deployment is performed.


### Server and speed-test alignment
Server names and badges share a baseline and wrap without leading spacer items. Speed metrics use balanced two-by-two phone rows, four columns on wide screens, and one column for large text. The speed-test subtitle is centered when it wraps. No VPN or measurement logic changed.


### Settings and recovery alignment
App Version is inside Developer / Debug and remains visible in release; debug diagnostics remain gated. Account recovery has its own section. Settings and the Device VIP Recovery sheet share a wrapping Device ID card with Copy/Copied feedback. Only the installation ID is copied. Home text actions are vertically centered in their touch targets.


### In-place theme switching
MainActivity handles `uiMode` configuration changes so Compose updates the palette without activity recreation. Existing native splash preference synchronization remains intact. System-bar appearance updates only when theme/view changes, and native ad XML views refresh for new night-mode resources. Routing, data models, consent and ad IDs are unchanged; confirm real transition timing on the phone.


### VIP sheet and verified reward feedback
The VIP options sheet is compact with a close action and balanced plan rows. Pending VIP now comes from an actual reward ticket, not ad-free time. Claims check verified grants more promptly, serialize requests, bound waiting, retry while foregrounded without the prior five-minute throttle, and reject expired/free responses rather than showing false activation. Both benefits start from a confirmed VIP grant. This requested reward follow-up changes only Android reward coordination; backend signature verification remains mandatory and unchanged. Live SSV delivery/configuration still requires verification; debug test-unit rewards are not production VIP proof.


### Server refresh feedback
The server refresh icon rotates during the actual catalog refresh, disables duplicate taps and announces its loading state. Rotation stops in the background and when the request completes. Lazy latency probing retains its existing behavior. Reward request timeouts retain pending tickets and no longer cancel the foreground retry loop.


### Alignment and reward regressions
Tests cover inline server badges, balanced metrics, large fonts, recovery ID clipboard content, Settings ordering, duplicate refresh taps, verified/pending/expired reward responses and the retry-throttle regression. A manifest check keeps in-place theme handling limited to uiMode. Android runtime checks require CI/Android Studio.


### Alignment follow-up publication
See [alignment and VIP follow-up](docs/audit/UI-ALIGNMENT-UPDATE.md) for evidence, scope, AdMob SSV setup and phone checks. The accompanying Git bundle and `scripts/publish-alignment-vip.py` import a reviewed branch into a separate checkout. No backend deployment or migration is performed.

## VIP Access And Home Banner Update

VIP-only server selection and connection now offer Purchase VIP Plan / Watch An Ad before creating a tunnel. Settings keeps Device ID last. Server filters occupy equal widths, and speed-test Ping, Jitter and measured Peak occupy a single row. Home announcements appear as centered dismissible banners, preserve dismissal for unchanged content and refresh every five seconds while Home is visible. See [the rollout notes](docs/audit/VIP-HOME-UPDATE.md). No backend API, schema or migration changes.

UI follow-up: centered live Home announcement with X; full-width equal server filters; single-row Ping/Jitter/Peak speed metrics; Device ID remains the last Settings item. Regression coverage includes large fonts, dismissal/content updates and premium selection.

Publication: `scripts/publish-vip-home.py` imports reviewed commits into an isolated `Nsec-VPN-vip-home-repair` checkout and pushes only the repair branch with an exact-main guard. Source project edits, main, deployment and credentials are preserved.

CI correction: allow one physical pixel of Compose rounding in equal-width metric tests and explicitly opt into the coroutine virtual-time API. Runtime UI remains unchanged.

## Popup And Live Server Status Polish

VIP access prompts now use a compact card with an X, a prominent rewarded-ad action, a separate purchase action and the actual reward availability reason. Centered Home announcements use the same surface, spacing and close treatment in both themes. Device VIP Recovery adds a pinned X, preserving the existing in-flight-operation dismissal guard. Servers checks live metadata every five seconds while visible, without toggling foreground loaders. No backend/API/schema changes or migrations. See [rollout notes](docs/audit/POPUPS-LIVE-SERVERS.md).

Publication: `scripts/publish-popups-live.py` imports this review into an isolated `Nsec-VPN-popup-live-repair` checkout, preserving existing edits and publishing only its new branch after checking the main revision.

## Backend security and administration audit (2026-10-14)

Added password-reauthenticated, encrypted authenticator enrollment and one-use TOTP counters; MFA session version checks, serialized refresh rotation/replay epoch revocation, fail-closed bootstrap, bounded graceful shutdown, safe JSON/body errors and guarded concurrent config editing. Blocked devices now use tracked current/pending-key cleanup. One nullable AdminUser.totpLastStep migration and stable ADMIN_TOTP_ENCRYPTION_KEY are required before rollout. See docs/audit/BACKEND-ADMIN-ROLLOUT.md and BACKEND-API-CHANGES.md, including MFA rollback limitations. Android/agent source is unchanged.

Promo deletion now frees the code for a new campaign while preserving historical redemptions and existing grants. Billing inputs/quota are bounded and temporary SSV provider/storage failures return retryable 503. Added route regressions and isolated PostgreSQL contention/replay tests for CI.

Public server metadata uses a two-second, invalidated single-flight catalog without sharing per-device authorization. Config reads use generation-safe coalescing; health probes have a DNS deadline and independent bounded workers; expired-peer cleanup rotates its cursor and anonymous aggregates use serialized UTC dates. Production builds clean dist and omit test modules. Query-count and timing-deadline regressions added; live latency remains unmeasured.

Reskinned the admin workspace with responsive grouped navigation and separate Ads & Rewards, App Controls, Announcements and Account Security tabs. Scoped validated config publishing detects stale saves. Added local-only authenticator QR generation (qrcode and @types/qrcode), theme/focus/empty/error states, and self-only CSP/no-framing headers. Existing role guards and management actions are retained.
