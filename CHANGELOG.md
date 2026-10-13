# Changelog

## Unreleased — Deep audit



- Protect entitlement expiry against concurrent grants/recovery, preserve longer VIP on purchase verification, and handle profile database failures without unhandled async rejections.

- Commit durable peer reservations before agent mutation; track uncertain keys, compensate only under locks, reconcile abandoned reservations, and rotate disconnect handles on renewal. Add the nullable pending-public-key migration and regression/integration tests.

- Require signed AdMob verification before rewarded VIP; make claims read-only, guard ticket ownership/event time, prevent replay/expiry extension, and poll pending verification without delaying local ad-free time.

- Record the current main commit, security/core/admin/performance findings, dependency/history scan scope, and device/operator verification limits before source fixes.

## Unreleased — Device VIP recovery and native splash

- Add authenticated recovery-PIN setup and Device ID + PIN restoration of existing VIP/promo ownership without extending expiry.
- Add persistent failed-proof locks, credential rotation, auth-epoch enforcement for access/refresh tokens, and safe old-peer lease cleanup.
- Add a Settings recovery sheet and update the displayed identity after recovery.
- Move Europe/EU branding onto the original native day/night launch window and remove the extra Compose splash and delay.
- Rename Accept & Continue to Continue, retaining disclosure and Android VPN permission.
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
