# Nsec VPN Strict Zero-Logs Privacy Policy

**Effective Date:** October 2026  
**Website:** https://nsecvpn.com  
**Contact:** privacy@nsecvpn.com  

---

### 1. Introduction & Zero-Logs Principle
At **Nsec VPN**, we believe privacy is a fundamental human right. Our systems are deliberately engineered from the ground up to operate without collecting or storing your online activity.

**We do NOT store or log:**
- Browsing history or visited domain names.
- Traffic content or data payload.
- Originating client IP addresses.
- VPN server IP connections correlated to personal identities.
- DNS queries or network requests.

---

### 2. Information We Process
To maintain continuous service reliability, we process only the bare minimum technical data:
- **Cryptographic Device UUID**: Generated randomly inside your device's Android hardware Keystore. This ID contains no personal information and cannot be linked to your identity.
- **Aggregated Performance Metrics**: Anonymous statistics (e.g., total active handshakes per region, app crash logs) to optimize gateway bandwidth.

---

### 3. WireGuard® Key Exchange
When establishing a VPN connection, your device generates a private/public keypair using Curve25519 cryptography. Only the public key and an assigned private subnet address (e.g. `10.8.0.x`) are sent to our gateway node. These values reside exclusively in the volatile RAM of the gateway kernel and are purged immediately upon disconnection.

---

### 4. Third-Party Services & Monetization
- **Google Play In-App Billing**: Subscriptions are processed directly by Google. We never receive or store credit card details.
- **Google AdMob**: Optional advertising for Free tier users is served via Google Mobile Ads SDK. Free users can upgrade to VIP or redeem Rewarded Passes to disable ads.

---

### 5. Data Deletion
Users can delete their device registration at any time by clearing application data in Android System Settings or selecting **Reset Keystore Vault** within the app.
