# Changelog

## Unreleased — admin experience phase 1

### Added
- Source audit with immutable file/line evidence.
- Day-based promo CRUD, bulk creation, schedules, encrypted retrieval, usage records and deletion tombstones.
- Authenticated server request API and management page; exact/partial device lookup and details.
- Server reordering, audit pagination, modern responsive admin console and global ad control.
- Additive migration, private promo-key configuration and exact-release schema approval scripts.

### Fixed
- Promo redemption consuming a code without a finite-VIP extension.
- Concurrent expiry cleanup overwriting a renewed entitlement, and billing overwriting a recorded promo grant.
- Sensitive fields in device update responses; missing audit/error handling and expired dashboard counts.
- Remote Config retry/save/delivery gaps, whole-record form submissions and stale admin responses.

### Changed
- Existing minute promo durations are preserved exactly as fractional days; new console campaigns use whole days.
- Promo deletion retains one-use evidence; copies of legacy hash-only codes require the original code.
- Remote settings cache only validated live responses and refresh while the app is open.

### Pending
- App experience items 1–7 and honest Troubleshooting assistant (in progress).
- Separate signed speed-test helper, chosen by the user, plus on-device VPN-path verification.

### Remote Config client delivery
- Validated API responses persist locally and refresh every 30 seconds while the activity is open.
- Failed refreshes report a visible error and retain verified settings; invalid or unsuccessful responses are rejected.
- Global ads controls, support contact and publish timestamp have backward-compatible optional DTO fields.
- Connect waits for verified settings and respects maintenance/minimum-version controls.
