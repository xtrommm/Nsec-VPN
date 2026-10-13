# UI repair verification

Use the supplied Android Studio project or import the bundle with the publisher. Original local edits remain in the old checkout. This is an Android UI update only; do not redeploy backend/admin/agent. No migration, env variable, signing change, ad ID change or runtime dependency is required.

## Build checks

In Android Studio, open the new project root, choose the bundled JDK 21 for Gradle, and run Sync Project with Gradle Files. Install SDK components requested by the existing build. In the terminal:

```powershell
.\gradlew.bat :app:testDebugUnitTest :speedtest-helper:testDebugUnitTest :app:lintDebug :app:lintRelease :speedtest-helper:lintDebug :speedtest-helper:lintRelease :app:assembleDebug :speedtest-helper:assembleDebug --no-daemon --warning-mode all
```

CI supplies signing configuration for its builds. Keep your local production signing secrets outside the repository; do not replace signing configurations to make a check pass. Run `UiSmoothnessRegressionTest`, `HomeConnectionHostTest`, `ConnectedScreenUiTest`, `VipRewardCardUiTest`, and `SettingsIdentityRequestTest`. With a connected test phone/emulator, run `:app:connectedDebugAndroidTest` (or ConnectionUiDeviceTest in Android Studio). These presentation tests do not establish a real handshake or internet access.

## Manual phone checklist

1. Cold start, warm start and reopen: check both Light and Dark. Confirm existing logo size, text then EU flag, no additional splash page or white dark-mode flash. Existing native platform launch limitations still apply.
2. Home: cached real reward offer remains stable; native ad stays below content; tap feedback is visible. Announcements stay dismissed through tab switches and reset for changed announcement text.
3. Connect: one tap shows Connecting, repeat taps cannot start another operation. After actual handshake, connection details show the actual connected node. Disconnect returns Home immediately, shows Disconnecting until tunnel teardown finishes, and then enables Connect. Test Back and the bottom tabs during each state.
4. Servers: scroll a long real list, search, choose a filter, switch to Settings and back. Search/filter/scroll should remain. Empty searches show an explanation. Search/filter controls should not jump when skeletons disappear. Ads should not appear during Connecting/Disconnecting. Favorite target is usable without selecting the server.
5. DNS: choose Automatic/Cloudflare/Google/Quad9/AdGuard and Custom. Automatic explains node defaults, Custom shows validated address input. Try invalid or IPv6 resolver on this IPv4-only setup: show an error and retain input. Connected saves say applying automatically, never ask to reconnect manually. Existing tunnel manager does briefly rebuild the tunnel. Wait for a verified connection before expecting Active DNS. Use an INCLUDED browser to run a DNS leak test; this app is excluded from the tunnel.
6. Settings/recovery/request: rotate, use keyboard in portrait/landscape, check large font and display scale, dismiss sheets and submit invalid fields. Server request waiting is bounded; a timeout can mean the server already received the request, so inspect before retrying. PINs remain unsaved/private. Check support copy/open and unavailable email/browser/settings intents.
7. Split tunnel: real launcher apps only, empty state on a test device without other apps, retry on lookup failure. Existing routing policy is untouched.
8. Speed Test: start only after connection, cancel, disconnect during the test, switch tabs, check error feedback and wrapping metrics. Existing speed helper is still required and unchanged; install its matching debug build only if needed. No browser or history is added.
9. Troubleshooting, legal and diagnostics: confirm single safe-area inset, readable scrolling, correct Back. Diagnostics remains debug-only. Troubleshooting reports observations, not invented leak proofs.
10. Background/resume, rotate, process recreation: tab/search/scroll survive where saveable; active animations and native-ad refreshes stop in background. In-flight speed test cancels when the screen is removed. Validate real purchase/reward/promo flows separately; their security and backend logic were not changed.

Repeat at 320dp width, landscape, 200% font scale, a tablet/foldable profile, gesture navigation and three-button navigation. Use a low-end AVD (2 CPU cores, 2 GB RAM, hardware graphics), then your phone at normal and high refresh rate. Emulator results do not establish physical-device battery or 120fps performance.

## Real performance capture

Use the SAME device, refresh rate, theme, dataset, ad availability, thermal state and action sequence before/after. Enable USB debugging and add Android SDK platform-tools to PATH. Capture the baseline main build before replacing it if before/after numbers are required. Avoid testing on a debug build with the debugger attached when judging release performance.

```powershell
py scripts\profile-ui.py --label before-light --cold-start
py scripts\profile-ui.py --label after-light --cold-start
py scripts\profile-ui.py --label before-dark --cold-start
py scripts\profile-ui.py --label after-dark --cold-start
```

The script explicitly asks you to disconnect VPN before its optional force-stop. Without `--cold-start` it reuses the existing process. After a janky interaction, press Enter immediately: gfxinfo is a recent-frame buffer, not a full session. Repeat each scenario three times. Capture separate runs for server scroll, tab navigation and disconnect. `--apk PATH` records exact built APK bytes; `--serial SERIAL` selects a device.

The JSON gives real sampled frame P50/P95/P99, deadline misses where Android supplies deadlines, startup command timings, and process PSS. It never converts frame latency into invented presented FPS and never assumes 60Hz on a 120Hz device. Missing data remains null. No logcat, keys, traffic content, client IPs or full device logs are collected. Share JSON summaries, not private diagnostic dumps.

For recompositions, Android Studio Layout Inspector → select the app → enable recomposition counts → reset counts → run identical actions. Check that Home/root do not redraw for connected statistics when details are not visible. For scheduling/GPU causes, Android Studio Profiler System Trace or Perfetto should capture the same action sequence. Record counts and timings in the audit report; currently no before/after runtime numbers exist.

## Publishing

The GitHub integration returned 403 for writes. The publisher pushes only the repair branch using YOUR authenticated Git, then creates a draft PR if GitHub CLI is signed in, otherwise prints the exact Create pull request link. It never merges/deploys. Complete CI and device checks before merging.
