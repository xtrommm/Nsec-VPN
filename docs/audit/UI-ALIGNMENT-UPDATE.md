# Alignment, theme and verified VIP follow-up

Based on merged main 3b9fe2b513d65a5ebf82f75b47398ebc567121ce (PR #21), on new branch fix/ui-alignment-settings. The four supplied screenshots were opened and inspected locally.

## Findings and changes

- FASTEST was in a separate flow row containing standalone leading Spacer children. Names and badges now share a baseline; wrapped badges start at the same content edge. Cluster/VIP badges retain their functionality and favorite targets retain 48dp size.
- Speed metrics allowed three columns plus an isolated fourth. The panel now chooses only one, two or four columns: two-by-two on phones, four across on wide screens, one for large text/narrow screens. Subtitle wraps with centered alignment; no speed traffic logic changed.
- App Version preceded Developer / Debug. It is now inside that section and remains present in release; diagnostics stays debug-only. Recovery has its own section.
- Device VIP Recovery showed the public installation ID without a copy action. A shared ID card now provides Copy/Copied feedback in Settings and the recovery sheet. PINs are not copied, logged or saved by this component. Clipboard policy failures show an error.
- Home CHANGE/DISMISS text occupied tall touch targets but sat toward their top. Standard centered text buttons retain the existing wording and adequate targets.
- Theme saving calls application night mode while MainActivity did not handle uiMode configuration changes. The activity now handles only uiMode in place; rotation and other configuration recreation remain as before. Compose palette changes retain screen state; native splash persistence is unchanged. System-bar effects are keyed to theme/view, and native ad XML reinflates for changed night resources. Phone profiling must confirm the actual remaining transition duration.
- Refresh had no animation or disabled state. Its icon now rotates during actual catalog loading, disables duplicate taps and announces loading; the lifecycle gate stops background rotation. Lazy ping probing remains separate and unchanged.
- The old pending VIP UI was derived from any local ad-free expiry rather than an actual pending ticket. It now uses actual pending reward state. The VIP sheet has a compact header/close action, reward card and balanced plan rows.
- Reward claims checked every three seconds and retries were throttled for five minutes, including when startup had no pending ticket. The client now checks at one second initially then two seconds, serializes requests, bounds each verification wait to 35 seconds, and retries pending tickets while foregrounded with a 15-second throttle. Verified entitlement is applied immediately on the first successful response.
- A successful response containing FREE or an expired pass could still produce a VIP Activated toast. Such responses now clear the old ticket and show expiry feedback instead of a false activation.
- The app now starts local ad-free and confirmed VIP benefits together after a successful verified claim, rather than announcing ad-free while VIP is pending.

Baseline evidence (line numbers identify the audited merged main, before edits):

| File | Line |
|---|---|
| `app/src/main/java/com/nsec/vpn/ui/components/ServerCard.kt` | 64 |
| `app/src/main/java/com/nsec/vpn/ui/screens/SpeedTestScreen.kt` | 200 |
| `app/src/main/java/com/nsec/vpn/ui/screens/SettingsScreen.kt` | 208 |
| `app/src/main/java/com/nsec/vpn/ui/components/DeviceRecoverySheet.kt` | 55 |
| `app/src/main/java/com/nsec/vpn/data/local/KeyStoreManager.kt` | 170 |
| `app/src/main/AndroidManifest.xml` | 70 |
| `app/src/main/java/com/nsec/vpn/data/repository/VpnRepository.kt` | 502 |
| `app/src/main/java/com/nsec/vpn/data/repository/VpnRepository.kt` | 531 |
| `app/src/main/java/com/nsec/vpn/MainActivity.kt` | 110 |
| `app/src/main/java/com/nsec/vpn/ui/screens/ServerListScreen.kt` | 121 |

## Scope and reward limitation

The owner's additional reward request requires Android repository reward coordination and MainActivity changes beyond pure layout. No backend, schema, endpoint, agent, VPN routing, signing, ad ID, consent or security validation was changed. No migration/env dependency is required. Backend `grantVerifiedReward` already grants VIP within the signed-callback transaction; client claims cannot grant themselves VIP.

This does NOT guarantee instant delivery of Google's signed callback. Debug config deliberately uses Google test-unit IDs (`RemoteConfigManager.kt:100–115`), while the backend validates the configured live rewarded unit (`backend/src/services/rewardVerification.ts:31–34`). A test SDK callback cannot prove a production VIP grant. The pending state remains honest when Google verification is missing. No fake client-only VIP fallback is added.

For the real flow, in AdMob → Apps → Nsec VPN → rewarded ad unit → server-side verification, configure:

`https://api.nsecvpn.com/api/v1/billing/rewarded/ssv`

Use the real release rewarded unit configured in your admin console. The existing SDK sets installation identity as user_id and ticket as custom_data. Ensure Cloudflare allows machine requests to this exact path without browser challenges, while backend signature checking remains enabled. Test the callback in AdMob, then test a legitimate signed rewarded flow on a correctly configured release build. Do not treat a debug test ad as production grant evidence. Invalid unsigned requests should return 400; that only proves routing, not a valid reward.

If a signed callback is not delivered or accepted, this update cannot fabricate VIP. Review callback setup and acceptance; never paste private keys/tokens, signed callback query strings or credentials. Backend signed verification still protects against replay, ticket ownership abuse and unearned VIP. Google documentation: https://developers.google.com/admob/android/ssv and https://support.google.com/admob/answer/9603226 . Android theme configuration: https://developer.android.com/reference/android/app/UiModeManager .

## Checks and install

Local: 12 Python Android configuration/branding checks, 3 profiling parser tests and 23 deployment regressions pass. Changed Kotlin parses using tree-sitter after normalizing a catch/newline grammar limitation; that is NOT type checking. Gradle compile attempt again fails before compilation because downloading Gradle 9.8.1 is network-blocked. No SDK/device/emulator is available. New UiAlignmentRegressionTest and RewardClaimFeedbackTest await Android Studio/CI. No measured speedup, zero-warning build or live VIP activation is claimed here.

1. Extract the fresh ZIP. Run apply-alignment-vip-update.py against your existing Git project with --publish. It creates Nsec-VPN-alignment-vip-repair and preserves original edits.
2. If no GitHub CLI session is available, open the printed compare URL and click Create pull request. GitHub integration writes are denied (403), so no PR has been created by this session. Wait for CI before merging.
3. Open the NEW project in Android Studio. Sync Gradle, build/run app. The speed helper and backend/agent were not changed, so no backend migration or deployment is needed.
4. Run UiAlignmentRegressionTest and RewardClaimFeedbackTest, then app/helper lint/build/unit checks already in CI. On a test phone, confirm the following in light/dark, portrait/landscape and enlarged fonts:
   - FASTEST beside the name when space permits, wrapped badges flush left, long names/countries contained; star click does not select a node.
   - Speed metrics have balanced rows; labels/values/button/advertisement remain reachable.
   - Developer / Debug precedes App Version; recovery stays outside that category; ID copy matches the displayed installation ID and shows Copied.
   - Switch Light → Dark → Light repeatedly: current screen/scroll remain, splash stays matched on next cold launch, native ad XML colors update.
   - Refresh rotates during the request, repeat taps are disabled, failure feedback appears, animation stops afterward/backgrounded.
   - VIP sheet closes, reward/paid buttons work, unavailable/pending states do not claim active access.
   - For a signed real reward: server verifies once, client reads VIP promptly, both benefits become active, and a VIP-only server authorizes access. Delayed/offline/rejected/expired claims never announce false VIP and retry safely.

Use scripts/profile-ui.py to capture theme switching and compare real device timings; no before/after runtime numbers are currently available.
