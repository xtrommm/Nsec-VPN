Complete the deferred audit fixes and remove the disconnect screen's blocking backend cleanup wait. Local DOWN is confirmed before navigation finishes; remote cleanup is separately bounded.

Adds UMP consent gating, encrypted connection restoration, active split-setting reconnects, server-side forced-version enforcement, protected agent transport validation, bounded 90-day retention, authenticated Play RTDN/receipt reconciliation, independent promo grants and Restore Purchases. Removes unreachable legacy admin UI.

Validation: backend lint/build and 149 tests; admin lint/build and five tests; agent lint/build and nine tests; deployment 23 tests plus firewall; Android source/backup checks pass. Android compile/lint and real PostgreSQL/device/Google flows require CI/operator verification.

Before rollout apply scripts/migrate-audit-followup.sql and privately configure receipt encryption. Wait for exact merged image publication, then run approve-audit-followup-schema.py before deployment. Configure AdMob privacy messages and Google authenticated RTDN; keep forceUpdate off until compatible app publication. Read docs/audit/AUDIT-FOLLOWUP.md for compatibility, legacy VIP limitations and exact commands.
