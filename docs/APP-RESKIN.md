# Charcoal theme and server request sheet

Dark mode now uses neutral charcoal backgrounds, layered grey surfaces and brighter theme-aware blue accents. Main text, secondary text and captions were checked against the new backgrounds: contrast ratios 15.28:1, 9.18:1 and 6.44:1 respectively. Selected navigation, selected server borders and text/icon accents use theme roles. Existing light-mode palette, branding and branded button fills are retained.

Settings → Request Server now opens an expanded, scrollable bottom sheet with location icons, country/city fields, optional note, character count, input errors, progress and explicit backend errors. Close, swipe, outside tap and Back dismissal are disabled while submitting. The same authenticated request API and server-side 10-minute cooldown are used. Input remains available on errors; no server availability or approval is invented.

## Update the contact email

1. Sign in at https://admin.nsecvpn.com with a Super Admin account.
2. Open **Remote config & ads**.
3. Under **Support & app controls**, edit **Support email**.
4. Click **Publish settings** and confirm the success message.
5. Open Nsec VPN Settings. The app refreshes remote settings approximately every 30 seconds while open; reopening it also fetches settings. The new address appears in Support Email and is used for mail and copy actions.

No APK rebuild is needed for email changes. If publishing fails, the previously verified email remains in the app; do not assume an unsaved input changed the backend. A blank value means no support email has been configured.

## Apply and verify

Apply the updater to your phase-2 project; it creates `fix/app-charcoal-reskin` and can publish that branch. It does not change build files, dependencies, ad IDs, signing, VPN configuration or database schema. If phase 2 has not been merged, the new branch also carries the phase-2 commits it starts from.

Open a PR, run the existing Android CI, then sync Android Studio and run the **app** configuration. The already installed speed helper needs no change for this UI update.

Manual checks: dark/light/system themes on Home, Connected, Servers, Settings and Speed Test; readable selected tab and cards; open Request Server with keyboard and large font size; invalid fields show errors; valid submit appears in admin; immediate repeat shows cooldown; offline/blocked-device errors retain input; progress prevents duplicate taps and dismissal; close/Back work afterward; support mail/copy use the saved remote email.

Validation: source diff/role imports and contrast checks passed. Full Android compilation, lint and visual/device verification cannot be claimed locally because Gradle downloads are blocked for this workspace's Java process. Use CI and Android Studio before merging. No new dependency, environment variable or migration is required.
