# Changelog

All notable changes to the Nsec VPN project are documented in this file following the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) standard.

## [1.0.0] - 2026-10-09

### Added
- **Backend Architecture**: Production-grade Node.js/TypeScript backend with PostgreSQL and Prisma ORM.
- **WireGuard Node Agent**: Linux daemon supporting parameterized `wg` management with HMAC-SHA256 signatures and replay defense.
- **Web Admin Console**: Complete React 18 / Tailwind administration portal with live node health checks, device management, and audit logs.
- **Google Play Billing Verification**: Integrated subscription state verification supporting ACTIVE, IN_GRACE_PERIOD, ON_HOLD, PAUSED, CANCELLED, and EXPIRED states.
- **Dynamic IP Pool Management**: Concurrency-safe, retry-backed IPv4 host allocation supporting arbitrary server subnets.
- **Audit Logging**: Immutable system event logging for administrative operations.
- **Automated CI/CD**: GitHub Actions workflow running tests and builds across backend, agent, and admin panel.

### Security & Hardening
- **IDOR Defense**: Enforced strict device ownership verification on peer session terminations (`/vpn/disconnect`).
- **Token Reuse Defense**: Automatic revocation of all active device sessions upon detection of revoked refresh token reuse (RFC 6749).
- **Zero-Logs Guarantee**: Strict removal of any IP address logging or personal identifier storage.
- **Parameterized Execution**: Complete elimination of shell injection risks in the WireGuard node agent.
- **Non-Root Containers**: Docker images configured with non-root runtime users.

### Fixed
- **Compilation Failure**: Fixed invalid long literal `1000L` in `src/services/billing.ts`.
- **Missing TypeScript Config**: Added `tsconfig.json` for the admin panel.
- **Peer Persistence**: Configured `SaveConfig = true` and `wg-quick save` to preserve dynamic peers across server reboots.
- **Server Load Calculation**: Implemented dynamic load percentage updates on peer connections and disconnections.
