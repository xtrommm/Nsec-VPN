# Nsec VPN Security Architecture & Threat Model

---

## 1. Threat Model & Mitigations

| Threat | Attack Vector | Nsec VPN Mitigation |
|---|---|---|
| **Traffic Eavesdropping** | Public Wi-Fi packet capture | Full tunnel encryption with WireGuard® (ChaCha20-Poly1305 AEAD). |
| **DNS Leaks** | ISP overriding client DNS queries | In-tunnel encrypted DNS resolver enforcement (`1.1.1.1` / `8.8.8.8`). |
| **IP Address Scraping** | Compromised VPN database | Zero-logs architecture: No client IP addresses are logged or stored. |
| **Token Compromise** | Refresh token interception | RFC 6749 Token Rotation with automatic revocation of all active sessions upon reuse detection. |
| **Shell Injection** | Malicious public key payload | Gateway agent uses parameterized `execFile` exclusively with strict regex sanitization. |
| **Replay Attacks** | Intercepting agent control packets | HMAC-SHA256 signatures with 60-second validity window and deduplication cache. |

---

## 2. Vulnerability Disclosure & Bug Bounty

If you discover a security vulnerability, please report it privately:
- **Email**: `security@nsecvpn.com`
- Please do NOT open public GitHub issues for security vulnerabilities.
- We aim to respond within 24 hours and patch critical issues within 72 hours.
