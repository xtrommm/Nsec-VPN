# Android experience update — phase 2

This update follows `docs/EXPERIENCE-PHASE1.md`. Apply phase 1 first: its backend provides server requests and support email. Do not publish before Android CI and the phone checklist below pass. Source changes do not prove real-device behavior.

## What changed

| Item | Actual implementation | Verification still needed |
| --- | --- | --- |
| 1. Navigation | One MainActivity Scaffold now keeps the footer on Connection Status. Opening status selects Home, tab taps leave status, Back returns to Home, navigation survives activity recreation. | Connect/disconnect/reconnect, tab highlighting, Back and rotation on phone. |
| 2. Support / requests | Settings opens configured `support.email` via mailto, offers copy, validates country/city/note, submits the real device-authenticated API, reports failures and a persisted 10-minute cooldown. Backend assigns installation identity, registered app version, timestamps and Pending status. | Submit then find exactly that request in admin; test 429 and offline errors. |
| 3. DNS | Automatic uses node DNS; Cloudflare, Google, Quad9, AdGuard and Custom persist. Numeric public IPv4/IPv6 syntax is validated. Changing DNS cancels/rebuilds an active tunnel and waits for a new handshake. Active DNS comes from the applied configuration. | DNS resolution/leak tests and reconnect on phone. |
| 4. Device ID | Settings copies the same KeyStoreManager installation UUID used in device registration. No new competing ID is generated. | Compare copied ID against admin exact lookup after restart. |
| 5. Speed | No browser launch. A separate, signature-protected helper UID makes VPN-bound HTTPS requests; the main app owns the gauge, cancel control and five-result local history. | Two-APK install, routing, measurements, cancellation and loss tests. |
| 6. Loading | A validated real server catalog (24-hour TTL) restores before remote requests; successful refresh replaces it. Splash prefetch, de-duplicated refresh, asynchronous entitlement updates and lazy bounded pings remove the old sequential work. Skeleton/error/cached states are explicit. | Measure cold/warm perceived loading; <500 ms is a target, not a measured claim. |
| 7. Home | Removed the duplicate node/location/IP text above the logo. Connected screen remains the authoritative public-IP display. | Visual check in both themes. |
| Troubleshooting | Actual Android internet capability, bounded HTTPS API call, recent handshake and byte-counter observations. DNS/path MTU remain unverified until measured; no fixed delay followed by fake passes. | Run disconnected, with API offline, and with a connected tunnel. |

Audit references before fixes: `docs/EXPERIENCE-AUDIT.md` records the original main SHA and file/line evidence. Cache delay came from awaiting entitlement and ping work before catalog publication, empty startup state, and duplicate list refreshes. A first install without a cache still needs real network data.

## DNS limitations and leak checks

The current node is IPv4-only. Valid IPv6 DNS input is recognized but cannot be selected: the UI explains that real IPv6 tunnel support is required. No IPv6 default route or pretend IPv6 resolver is pushed. Private DNS resolvers such as 10.8.0.1 are rejected because the current node does not host one.

Changing DNS reconnects the tunnel. Android Always-on VPN with “Block connections without VPN” is needed to prevent ordinary network access during teardown/reconnect; this app cannot silently enable system lockdown. Android Private DNS and another app's own DoH can supersede the configured resolver. DoH may still travel inside WireGuard while using a different resolver. Configuration display alone cannot prove resolver identity or absence of leaks.

On the phone, set Android Private DNS to Off temporarily and disable Chrome secure DNS for the diagnostic. Connect, open a browser included in the VPN, load a DNS leak test and https://ifconfig.me. Expect the node public IP, the chosen resolver's operator and no ISP DNS or public IPv6 result. Re-enable your chosen Private DNS policy afterward; explain differing resolver identity rather than claiming the app overrides it.

## Speed helper design

Package `com.nsec.vpn.speedtest` runs a bound service. Main package `com.nsec.vpn` stays excluded, including ads/API. The helper checks the caller UID and matching signatures, captures an Android network with TRANSPORT_VPN, binds every HTTPS request to that Network, and aborts if it disappears. It never retries through the physical network. Before measuring, a VPN-bound Cloudflare trace must match the connected node's backend-provided public IP; an unexpected observed IP is neither stored nor logged.

Cloudflare publicly documents GET `https://speed.cloudflare.com/__down` and POST `https://speed.cloudflare.com/__up` in https://github.com/cloudflare/speedtest/blob/main/README.md. We call them directly; we do not embed its browser SDK or send a results beacon. Cloudflare necessarily sees the node exit IP and may retain its own service records. Consent appears before each test. Provider outages/rate limits show errors, not invented numbers.

