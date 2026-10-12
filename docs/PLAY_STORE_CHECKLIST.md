# Google Play Store Release & Compliance Checklist

Package Name: `com.nsec.vpn`  
Target SDK: **36 (Android 16)** | Minimum SDK: **24 (Android 7.0)**

---

## 1. Google Play VPN Service Policy Declaration

Under Google Play's *VPNService* policy:
1. In Play Console ➔ **App Content** ➔ **VPN Service declaration**:
   - Check **Yes**, this app uses `VpnService`.
   - **Core Functionality Justification**:
     > "Nsec VPN is a dedicated Virtual Private Network application whose sole and core purpose is to provide secure, encrypted network communications using the WireGuard® protocol. The VPNService API is required to establish encrypted network tunnels, route user IP traffic, and prevent packet eavesdropping on public Wi-Fi networks."
   - Provide a YouTube video link demonstrating the app connecting, showing the OS VPN dialog, and actively encrypting data.

---

## 2. Foreground Service Declaration (Android 14+)

In Android 14+ (`targetSdkVersion 34+`), apps declaring `FOREGROUND_SERVICE` must specify a specific type:
- **Type Used**: `systemExempted`
- **Manifest Declaration**:
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
  ```
- **Service Declaration**:
  ```xml
  <service
      android:name=".vpn.NsecVpnService"
      android:permission="android.permission.BIND_VPN_SERVICE"
      android:foregroundServiceType="systemExempted"
      android:exported="false" />
  ```
- **Play Console Justification**:
  > "The application maintains an uninterrupted, continuous encrypted WireGuard VPN tunnel while running in the background. The foreground service displays persistent traffic statistics and status notifications required for network stability."

---

## 3. Ads & Split Tunneling Compliance (Google Play Policy)

Under Google Play policies regarding Monetization and Ads:
- **Ad Traffic Split Tunneling**: The application implements automated split tunneling exclusion rules (`addDisallowedApplication`) for the app's own package (`com.nsec.vpn`) and ad request endpoints. This ensures Google Mobile Ads (AdMob) SDK calls route directly over the standard network interface outside the VPN tunnel, preventing ad delivery failures, timeout errors, and ad mediation policy violations.
- **Data Safety Form Responses**:
  
  | Question | Answer | Details |
  |---|---|---|
  | Does your app collect or share user data? | **Yes** | Device or other IDs (Random installation ID for subscription sync and ad identifiers where enabled). |
  | Is data encrypted in transit? | **Yes** | App API traffic requires HTTPS; verify deployed TLS and the private or HTTPS agent channel. Tunnel traffic is ChaCha20-Poly1305. |
  | Can users request data deletion? | **Yes** | Operator must implement and verify server-side deletion before claiming this; clearing local data only resets the device. |
  | Does the app log web browsing or traffic? | **No** | Zero logs of websites visited, apps accessed, or IP payloads. |
  | Are IP addresses logged? | **No** | App/backend/agent request logging is disabled; independently verify reverse proxies, CDN and cloud logging. |

---

## 4. Visual Assets & Store Listing Checklist

- [ ] **App Name**: Nsec VPN (Under 30 characters).
- [ ] **Short Description**: Fast, secure, and zero-logs WireGuard® VPN.
- [ ] **App Icon**: 512x512 PNG, 32-bit color, no transparency.
- [ ] **Feature Graphic**: 1024x500 PNG / JPG.
- [ ] **Screenshots**: At least 4 screenshots (Home screen, Server list, Speed test, Settings).
- [ ] **Privacy Policy URL**: `https://nsecvpn.com/privacy` or `https://api.nsecvpn.com/api/v1/legal/privacy`.
update
