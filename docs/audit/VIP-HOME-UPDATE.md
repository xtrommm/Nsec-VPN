# VIP Access And Home Banner Update

Base: main `0cdcba3` (merged PR #22). Branch: `fix/vip-gating-home-banner`.

## Findings and changes

- `MainActivity.kt:startConnectInternal` and the VPN permission result previously called the tunnel without checking the selected premium node. The server-list card did check access, so previously selected premium nodes could still fail at peer creation. Both connection entry points now check current entitlement expiry and prompt Purchase VIP Plan / Watch An Ad. `ServerListScreen` uses the same gate. Backend authorization remains authoritative.
- `SettingsScreen.kt` rendered DeviceIdCard above DEVELOPER / DEBUG. It now renders after App Version and debug diagnostics, as the final Settings item. Recovery's own copy button stays available.
- `ServerListScreen.kt` used a horizontally scrolling row of intrinsic-width filters. Four equally weighted, full-width cells now cover the row without trailing space; text can wrap for accessibility.
- `SpeedTestMetrics.kt` previously displayed four metrics across two rows. It now displays Ping, Jitter and Peak in one equal-width row. `SpeedTestScreen.kt` records the highest actual speed sample in the current test; no invented values or history are introduced.
- `HomeScreen.kt` previously put the announcement above Home controls and used DISMISS. `HomeAnnouncement.kt` presents a centered banner dialog with an accessible X, back/outside dismissal and scrollable long content. It displays only on Home, including when another connection surface remains composed but hidden. Identical config refreshes preserve dismissal; changed title/body reopens it. Live config refresh runs every five seconds on visible Home, every thirty seconds on other screens, and stops when backgrounded.

## Rewards: actual evidence and limits

`RemoteConfigManager.kt:apply` deliberately selects Google's test rewarded unit in debug builds. `AdMobManager.kt:showRewarded` passes the device identity and ticket as SSV user_id/custom_data. `backend/src/services/rewardVerification.ts:verifyReward` requires a valid Google signature and the configured live ad unit. `rewardClaims.ts:grantVerifiedReward` updates VIP inside the verified callback transaction. A debug SDK completion is not proof of a production reward.

The app now polls at 500 ms during the first confirmation attempt, then one claim request per two seconds while foregrounded and pending (below the existing 60/min claim limit). A successful verified response applies VIP and clears pending immediately. An unconfirmed response gives specific test-ad or delayed-confirmation feedback instead of keeping a generic Confirming label indefinitely. No client callback, timer or HTTP 202 grants VIP.

A maximum 1–2 seconds after *watching* cannot be promised: AdMob delivers the signed callback asynchronously. Once the callback transaction has committed, the next poll observes the grant, subject to network latency. No database/API changes or backend deployment are required by this Android update.

### Required owner setup for real rewarded VIP

1. In AdMob, open the production rewarded ad unit and set its server-side verification callback URL to `https://api.nsecvpn.com/api/v1/billing/rewarded/ssv`.
2. Match that rewarded unit in Admin Remote Config & Ads; enable rewarded ads and the configured pass duration.
3. Allow that exact GET callback route through Cloudflare without an interactive browser challenge. Keep backend Google-signature validation enabled.
4. Test a release build with the actual configured unit and a registered test device. Android Studio's normal debug build uses the Google sample unit and cannot unlock live VIP through this path. Do not repeatedly click live ads to test monetization.
5. If a release reward remains pending, inspect callback status counts without copying signed query strings, tokens, device identities or credentials into chat. Missing callback delivery/ad-unit configuration must be corrected in AdMob/proxy, not bypassed in the app.

## Validation

38 local Python tests passed: Android configuration (12), profiling parser (3), deployment (23). Changed Kotlin files passed a syntax parse and `git diff --check`. Added/updated Robolectric tests cover VIP gating/actions, equal full-row filters, single-row speed metrics at large fonts, Device ID ordering, live announcement replacement, and pending reward feedback without a grant.

Android Gradle execution was attempted but failed before compilation because the environment cannot download Gradle 9.8.1 (`Network is unreachable`). Android compilation, lint, Robolectric and APK checks therefore remain for GitHub CI; do not merge until green. No FPS/device measurements are claimed.

## Manual checklist

- FREE account: tap a VIP node; see both actions and no failed tunnel. Select VIP while entitled, let entitlement expire, and tap Connect again; see the same prompt. FREE nodes still connect normally.
- Settings: scroll to the end; Device ID with Copy is last in debug and release.
- Server filters: all four fill the row; test 200% font size and both themes.
- Speed test: Ping, Jitter, Peak share one row, N/A until actual measurements, Peak resets each run. Helper/transport remains unchanged.
- Admin announcement: publish while Home stays open. Within the next successful five-second refresh, a centered popup appears. X dismisses it; unchanged refresh does not reopen it; edit content and it reappears. Disable it and it closes. It does not overlay Servers/Settings/Connected.
- Release rewarded ad: signed callback grants VIP; claim response updates both VIP servers and ad-free access. Debug test ad: explicit diagnostic, no counterfeit grant. Stop internet during confirmation, restore it, and confirm safe retry.
