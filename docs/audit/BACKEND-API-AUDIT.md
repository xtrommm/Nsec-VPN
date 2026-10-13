# Backend/API Audit — Phase 1

Audited main: `7ba3f3d` (PR #24 merged). Production backend modules, routes, schemas, middleware and services reviewed before code edits. Baseline: TypeScript lint passed; 25 suites/149 tests passed; npm audit reported zero advisories across 522 dependencies.

| ID | Severity | Baseline evidence | Root cause | Planned fix | Verification |
|---|---|---|---|---|---|
| B01 | High | backend/src/routes/admin.ts:340-341 | Device blocking deletes tracking after removing only the current key | Use locked disconnectPeer; retain both current/pending keys until confirmed removal | Pending-key removal/failed-cleanup regression |
| B02 | Medium | backend/src/routes/promos.ts:120-126; backend/prisma/schema.prisma:210 | Deleted campaign retains globally unique code hash | Archive hash on delete and on create for legacy tombstones under the promo lock; preserve redemptions | Delete/recreate and concurrent-create database tests |
| B03 | Medium | backend/src/services/totp.ts:2-6; backend/src/routes/admin.ts:89 | Authenticator verification exists without enrollment API/control | Add password-reauthenticated encrypted enrollment and confirmation; minimal admin Security control required by new contract | Enrollment/password/expiry/role tests |
| B04 | High | backend/src/services/totp.ts:12-18 | Same authenticator step can authenticate multiple times | Consume accepted step atomically with a persisted last-step field | Concurrent/replayed authenticator test |
| B05 | High | backend/src/middleware/auth.ts:111-118 | A pre-enrollment admin bearer remains valid after enabling MFA | Require MFA seed-version claim for enabled accounts; issue it only after valid MFA login | Old bearer rejected/current MFA bearer accepted |
| B06 | High | backend/src/routes/auth.ts:152-156 | Refresh reuse revokes refresh rows but not still-valid access tokens | Rotate device auth epoch under the registration/recovery identity lock on proven reuse | Reuse revokes access and refresh credentials |
| B07 | Medium | backend/src/routes/auth.ts:134-196 | Refresh reads before its conditional transaction; concurrent losing update surfaces generic 500; exact-expiry comparison is strict less-than | Serialize/re-read rotation and use expiry <= now with specific terminal codes | Concurrent rotation and exact-expiry tests |
| B08 | Medium | backend/src/types.ts:109-113; backend/src/routes/billing.ts:60 | Billing input has no maximum length or per-device verification quota | Bound receipt/product/package fields and apply device quota | Oversize and abuse negative tests |
| B09 | Medium | backend/src/routes/billing.ts:40-48 | Temporary verifier-key/DB failure is returned as permanent 400 | Separate invalid signed event from transient verifier/storage 503 | Dependency failure and bad-signature status tests |
| B10 | Medium | backend/src/routes/config.ts:11-14,60-61 | In-flight old config read can repopulate cache after invalidation; cache misses duplicate DB reads | Generation-bound single-flight reads; never cache a pre-invalidation result | Invalidation-race/coalescing tests |
| B11 | Medium | backend/src/services/peerProvisioning.ts:123-127 | Repeated unordered first 100 cleanup candidates can starve other expired reservations | Stable rotating keyset cursor while keeping confirmed-removal semantics | Unreachable-first-batch fairness regression |
| B12 | High | backend/src/index.ts:100-125 | Bootstrap errors are swallowed after HTTP listener starts | Finish serialized bootstrap before listening and fail startup safely on missing first-admin config | Bootstrap outage/missing-config/race tests |
| B13 | Medium | backend/src/index.ts:127-149 | Intervals continue during shutdown and graceful drain has no deadline | Stop schedules, reject new readiness during shutdown and bound drain | Shutdown/scheduler unit checks |
| B14 | Low | backend/src/services/stats.ts:10-33 | Aggregate day uses server-local midnight and read/create can split rows concurrently | UTC day plus serialized aggregate writes | UTC boundary and concurrent counter tests |
| B15 | Low | backend/src/routes/config.ts:76-105 | Unknown legal document type returns terms with 200 | Explicit privacy/terms allowlist with 404 for unknown types | Unknown-document negative test |
| B16 | Medium | backend/src/routes/servers.ts:8; backend/src/routes/admin.ts:263 | Authenticated metadata responses lack uniform no-store policy | Set no-store on API responses; retain explicit public legal-document cache override | Cache header contract test |
| B17 | Low | backend/src/services/deviceRecovery.ts:11 | Recovery identity validation differs from registration format | Reuse the authoritative installation-ID schema | Malformed/legacy identity tests |
| B18 | Medium | backend/src/routes/admin.ts:184-197 | Any node edit clears health and reruns agent/DNS probes, including VIP/label changes | Invalidate/probe only when actual health-sensitive fields change | VIP-only edit retains health, endpoint edit revalidates |

## Scope and verification limits

No live administration/session credentials or database/server execution access are available here. Live per-endpoint p50/p95, provider TLS/log settings and runtime database privileges are therefore unmeasured, not presumed passing. Docker deployment currently initializes PostgreSQL using the same configured database owner as application traffic; least-privilege runtime-role migration requires operator execution on the live database. Migration history, partial peer uniqueness and rollout schema approval remain mandatory. Existing no-source-IP/access logging settings are retained. No VPN/mobile changes are planned. Only a minimal admin authenticator control is needed to consume the new enrollment API, and will be explicitly reported.

## Ruled out / retained safeguards

Device/admin routes already use auth guards; server/promo/config mutation roles are enforced server-side. Promo redemption has per-device/per-code database advisory locks, unique per-campaign/device redemption and atomic usage/entitlement changes. Recovery is PIN-authenticated, throttled and auth-epoch revoking. Agent requests and responses are authenticated; uncertain reservations are durable. Google reward signatures, ad-unit binding and transaction replay ledger are present. Play notifications query authoritative Google status, and purchase/protected-grant reconciliation preserves unrelated VIP. Request owner/status are server-controlled with an existing transactional cooldown. Paged devices/promos/requests/audit queries and node health overlap guards exist. No claim of universally bug-free behavior is made.

## Final implementation status

The user subsequently authorized a full admin reskin; it is included without Android/VPN logic changes. Every B01–B18 source finding above has an implemented fix and regression coverage. See BACKEND-API-CHANGES.md for per-endpoint mappings and BACKEND-ADMIN-ROLLOUT.md for deployment and operator checks.

Additional findings discovered during implementation:

| ID | Severity | Baseline evidence | Cause and fix | Status / proof |
|---|---|---|---|---|
| B19 | Medium | backend/src/routes/admin.ts:384-406 (baseline) | Full configuration editors overwrite concurrent updates; optional expectedUpdatedAt checked under config lock, scoped panel saves | Fixed; node-admin-consistency stale-save test |
| B20 | Low | backend/src/index.ts:95-100 (baseline) | Parser failures mislabeled internal errors; safe 400/413 codes | Fixed; http-lifecycle |
| B21 | Medium | backend/src/services/nodeHealth.ts:18,62-68 (baseline) | Unbounded DNS and chunk head-of-line waits; 2s DNS deadline, 8 independent workers, safe partial-failure warning | Fixed; node-health-workers deadline/independent-worker tests; live many-node timing unmeasured |
| B22 | Medium | backend/src/routes/servers.ts:24-32 (baseline) | Repeated public catalog DB queries; public-only two-second single-flight cache with mutation invalidation | Fixed; server-catalog and cross-tier server-load-response tests |
| B23 | Low | backend/tsconfig.json:include; backend/package.json:scripts.build (baseline) | Tests compiled into production distribution; separate build config and clean dist before build | Fixed; clean build emits no __tests__/test modules |
| B24 | Low | admin-panel/src/components/ControlSettings.tsx:reward help text (baseline) | Help claimed unverified client callback grants benefit; accurate server-verification text | Fixed; panel source/build |
| B25 | Medium | admin-panel/nginx.conf:1-8 (baseline) | No panel CSP; self-only scripts/connect, local QR data images, no framing/object content, nosniff/referrer headers | Fixed in source; operator must verify deployed proxy headers |
| B26 | Medium | deployment/docker-compose.yml:postgres/backend environment (baseline) | Owner credentials can also be runtime credentials; CRUD-only role SQL + protected switch/rollback instructions supplied | Operator action required: runtime credentials/privileges unavailable here |

### Validation and measurable results

Baseline backend 25 suites/149 tests; final backend test totals are recorded in the delivery PR body. TypeScript checking and production build passed, as did panel checking, tests and Vite build. npm dependency scans returned zero advisories for backend and panel. A targeted current/history secret scan found no current credential literal/private-key pattern in the scanned source. Historical fallback defaults exist in backend/src/index.ts, backend/src/middleware/auth.ts, backend/src/routes/admin.ts and backend/src/routes/auth.ts; paths/blob/line metadata are in BACKEND-SECRET-SCAN.json, values are never disclosed. Confirm rotation of any deployment that used those defaults (JWT and bootstrap admin password). Previous node private-key rotation remains required if not already completed; this update does not print or rotate node keys.

Measured query/operation counts in regression tests: concurrent cold config reads coalesce to one DB query; concurrent catalog reads coalesce to one DB query, cached reads issue zero until the two-second expiry; VIP-only node edit performs zero agent probes. Real endpoint p50/p95 before/after, TLS/proxy/log retention, real DB privileges and visual browser QA were not measurable here. There is no invented latency result or claim of end-to-end security verification. The read-only benchmark and real-PG CI harness are supplied for those checks.

### Privacy and security scope

Stored data remains installation IDs, optional device/app metadata, entitlement/expiry, peer public keys/tunnel leases, encrypted purchase receipts/promo text, server requests, administrator identity and safe action audits. No source-IP, browsing content or DNS query storage was added. Enrollment stores encrypted authenticator seed and last consumed counter; audit actions do not contain the seed/password/code. Request country/city/note can contain user-supplied personal information; deletion remains available and retention is an operator policy. External CloudPanel/Cloudflare/Google/provider logs, backups, secret access permissions and retention must be checked by the owner. Existing HTTPS/CORS/proxy allowlists, Google verification, peer reservations and IDOR guards were retained; this report does not certify external infrastructure.

### Remaining verification, not skipped code fixes

Real PostgreSQL integration tests (promo contention, refresh reuse, TOTP contention) require a test DB; none was available locally. They are wired into the existing CI integration glob. Browser download was unavailable, so responsive/theme/authenticator QR layouts require the supplied manual checks. Provider-positive billing/rewards and live endpoint/network tests require owner credentials and devices. These are concrete access limits, not claimed passing. No guarantees of a bug-free system or future zero errors are made.

### Ranked follow-on hardening

| Improvement | Value | Effort | Risk | Decision |
|---|---|---|---|---|
| Generation-safe caches, bounded health jobs and scoped config updates | High | Low | Low | Implemented with regressions |
| Authenticator enrollment and replay/session protections | High | Medium | Medium | Implemented; migration/key setup required |
| Read-only timing harness and exact-schema approval | High | Low | Low | Implemented; operator executes |
| Least-privilege runtime database account | High | Low | Medium | SQL/instructions supplied; operator credential switch required |
| Shared quota store for multiple backend replicas | High at scale | Medium | Medium | Proposed; current deployment is a single backend with process-local quotas; choose Redis/service before expanding replicas |
| Live provider callback alerts, safe aggregate metrics and retention policy | High | Medium | Medium | Proposed; requires destination/retention choice; no user data or unsolicited external messages added |
| Formal backup restoration drills and encrypted secret rotation runbooks | High | Medium | Medium | Proposed; requires planned maintenance and backup retention choice |

The process-local cache invalidates immediately on the writing instance and expires within two seconds on other instances. Existing rate limits are process-local; adding replicas without a shared quota store multiplies quotas. No replica deployment was added. These are scale-dependent design choices, not hidden assertions that the current single-instance protections cover arbitrary horizontal scaling.
