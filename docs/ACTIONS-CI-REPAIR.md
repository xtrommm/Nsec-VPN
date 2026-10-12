# GitHub Actions repair — 2026-10-12

## Evidence

Latest main at review: `4dcc12d06d2a9b1eb3e1c86326784cf85655f2e2`.

- CI Pipeline #57: https://github.com/xtrommm/Nsec-VPN/actions/runs/38276884227 . Android job `114904418873` passed unit/Robolectric tests and failed Android lint. Both speed-test helper debug/release lint reports identify `speedtest-helper/src/main/AndroidManifest.xml:6` with **DataExtractionRules**: `allowBackup=false` without Android 12+ XML rules.
- Earlier CI #53 (https://github.com/xtrommm/Nsec-VPN/actions/runs/38271477593), Android job `114886513194`, reports the same helper manifest error. Repeated reskin merges did not address that module.
- The latest agent, backend (including DB integration and Docker build), admin panel and deployment-script jobs all passed.
- Auto Deploy Website #9 was **skipped**, not failed, because its upstream CI conclusion was failure. Keep that gate; do not deploy failed Android changes just to make the workflow appear green. This workflow deploys the VPN backend/admin services, not the separately uploaded public marketing website.
- Latest Android logs also report `UNNECESSARY_SAFE_CALL` at `RemoteConfigManager.kt:57` and `SpeedTestClient.kt:49`. These are corrected without changing behavior.
- Gradle emits `Configuration.setVisible(boolean)` deprecation messages from AGP internals (e.g. BasePlugin/SourceSetManager), not app source. Those messages did not cause the lint failure. No unsupported plugin/Gradle version swap or hidden warning setting is included; upstream compatibility maintenance is separate work.

## Changes

Both modules retain `allowBackup=false` and now have explicit XML references and rules covering cloud backup, device transfer, and Android 11-and-lower backup. Each excludes the complete root/file/database/sharedpref/external and supported device-protected domains. Main-app empty/template rules previously left default device-transfer eligibility ambiguous; installation-specific encrypted vault data and identity should not migrate to a different device through backup.

Official Android reference: https://developer.android.com/identity/data/autobackup . Explicit device-transfer rules are necessary because some manufacturers do not disable D2D transfer just from `allowBackup=false`. This does not remove existing app data or regenerate keys on an update. A fresh install/new device still creates its own identity by design. The project does not opt into cross-platform transfers; if introduced later, the guard requires complete exclusions in that mode too.

A lightweight CI guard checks manifest XML references, denies empty/missing cloud/device policies and detects any reintroduced include rules. It runs before expensive Android unit/lint work. Complete lint reporting now covers `app` and `speedtest-helper`, debug and release. Strict lint, builds, signing configuration, ad IDs, dependency versions and deployment conditions are unchanged.

## Verification

Passed locally:

```text
python3 scripts/check-android-backups.py
  PASS: app cloud/device/legacy backup policy
  PASS: speedtest-helper cloud/device/legacy backup policy
python3 -m unittest discover -s tests/android-config -p 'test_*.py'
  6 tests, OK
```

Workflow YAML parses; inline shell syntax is checked; the patch passes `git diff --check`. Existing deployment regressions pass. Full Android execution was attempted with `bash gradlew ...`, but failed before project configuration because this workspace’s Java networking cannot download Gradle (`Network is unreachable`). Therefore full lint/Android compilation is **not yet verified** after this patch. Prior passing Android unit tests are evidence about the old commit, not a claim about the new commit.

Publish `fix/android-backup-rules-ci`, open a PR to main, and require all five CI jobs to pass before merging. After merge, the push CI and eligible deployment run should complete. An old failed run remains historical; retrying the old SHA cannot validate new code. No change can guarantee every future code edit or runner/provider failure will pass.

Local/Android Studio checks if needed:

```powershell
.\gradlew.bat :app:testDebugUnitTest :speedtest-helper:testDebugUnitTest :app:lintDebug :app:lintRelease :speedtest-helper:lintDebug :speedtest-helper:lintRelease :app:assembleDebug :app:assembleRelease :speedtest-helper:assembleDebug :speedtest-helper:assembleRelease --no-daemon
```

CI uses an ephemeral debug signing key for validation; its release output is not a production upload. Existing phone installation remains intact. No backend migration, server restart, or website update is required for this repair.
