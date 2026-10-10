# Nsec VPN Testing Strategy & Verification Plan

This document outlines the automated test suite and recommended manual testing procedures for production releases.

---

## 1. Automated Test Suites

### Backend Unit Tests (Jest & ts-jest)
```bash
cd backend
npm test
```
**Coverage Areas:**
- `validation.test.ts`: Zod schema validation across all device and admin endpoints.
- `billing.test.ts`: Google Play in-app billing verification and state transitions.

### Android Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
**Coverage Areas:**
- ViewModels and repositories.
- Keystore encryption and fallback logic.
- Quick settings tile service logic.

---

## 2. 15 Critical Manual Verification Procedures

Before deploying to production, execute these 15 manual tests:

1. **WireGuard Handshake Initiation**: Connect from Android app and verify `latest handshake` advances in `wg show wg0`.
2. **DNS Leak Prevention**: Visit `https://browserleaks.com/dns` while connected; ensure only tunnel DNS (Cloudflare/Google) is detected.
3. **IPv6 Blackhole Routing**: Verify no IPv6 address leaks bypass the tunnel on dual-stack carrier connections.
4. **App Kill Switch / System Always-On**: Verify no traffic flows when VPN is paused if Always-On is enabled in Android settings.
5. **Split Tunneling Verification**: Add Chrome or Banking app to excluded list; verify external IP matches carrier IP while other apps route through VPN.
6. **Token Expiration & Refresh Rotation**: Expire access token and verify app seamlessly refreshes token via `POST /auth/refresh-token`.
7. **Compromised Refresh Token Revocation**: Replay a previously revoked refresh token; confirm all active sessions for the device are invalidated.
8. **Subnet Address Exhaustion**: Simulate filling all 253 host IPs in a `/24` subnet; verify backend returns HTTP 503 `SERVER_FULL` gracefully.
9. **Gateway Agent Failure Rollback**: Cut agent network during connect; verify DB session rolls back to inactive and client receives HTTP 502.
10. **Server Reboot Peer Persistence**: Reboot WireGuard gateway server; verify existing peers re-establish handshakes automatically.
11. **Super Admin vs Admin Permissions**: Attempt server deletion as `ADMIN` user; confirm backend rejects with HTTP 403 `FORBIDDEN`.
12. **In-App Billing Mock & Real Purchase**: Purchase VIP subscription in Play Store sandbox; verify backend updates device tier to `VIP`.
13. **Google AdMob Toggles**: Turn off banner ads in Admin Panel; verify mobile app hides banners on next app launch.
14. **Emergency Home Screen Announcement**: Enable announcement in Admin Panel; verify banner appears on Android app Home screen.
15. **Quick Settings Tile**: Toggle VPN on and off from Android quick settings notification drawer.
16. **Dynamic Theme & Dark Mode Switching**: In Settings, toggle between System, Light, and Dark mode; verify contrast, status bar icons, and badge rendering across all screens.
