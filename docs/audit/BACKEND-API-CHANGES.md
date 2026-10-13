# API compatibility and endpoint verification

Existing request/response field names are retained. No Android data-model change. Routes below are relative to `/api/v1`; health and readiness are root routes. Tests are in backend/src/__tests__ unless marked integration.

| Endpoint | Change or reviewed safeguard | Evidence/test |
|---|---|---|
| GET /health, /ready | Drain readiness 503; database readiness retained | http-lifecycle; existing readiness checks |
| POST /auth/register-device, /auth/register-device-v2 | Existing proof/recovery/epoch guards retained | identity-ownership, device-recovery |
| POST /auth/refresh-token | Serialized rotation; exact expiry; reuse invalidates access epoch and leases | backend-audit; integration/backend-audit |
| GET /auth/me | Existing database-authoritative entitlement/block checks | auth-guards |
| GET /auth/recovery/status, PUT /auth/recovery/pin, POST /auth/recover-device | Same identity schema as registration; PIN ownership/limits retained | device-recovery, identity-ownership |
| GET /servers | Public-only shared catalog, two-second TTL; per-request premium filtering; health cutoff | server-catalog, server-load-response |
| POST /vpn/connect, /vpn/disconnect | Confirmed peer/lease semantics retained; fair expiry worker | peer-provisioning, lease-cleanup; integration/provisioning |
| POST /billing/rewarded/prepare, /claim | Existing signed-event ledger/device binding retained | reward-claims, reward-verification |
| GET /billing/rewarded/ssv | Invalid signatures 400; transient provider/storage failures 503 | ssv-status |
| POST /billing/rtdn | Existing Google OIDC/provenance reconciliation retained | purchase-reconciliation |
| POST /billing/verify | Bounded input + per-device 10/minute quota | backend-audit, http-lifecycle |
| POST /promos/redeem | Existing atomic usage and one-use-per-campaign retained | promotions, promo-recreation; integration/backend-audit |
| POST /server-requests | Existing owned transactional cooldown retained | console-rules |
| POST /admin/login | Atomic once-per-step TOTP and MFA-version bearer | backend-audit, auth-guards |
| GET /admin/me, /stats, /export/stats | Role checks retained; UTC serialized aggregate writer | auth-guards, backend-audit |
| GET, POST /admin/servers; PUT, DELETE /admin/servers/:id | Metadata edits avoid probe; network edits invalidate health; catalog invalidated | node-admin-consistency |
| POST /admin/servers/:id/ping, /sync-public-key; PUT /admin/servers-order | Existing key/endpoint/role checks retained; catalog invalidated | node-admin-consistency, e2e-validation |
| GET /admin/devices, /devices/:installationId/details | Existing paged/search/role checks retained | console-rules |
| PUT /admin/devices/:id/tier, /block | Blocking uses locked current+pending-key disconnect | node-admin-consistency, peer-provisioning |
| GET, PUT /admin/config | Optional expectedUpdatedAt optimistic concurrency; single-flight config cache | backend-audit, node-admin-consistency |
| GET /config | In-flight stale results cannot refill invalidated cache | backend-audit |
| GET /legal/:type | Unknown type returns 404, privacy/terms unchanged | backend-audit |
| GET, POST /admin/promos; POST /admin/promos/bulk | Deleted-text recreation under existing promo lock | promo-recreation |
| GET /admin/promos/:id/code, /redemptions; PUT, DELETE /admin/promos/:id | Archive deleted hash, preserve history and granted VIP | promo-recreation; integration/backend-audit |
| GET /admin/server-requests; PUT, DELETE /admin/server-requests/:id | Existing pagination/validation/roles retained | console-rules |
| GET /admin/audit-logs | Existing SUPER_ADMIN pagination and safe audit records retained | console-rules |
| GET /admin/mfa/status (new) | Authenticated own-account enabled/enrollmentConfigured | adminMfa route + auth-guards |
| POST /admin/mfa/enrollment (new) | Password reauth; encrypted ten-minute setup; 201 data.secret/authenticatorUri/expiresAt only to owner | backend-audit |
| PUT /admin/mfa/enable (new) | password/code; success + token; same code consumed immediately | backend-audit; integration/backend-audit |

New/specified errors: MFA_SESSION_REQUIRED (401); TOTP_REQUIRED (401 login); REAUTHENTICATION_REQUIRED (403); TOTP_ALREADY_ENABLED (409); TOTP_ENROLLMENT_EXPIRED (410); TOTP_INVALID (400); TOTP_ENCRYPTION_NOT_CONFIGURED and MFA_UNAVAILABLE (503); MFA_RATE_LIMIT (429); CONFIG_CHANGED (409); AUTH_UNAVAILABLE (503 refresh); TOKEN_COMPROMISED/TOKEN_REVOKED/REFRESH_TOKEN_EXPIRED (401); BILLING_RATE_LIMIT (429); REWARD_VERIFICATION_UNAVAILABLE (503); LEGAL_DOCUMENT_NOT_FOUND (404); INVALID_JSON (400); PAYLOAD_TOO_LARGE (413). Existing envelopes and success shapes remain; invalid field validation keeps VALIDATION_ERROR. Purchase input maxima: packageName 255, productId 128, purchaseToken 16384. Refresh token maximum 256.

All API responses use no-store except explicitly public legal documents. Older admin clients may omit expectedUpdatedAt and retain last-write behavior; the new panel uses it. New MFA changes session authorization only after deliberate enrollment. Promo code reuse is a new campaign and may be redeemed again by old participants. No existing durations are rewritten.

This table is a code/test mapping, not a claim of successful live requests to every endpoint. Provider-positive reward/billing flows require real provider configuration; real PostgreSQL tests must pass CI. Unit tests use isolated mocks and cannot establish live network/database behavior.
