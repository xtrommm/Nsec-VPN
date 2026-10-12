$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo
if (!$env:JAVA_HOME -and !(Get-Command java -ErrorAction SilentlyContinue)) {
    $studioJava = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
    if (!(Test-Path "$studioJava\bin\java.exe")) { throw "Set JAVA_HOME to Android Studio's JDK 21 before running this script." }
    $env:JAVA_HOME = $studioJava
}
& .\gradlew.bat :app:assembleDebug :speedtest-helper:assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Android build failed. Fix the reported error before installing." }
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (!(Test-Path $adb)) { throw "ADB not found. Install Android SDK Platform-Tools in Android Studio SDK Manager." }
$devices = @(& $adb devices | Select-String '^\S+\s+device$')
if ($devices.Count -ne 1) { throw "Connect exactly one phone with USB debugging enabled and accept its authorization dialog." }
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
if ($LASTEXITCODE -ne 0) { throw "Main APK installation failed. Do not uninstall it: preserve its device identity and use the original signing key." }
& $adb install -r "speedtest-helper\build\outputs\apk\debug\speedtest-helper-debug.apk"
if ($LASTEXITCODE -ne 0) { throw "Helper installation failed. Both APKs must have matching signing certificates." }
& $adb shell am start -n com.nsec.vpn/.MainActivity
if ($LASTEXITCODE -ne 0) { throw "App installed, but could not be opened automatically. Open Nsec VPN on the phone." }
Write-Host "Both APKs installed. Connect to a server, then use Speed Test inside Nsec VPN."
