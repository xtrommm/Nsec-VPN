# Disconnect UI and consent removal

Base: merged/live release c07e0259c5ab616a0d8ec1eb4cde53aef1447ba2. Branch: fix/disconnect-transition-no-ump.

## Evidence and changes

The supplied 7.29-second recording was inspected as frames. The second disconnect visibly passes through a CONNECTING header before Home. This is a display bug, not evidence of a new successful connection. No phone CPU/frame trace was supplied, so precise jank attribution or a millisecond improvement cannot be proved from the video alone.

- Base ConnectedScreen.kt:112,173,221: all non-Connected states labelled Connecting and timer labelled Connected regardless of state. ConnectionPresentation now distinguishes Disconnecting, Connecting, Disconnected and Error.
- Base MainActivity.kt:303–307 and 360–439: navigation to Home waits for terminal state; mutually exclusive ConnectedScreen/Home branches dispose and recreate Home. HomeConnectionHost keeps Home composed/measured at the same callsite, unplaced while details are open. Disconnect tap requests teardown and immediately returns to retained Home in Disconnecting, without claiming confirmed DOWN. Reconnect remains disabled until the real manager publishes completion.
- Base ConnectButton.kt:43–64: always-present infinite transition and a 500ms state-colour tween. Feedback colours are immediate; hidden/idle Home does not run a pulse. Layout size is unchanged.
- Interstitial dismissal could unconditionally reopen Connected even after disconnection. The callback now rechecks actual tunnel state; disconnect clears pending user navigation.
- SmartNativeAd excludes hidden Home and Disconnecting. SDK preload waits for the first navigation frame. The hidden Home is never placed, so it is not drawn or touchable. Connected-screen ads remain supported.

The previous independent/bounded remote cleanup and actual WireGuard DOWN confirmation are retained. These changes address the observable transition and avoid cold Home composition; device validation is still necessary to measure remaining jank.

## Owner-requested consent change

Remove AdConsentManager, UMP form calls, privacy settings rows and direct UMP dependency. No synthetic consent flag or affirmative consent record is created. SDK initialization is explicit/idempotent, runs off Main and enables placements only after readiness; configuration/ad requests run on Main. All native/interstitial/rewarded requests now use npa=1 non-personalized requests. Existing ad IDs, signing, reward verification and admin kill switch are retained.

Google SDK privacy/consent obligations still require publisher review; npa=1 does not exempt every regional consent requirement. This implementation deliberately does not claim that removing the dialog establishes consent or publication compliance. References: https://developers.google.com/admob/android/quick-start and https://developers.google.com/admob/android/optimize-initialization . The later owner-requested removal supersedes the UMP implementation described in AUDIT-FOLLOWUP.md.

## Checks and limitations

- Android configuration tests: 10 passed; both cloud/device/legacy backup checks passed.
- Added HomeConnectionHostTest: preserves remembered Home state across details, no Home disposal, immediate Home Disconnecting display and disabled reconnect before DOWN.
- Added ConnectionPresentationTest: transitional state never claims Connecting or confirmed disconnection.
- Updated AdMobManagerTest: initialization called once, readiness gates placements and remote kill switch disables them.
- git diff --check passed. Android compilation/lint/Robolectric could not run locally because the Gradle distribution download has no network access. Full GitHub CI is required; no passing result is invented.
- No database/API/server changes or migrations. No new dependency; direct UMP dependency removed. Existing runtime Gradle warnings are not suppressed.

## Install and test

1. Extract Nsec-VPN-disconnect-ui-update.zip into Windows Downloads.
2. Run apply-disconnect-ui.py with your existing Git project root and --publish. It clones a separate Nsec-VPN-disconnect-ui-repair checkout, imports exact reviewed commits, leaves original local edits untouched and pushes only the new branch. Authenticated GitHub CLI creates a draft PR; otherwise use the printed comparison URL and Create Pull Request.
3. Wait for CI. Open the printed separate checkout in Android Studio, Sync Gradle and run app on your phone. Both modules are included; the helper's code is unchanged and the already installed helper need not be reinstalled for this UI-only change.
4. Keep app data. Connect, open connection details, tap Disconnect. Home must appear with Disconnecting if teardown is still active; it must not say Connecting or enable another connection until DOWN. Repeat 5 times. Confirm actual internet/protection state with the WireGuard interface rather than UI alone.
5. Open details and return Home without disconnect: still Connected; scroll/dismissed announcement state stays retained. Switch main tabs, rotate, check light/dark mode and back button.
6. Consent form/privacy choices row must be absent. Check remote kill switch, free ads and backend-verified rewarded VIP; existing VIP/ad-free reward suppresses normal placements. Hidden Home must not show an ad behind connection details.
7. Test an interstitial dismissed after disconnect or background state change: it must not reopen a stale Connected screen.

For remaining device jank, capture a system frame/CPU trace in Android Studio Profiler. A screen recording establishes visible behavior, not thread stacks or proven root CPU time.
