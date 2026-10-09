# 🛡️ Nsec VPN — High-Speed WireGuard® Android Client & Management Suite

[![Platform](https://img.shields.io/badge/Platform-Android_14--16_%28API_24--36%29-blue.svg)](https://developer.android.com)
[![Protocol](https://img.shields.io/badge/VPN_Engine-WireGuard®_Core-emerald.svg)](https://www.wireguard.com)
[![Architecture](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-blue.svg)](https://developer.android.com/jetpack/compose)
[![Backend](https://img.shields.io/badge/Server-Node.js_•_Docker_•_PostgreSQL-indigo.svg)](https://docker.com)
[![Zero-Logs](https://img.shields.io/badge/Security-Zero--Logs_Verified-success.svg)](https://nsecvpn.com/privacy)

**Nsec VPN** (`com.nsec.vpn`) is a modern, production-grade, zero-logs WireGuard® VPN application designed for Android devices, paired with a web management portal and scalable gateway orchestration backend.

Official Website: **[nsecvpn.com](https://nsecvpn.com)**

---

## 📑 Table of Contents
1. [Key Features & Capabilities](#-key-features--capabilities)
2. [Project Architecture](#-project-architecture)
3. [Android App Setup & Build](#-android-app-setup--build)
4. [Backend & Web Admin Panel Deployment](#-backend--web-admin-panel-deployment)
5. [WireGuard Server Node Installation](#-wireguard-server-node-installation)
6. [Google Play Store Release & Policy Guide](#-google-play-store-release--policy-guide)
7. [API Specification](#-api-specification)
8. [License & Privacy Guarantee](#-license--privacy-guarantee)

---

## 🚀 Key Features & Capabilities

### 📱 Android Client (`/app`)
- **Modern Pro UI**: Designed with a clean, high-contrast Material 3 Slate/White theme with smooth pulse animations and responsive cards.
- **WireGuard® Protocol Engine**: Built directly on the native userspace Go backend (`wireguard-android`) using ChaCha20-Poly1305 authenticated encryption and Curve25519 key exchanges.
- **Android 14+ Foreground Service**: Fully compliant with Android 14, 15, and 16 (`systemExempted` foreground service type) preventing OS service kills.
- **Smart Connect**: Automatically pings global server nodes via UDP socket handshakes and routes traffic through the lowest latency gateway.
- **Custom Encrypted DNS**: One-tap selection between Cloudflare (`1.1.1.1`), Google (`8.8.8.8`), AdGuard Ad-Blocking DNS (`94.140.14.14`), and Quad9 Security Filter (`9.9.9.9`).
- **App Split Tunneling**: Route specific banking, entertainment, or local applications outside the VPN tunnel while keeping all other traffic encrypted.
- **System Kill Switch Integration**: Direct deep-linking into Android system-level *Always-On VPN* to eliminate packet leakage.
- **Live Bandwidth & Speed Benchmark**: Real-time throughput graphs, duration trackers, and an interactive speedometer testing tunnel download throughput, jitter, and latency.
- **Comprehensive Diagnostic Suite**: In-app automated verification of MTU packet sizing (1420 clamping), DNS leak prevention, and IPv6 blackhole routing.
- **Anonymous Device Authentication**: Uses hardware-backed Android Keystore UUIDs. No email addresses, phone numbers, or passwords required.
- **Quick Settings Tile**: `VpnTileService` lets users toggle their VPN connection directly from the Android system notification shade.
- **Monetization Engine**:
  - Integrated Google Play Billing (`nsecvpn_premium_monthly` and `nsecvpn_premium_yearly`).
  - Google AdMob support with banners, exit interstitials, and rewarded ads granting 30-minute free VIP passes.

### 🖥️ Web Admin Panel (`/admin-panel`)
- **Real-Time Gateway Monitoring**: View active WireGuard tunnels, connected clients, server utilization, and bandwidth transfer rates.
- **Node Management**: Add, modify, or disable VPN server endpoints (`IP:Port`), public keys, country flags, and tiers (**FREE** vs. **VIP**).
- **Remote Configuration**: Toggle AdMob banners, interstitials, and rewarded ads remotely without submitting new app updates to Google Play.
- **Network Emergency Alerts**: Broadcast announcements and notices directly to the Android app Home screen.
- **Minimum Version Enforcement**: Remotely mandate minimum version codes to ensure users remain on the latest secure build.

---

## 🏗️ Project Architecture

```
Nsec-VPN/
├── app/                          # Android Client Application
│   ├── src/main/java/com/nsec/vpn/
│   │   ├── MainActivity.kt       # Single-activity Compose entrypoint
│   │   ├── NsecVpnApp.kt         # Application lifecycle & dependency locator
│   │   ├── core/network/         # Retrofit2 REST client & ServiceLocator
│   │   ├── data/                 # Repositories, models, Keystore vault, billing
│   │   ├── tiles/                # Quick Settings Tile Service
│   │   ├── ui/                   # Jetpack Compose UI (Screens, Components, Theme)
│   │   └── vpn/                  # WireGuardTunnelManager & NsecVpnService
│   ├── build.gradle.kts          # Module build config (applicationId: com.nsec.vpn)
│   └── proguard-rules.pro        # R8 / ProGuard obfuscation & native keep rules
├── backend/                      # Production REST API
│   ├── src/index.ts              # Express API (Auth, Servers, Tunnels, Config)
│   ├── Dockerfile                # Production Node.js 20 container
│   └── package.json
├── admin-panel/                  # Web Management Dashboard
│   ├── src/App.tsx               # React 18 + Tailwind dashboard
│   ├── Dockerfile                # Multi-stage Nginx container
│   └── package.json
├── deployment/                   # Deployment & Infrastructure
│   ├── docker-compose.yml        # Orchestrates Backend, Admin Panel, Postgres, Nginx
│   ├── nginx.conf                # Reverse proxy config with SSL routing
│   └── install-wireguard-node.sh # 1-Click Ubuntu WireGuard gateway installer
└── README.md
```

---

## 🛠️ Android App Setup & Build

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer.
- **JDK**: Java 17 or Java 21.
- **Android SDK**: `minSdk 24` (Android 7.0+), `targetSdk 36` (Android 16).

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/Nsec-VPN.git
cd Nsec-VPN
```

### 2. Open & Sync in Android Studio
1. Open Android Studio and select **Open** ➔ select the `Nsec-VPN` folder.
2. Android Studio will automatically resolve Gradle dependencies.
3. If building for local testing, Gradle will automatically decode `debug.keystore` from `debug.keystore.base64`.

### 3. Build & Run
- Connect an Android device with USB debugging enabled or launch an emulator.
- Select the `app` configuration and click **Run** (▶) or press `Shift + F10`.

---

## 🌐 Backend & Web Admin Panel Deployment

Deploy the entire server stack to any Ubuntu 22.04 or 24.04 VPS with a single command.

### 1. VPS Preparation
Ensure your DNS records point to your VPS IP:
- `api.nsecvpn.com` ➔ `YOUR_VPS_IP`
- `admin.nsecvpn.com` ➔ `YOUR_VPS_IP`

Install Docker and Docker Compose on the VPS:
```bash
sudo apt update && sudo apt install -y docker.io docker-compose git
sudo systemctl enable --now docker
```

### 2. Launch the Stack
```bash
git clone https://github.com/your-username/Nsec-VPN.git /opt/nsecvpn
cd /opt/nsecvpn/deployment
docker-compose up -d --build
```

### 3. Accessing Services
- **Admin Panel**: Visit `http://admin.nsecvpn.com` (or `http://YOUR_VPS_IP`)
- **Backend API**: Visit `http://api.nsecvpn.com/api/v1/servers`

---

## ⚡ WireGuard Server Node Installation

To add new global VPN locations (e.g., US, Germany, Singapore), run the automated node installer on each VPN gateway VPS:

### 1. Run the Node Installer
SSH into the target server as `root` and run:
```bash
curl -sSL https://raw.githubusercontent.com/your-username/Nsec-VPN/main/deployment/install-wireguard-node.sh | sudo bash
```

### 2. Copy Node Credentials
The script configures IPv4 forwarding, NAT masquerading, and WireGuard `wg0`, then outputs:
```text
==========================================================
    WireGuard Node Configured Successfully!
==========================================================
Public IP / Endpoint: 198.51.100.24:51820
WireGuard Public Key: 8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M=
==========================================================
```

### 3. Register Node in Admin Panel
1. Open the Admin Panel (`admin.nsecvpn.com`).
2. Navigate to **WireGuard Servers** ➔ click **Add New Node**.
3. Paste the **Endpoint IP:Port** and **Public Key**.
4. Set the location name and tier (**FREE** or **VIP**).
5. Click **Save** — the node is immediately accessible to all Android clients!

---

## 📦 Google Play Store Release & Policy Guide

### 1. Generate Production Release Keystore
```bash
keytool -genkey -v -keystore my-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```
Save `my-upload-key.jks` in a secure location.

### 2. Build the Signed Android App Bundle (.AAB)
In Android Studio:
1. Navigate to **Build ➔ Generate Signed Bundle / APK**.
2. Select **Android App Bundle (.aab)**.
3. Select your `my-upload-key.jks` file, enter credentials, and select **Release**.
4. The generated bundle will be located at:
   ```
   app/build/outputs/bundle/release/app-release.aab
   ```

### 3. Google Play Console Declaration Forms

#### A. VPN Service Declaration Form
- **Core Functionality**: Select **Virtual Private Network (VPN)**.
- **Justification Statement**:
  > *"Nsec VPN is a dedicated virtual private network utility whose primary purpose is encrypting user network traffic over the modern WireGuard protocol. The application uses android.net.VpnService exclusively to create a secure, encrypted tunnel (tun0) to remote VPN gateways upon explicit user action. Nsec VPN enforces a strict Zero-Logs policy and never inspects, logs, or stores browsing activity, DNS queries, or user traffic contents."*

#### B. Data Safety Form
- **Data Collection**: No personal data (no name, email, or physical location).
- **Device Identifiers**: Collected for app functionality and anonymous licensing (encrypted in transit, not linked to user identity).
- **Web Browsing & Network Data**: **None collected**.

---

## 📡 API Specification

All mobile client requests communicate over HTTPS with `https://api.nsecvpn.com/api/v1/`.

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `POST` | `/auth/register-device` | Registers hardware UUID and issues JWT tokens | No |
| `POST` | `/auth/refresh-token` | Renews expired access tokens | No |
| `GET` | `/servers` | Fetches active WireGuard server nodes & load | Bearer Token |
| `POST` | `/vpn/connect` | Requests tunnel IP allocation (`10.8.0.x`) & server peer keys | Bearer Token |
| `POST` | `/vpn/disconnect` | Teardowns active WireGuard peer lease | Bearer Token |
| `GET` | `/config` | Returns remote config, ad toggles, and announcements | No |
| `GET` | `/legal/:type` | Returns Markdown terms and zero-logs privacy policy | No |

---

## 🔒 License & Privacy Guarantee

- **Zero-Logs Guarantee**: Nsec VPN operates under a verified no-logs standard. No originating IP addresses, connection timestamps, DNS lookups, or transmitted payloads are ever stored.
- **WireGuard®** is a registered trademark of Jason A. Donenfeld.
- Licensed under the **MIT License**.
