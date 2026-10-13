# Reward card startup correction

Base: 0e3ec6d61e5ce02205f11d25309ed4364547467d. Branch: fix/reward-offer-startup. Includes the owner-requested EU flag alignment correction, with logo size and day/night colours retained.

The two supplied screenshots show the temporary Your Next VIP Pass / Loading The Current Reward Offer card changing to the real 45 Minutes Of VIP offer. These still frames establish the display change, not a CPU/frame-time profile or measured one-second duration.

## Cause and change

- RemoteConfigManager restores cached settings only inside fetchRemoteConfig, which is started after Home composition. Restore validated disk settings before the first Home frame; preference access and Moshi adapter creation are lazy/off Main.
- MainActivity previously built RewardOffer from AdMobManager's effective placement config, which intentionally disables ads until SDK readiness. Offer data now comes from the validated remote configuration independently of SDK initialization. Ad requests and reward validation retain readiness/backend gates.
- Native splash waits only for disk bootstrap and only for at most 250ms; it never waits for the API. No separate splash page is introduced. Slow disk or a first install can still render the compact loading skeleton.
- No verified cache: show a static 64dp skeleton matching the usual compact card, rather than a temporary marketing title or fake duration. API failure produces Reward Offer Unavailable with existing actionable VIP options instead of indefinite loading. A validated cached offer is retained on refresh failure.
- Local ad-free reward state is now exposed even before SDK initialization so it does not briefly disappear at launch. Monotonic expiry/boot protections are unchanged.

No duration is hardcoded to 45 minutes. Existing API fields/JSON cache format are reused; no server endpoints, database migrations, ad IDs, signing settings, motto or logo size changed. UMP removal and non-personalized ad requests from the previous repair remain intact.

## Verification

Ten Android configuration tests and both cloud/device/legacy backup checks passed; git diff --check passed. Added/extended regression tests cover real cache restore without API calls, cached offer independence from SDK readiness, corrupt-cache fail-closed bootstrap, loading/error semantics, compact skeleton and pre-initialization ad-free state. The boot-count fixture is isolated to tests.

Android compilation, lint and Robolectric tests are NOT verified locally because Gradle download has no network access. Require all GitHub CI checks. No before/after device latency figure is claimed.

## Install

Extract the ZIP in Windows Downloads, run apply-reward-startup-v2.py with the existing Git project root and --publish. The updater creates a separate Nsec-VPN-reward-startup-final checkout and leaves existing edits untouched. It pushes only the new branch; authenticated gh creates a draft PR, otherwise click the printed comparison URL and Create Pull Request.

Open the printed checkout in Android Studio, Sync, then Run app on the phone. Keep app data. The already installed speedtest-helper is unchanged. No CloudPanel command or database migration is needed for this Android update.

## Short phone checklist

1. Open with internet once so current real config is saved. Close/reopen five times with normal app data retained: the known offer should appear without the old temporary loading title. No known duration is invented.
2. Change duration/enablement in admin; refresh the app and verify the actual backend value appears and is retained next launch. Global ad kill switch still suppresses the reward option.
3. Offline reopening with valid cache: retain real offer; watching a reward must fail clearly if ticket preparation cannot reach the backend, never grant server VIP from cache alone.
4. First install/cleared cache: compact skeleton while fetching; no false 45-minute offer. Network failure displays explicit unavailable state. Do not clear recovery data just to test your own VIP account; use a test phone/account.
5. During SDK initialization the offer text remains stable. Watch Ad may report that the ad is loading until an actual ad is ready; no fake readiness is asserted.
6. Existing local ad-free reward: status appears before SDK readiness and expires under the existing boot/clock policy. Backend VIP continues to require verification.
7. Check aligned Made in Europe flag on native splash in light/dark mode; logo size and motto unchanged. Retest connect/disconnect after install.
