Admin toolbar checkboxes inherited form margins, narrow headers crowded actions, long IDs/modal tables overflowed, and grid gaps doubled form spacing. This update aligns shared controls, bounds and scrolls tables, wraps long content, and improves dialog focus/body scrolling in both themes without redesigning the app.

Settings drafts now survive parent refreshes and keep their original concurrency version; navigation/discard is explicit and each tab validates/saves only its fields. Superseded searches are aborted, first loads skip typing debounce, off-screen/hidden polling stops, node-only polls skip dashboard queries, and server results no longer wait for statistics. Dashboard aggregates coalesce for two seconds; ten concurrent calls make five queries instead of fifty. Node management no longer fetches agent credential references.

Validation: backend 33 suites/186 tests, panel 6 tests, type checks/builds and 23 deployment tests passed. Admin npm audit zero advisories. Browser matrix is added to CI (10 tabs × 4 widths × 2 themes), with modal/config/navigation regressions. Browser checks and live endpoint p50/p95 are not verified locally because browser downloads/live access are unavailable.

No database schema change, migration, env change, MFA rotation, Android or agent update. Existing auth guards and schema approval remain. See docs/audit/ADMIN-LAYOUT-API-PERFORMANCE.md.
