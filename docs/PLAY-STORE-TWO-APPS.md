# Publishing Nsec and the speed-test companion

## Why two apps exist

`app` uses application ID `com.nsec.vpn`. `speedtest-helper` uses `com.nsec.vpn.speedtest`. Both are Android **application** modules, not one app plus a library. Running only the app module in Android Studio installs only Nsec. The speed helper runs under a separate Android identity so its network traffic can use WireGuard while the main app and its AdMob/API traffic intentionally bypass the tunnel. The helper is protected by a signature permission and checks the caller's signing identity.

Simply copying the helper into the main app, making a dynamic feature, or putting it in another ordinary process of the same app does not preserve that UID separation. A server-only benchmark would measure node-to-test-server performance, not phone-through-VPN performance. This update keeps the working two-app architecture; no unverified one-install solution or automatic secondary APK installation is introduced.

## Test on another phone now

Open the updated repository in Android Studio, sync, connect one authorized USB-debugging phone, and run in the Android Studio PowerShell terminal:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-android-debug.ps1
```

It builds/installs both APKs. Keep the helper included in split tunneling. Installing only `app` is insufficient. Use matching builds/signing certificates; do not uninstall a production-signed main app just to bypass a signature mismatch. A fresh install resets installation identity.

## Current Google Play release procedure

1. Create **two Play Console applications/listings**, one for each application ID. The helper listing must clearly identify its purpose/dependency and have accurate privacy/Data Safety information. Google reviews each app; approval is not guaranteed here.
2. Use the existing production signing configuration/keystore. In Android Studio, generate signed **Android App Bundles** for `app` and `speedtest-helper`, or configure existing KEYSTORE_PATH/STORE_PASSWORD/KEY_PASSWORD privately and run:

   ```powershell
   .\gradlew.bat :app:bundleRelease :speedtest-helper:bundleRelease
   ```

   Outputs are in each module's `build/outputs/bundle/release/` directory. An AAB is for Play Console; it is not directly installed with adb.
3. Configure Play App Signing so the **final Play app-signing key** is the same for the two apps. For the second app, the Play Console flow supports “Use the same key as another app in this developer account.” The local **upload key** is not necessarily the key Google uses on installed APKs; matching upload keystores alone does not establish signature permission compatibility. Preserve the original production signing identity and coordinate key upgrades for both packages.
4. Upload each module's AAB to its own **Internal testing track**. Install **both apps from those Play test listings** on a test phone. Verify package certificates match, caller authorization succeeds, main ads still bypass VPN, and helper traffic/counters/exit IP are verified through WireGuard. Testing only local debug builds does not verify Play signing.
5. Complete each listing's content/privacy declarations and required review/testing, then release both. The main listing/help should disclose the separate companion needed for speed tests. A public main-app install will **not automatically install the companion**. The current app's missing-helper message tells users it is needed; add a verified Play install link only once that helper listing exists.

There are no signing-config or key changes in this patch. Never publish CI validation APKs signed with the ephemeral debug key. Protect private signing keys/passwords; do not put them in Git or screenshots. Upload the main bundle to the main listing and helper bundle to the helper listing; they are not combined into one AAB.

Official references checked 2026-10-12:
- https://support.google.com/googleplay/android-developer/answer/9842756 (Play App Signing / same key as another app / upload versus app key)
- https://developer.android.com/guide/app-bundle/app-bundle-format (one application's base/features/configuration splits)
- https://developer.android.com/studio/publish/app-signing (signed bundles and signing continuity)

## Product choice for a later one-install release

A single installation needs a deliberate redesign of measurement/routing while preserving the ad exclusion, or a different speed-test feature with clearly disclosed measurement boundaries. That changes architecture/security behavior and needs real-device routing proof. It is not implemented in this UI/load update. Keep the companion optional for VPN connections and required only for VPN-bound in-app speed tests in the current release.