“Ping” is warm HTTPS round-trip median, not ICMP latency. Jitter is mean absolute successive latency difference. Download uses received payload bytes; upload uses successfully acknowledged payloads. Tests are sequential HTTP measurements including request overhead, so results may be lower than a multi-stream benchmark. Eight-second download/upload stages, 60-second worker deadline and 65-second app timeout bound work. Cancellation closes connections. Payload caps are 128 MiB down / 64 MiB up, plus protocol overhead. Only completed verified results enter local history; no browsing or client-IP history is collected.

### Install in Android Studio

1. Apply the ZIP updater in Windows PowerShell to your existing checkout. Publish its branch and open a PR; wait for CI.
2. Open the same checkout in Android Studio and select **Sync Project with Gradle Files**. Use its bundled JDK 21. Install SDK 37 / the build-tools requested by Gradle.
3. Connect one phone, enable USB debugging and accept its authorization dialog.
4. In Android Studio Terminal (PowerShell), run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug.ps1
   ```

   This builds and installs BOTH APKs. Running only the app module does not install the helper.
5. Connect Nsec VPN and open Speed Test. A missing helper or mismatched signing certificate produces a clear error. Keep the helper included in the VPN.

No production dependencies were added. The new module uses Android framework networking and the existing JUnit dependency. Existing main-app signing configuration and ad IDs are untouched. Both APKs must use the same certificate; release uses the existing KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD configuration, never a new key. Existing production installs must retain their original certificate. A Play release requires distributing the companion package with matching signing certificates; do not ship only the main APK and advertise speed tests as ready. Never uninstall the main app merely to bypass a signing mismatch: that loses installation identity.

## API, migration and deployment

Phase 2 adds no database migration or secret/env variable. App calls the real phase-1 POST `/api/v1/server-requests` with country, city and optional note; device identity/status/time are server-owned. 429 honors Retry-After. Support email comes from phase-1 public config. Existing peer/server/reward APIs remain unchanged.

Deploy the phase-1 backend migration/key preparation before installing the new app. Existing promo minute grants are preserved exactly through the phase-1 migration; new promo durations are whole days. See phase-1 docs for rollback limits, encryption key handling and exact-SHA schema approval. Do not rerun or invent destructive SQL for this app update.

## Build status and complete testing checklist

Local helper Java compilation against Android 37 passed with `-Xlint:all -Werror`; four JUnit measurement tests passed. New Android regressions cover cache expiry/corruption/duplicates, DNS policy/persistence, stable identity, request failure/cooldown and troubleshooting evidence. They have NOT run here: the full Gradle attempt failed before configuration because this workspace's Java networking cannot download Gradle. No Android lint, APK compilation, device instrumentation or speed-provider routing pass is claimed. CI now includes both APKs' tests, debug/release lint and builds. PowerShell installation has not been exercised on Windows here.

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :speedtest-helper:testDebugUnitTest :app:lintDebug :app:lintRelease :speedtest-helper:lintDebug :speedtest-helper:lintRelease :app:assembleDebug :speedtest-helper:assembleDebug
```

1. Footer: connect/reconnect/disconnect, tap all four tabs, Back, rotate, check selected tab and screen padding.
2. Support/request: mail launch or visible missing-mail-app feedback, both copy actions, invalid fields, API outage, submit once, immediate 429, matching request/device/version/status in admin.
3. DNS: each provider + custom numeric input, invalid/private/documentation/IPV6-only errors, restart persistence, active-tunnel reconnect, DNS/leak checks above; verify no false Connected state during reconnect.
4. ID: compare copied UUID with backend/admin; restart/update keeps it; reinstall creates a new identity by design.
5. Speed: missing helper and wrong certificate fail; both installed succeed; no browser opens; gauge/ping/jitter/down/up/history; Cancel, leave page, disconnect, revoke/kill node and network switch stop measurements without an ISP result. On the node, `watch -n1 'wg show wg0'` must show rising transfer during a test. Test a provider outage/rate limit. Airplane-mode failure must not save history.
6. Loading: real API catalog then force-stop/reopen with network unavailable: show only that cached catalog with error feedback; clean install offline shows no invented nodes. Pings must not delay rows. Measure warm first-frame latency on the phone instead of asserting 500 ms from code.
7. Home: no duplicate node/IP header above logo; Connected screen still displays the actual failover node IP.
8. Admin (phase 1): CRUD/promos/day migration/usage/copy/bulk/search/pagination/roles; Remote Config/global kill/per-placement ads/units/frequency/public update; server requests/status/delete/device lookup; server ordering/health/dashboard/audits; API ownership/cooldown tests. Complete phase-1 database integration and deployment checklist.
9. Troubleshooting: disconnected does not claim UDP/DNS/MTU passed; an unreachable API reports failure; only a recent real handshake passes that observation. No private key or normal client IP appears.

**Publishing decision: NO-GO until CI, migration/deployment preparation and the phone checklist pass.** No new monitoring/Play Integrity/extra product feature is enabled without your decision.
