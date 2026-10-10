# Changelog

All notable changes to the Nsec VPN project are documented in this file following the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) standard.

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
