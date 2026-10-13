# Popup Redesign And Live Free/VIP Status

Base: merged PR #23, main `2348533`. Branch: `fix/popups-live-server-status`.

## What changed

- `VipAccessGate.kt:VipRequiredDialog`: replaced the large default AlertDialog with a surface card, VIP icon, accessible X, full-width primary Watch An Ad button and separate outlined Purchase VIP Plan button. `MainActivity.kt` supplies the actual reward offer detail so a disabled reward button has an explanation. Existing purchase/reward callbacks and entitlement gate remain intact.
- `HomeAnnouncement.kt`: centered popup stays on Home, now with a neutral theme surface, outline, announcement label, icon, better title/body hierarchy and a pinned X outside the scrollable long body. Existing live refresh and dismissal/content-change behavior remain intact.
- `DeviceRecoverySheet.kt`: X above the scrolling form; disabled while busy, consistent with the existing gesture/back dismissal guard. PIN, device ownership and recovery API are unchanged.
- `MainActivity.kt`: the existing server catalog background loop previously waited 15 seconds between checks. It now checks every five seconds on Servers, restarts when entering the tab/resuming, and forces a fresh catalog read rather than the five-second local refresh throttle. Other tabs retain the 15-second interval. Polling stops when the Activity is not STARTED.
- `VpnRepository.kt:refreshServers`: optional showLoading defaults to true for existing calls; background metadata refresh passes false so rows update in place without an automatic spinner flash. Mapping already reads isPremiumOnly from the API and preserves current ping/favorite state; that working logic is retained.

There is no websocket or server push: visible Servers reflects admin changes on its next successful five-second poll, plus request and backend health-validation time. The backend still excludes disabled/unhealthy nodes. Network failures preserve the last validated list and show the existing error. No fake statuses or access grants.

## Validation

38 local Python checks pass: Android configuration 12, UI profiler parser 3, deployment 23. git diff --check passes. Added Android regression cases cover VIP/recovery close actions, disabled reward feedback, Free-to-VIP-to-Free catalog and selected-node updates, silent loading state and retaining real data on refresh failure. Android build/lint/Robolectric must pass in GitHub CI; the local environment cannot download Gradle and has no device/emulator. No performance measurements or CI success are claimed for this branch.

## Install and check

Run the included apply-popups-live-update.py against your existing Git checkout with --publish. It creates an isolated Nsec-VPN-popup-live-repair checkout, preserves the source project and pushes only the reviewed branch. Open the printed PR link, wait for CI and merge. Open the new checkout in Android Studio, Sync Gradle and Run app. No backend deployment, database migration or new env variables.

1. FREE user: tap VIP from Servers and from Home. Both popups match; Watch An Ad/Purchase VIP Plan run the original flows; X/back/outside dismiss. If pending/disabled, the real reason is visible.
2. Announcement: verify center position, long-body scrolling with X visible, both themes, dismissal and changed-content reopening.
3. Recovery: X remains above the form with keyboard/scrolling; while saving/restoring it is disabled, reenabled when the operation ends.
4. Keep Servers open, change a node FREE to VIP in admin. Wait for next successful poll; badge and filters update without Refresh. Change back and repeat. Check All/Free/VIP/Favorites, selected-node status and existing pings.
5. Repeat after background/resume and with no internet. Cached real rows remain visible on failure; metadata updates when connectivity returns.
