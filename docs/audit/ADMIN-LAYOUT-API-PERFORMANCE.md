# Admin alignment and API performance follow-up

Base: merged release 300a0e6. Android, WireGuard, auth and entitlement policy are unchanged.

| Finding | Source evidence | Fix |
|---|---|---|
| Checkbox toolbar vertically offset | admin-panel/src/styles.css:6,11; ControlDevices.tsx:15 | Reset toolbar label margin; align fixed-height controls |
| Header controls cramped/overflowing at narrow widths | styles.css:7,16-17 | Responsive header grid, shrinkable text, wrapping mobile actions |
| Modal tables force dialog width beyond viewport | styles.css:11-12; ControlPromos.tsx:45 | Scroll table within its own wrapper; dialog remains bounded |
| Long IDs, keys, titles and statuses force card/table widths | styles.css:8,11; ControlDevices.tsx:15-17 | Min-width:0 on grids/flex children; wrap identifiers and row detail text |
| Form spacing combines label margin and grid gaps | styles.css:6,8 | Consistent explicit form grid spacing and zero nested-label margins |
| Dialog background scroll/focus can escape via links | ConsoleUI.tsx:8-20 | Body scroll lock, fuller focus selector, focus fallback and restoration |
| Off-screen dashboards still poll | App.tsx:50 | Poll overview/nodes only while visible; node-only polling skips stats; refresh on resume; skip overlapping refreshes |
| List requests are ignored but not aborted | ControlDevices/ControlPromos/ControlRequests/ControlAudit list effects | Abort superseded reads; keep stale-response guards |
| Every concurrent stats request repeats five DB queries | backend/src/services/stats.ts:49-72 | Two-second single-flight aggregate cache after auth; no credentials/identities cached |
| Settings editor overwritten on parent refresh | ControlSettings.tsx:11 | Keep dirty draft; require deliberate reload; scoped validation for active group |

Verification: local unit/type/build checks and supplied browser harness. Live p50/p95, responsive rendering and visual screenshots require browser/runtime availability. No invented latency figures. The short stats cache is intentionally eventually consistent (up to two seconds); fresh authentication still runs on every request. No migration or new environment variable is required.

## Completed checks and limits

Backend: 33 suites / 186 tests, TypeScript lint and production build passed. Panel: 6 unit tests, TypeScript lint and Vite build passed. Deployment: 23 tests passed. Admin dependency scan: zero advisories. Browser smoke checks now cover ten tabs, four viewport widths, both themes, modal bounds/scrolling, scoped config saves, dirty-draft preservation and off-screen polling. They run in CI; local browser download was blocked, so their result is not asserted here. The new Playwright dependency is development-only and never included in the shipped bundle.

Measured operation counts: ten concurrent dashboard reads now make five aggregate DB calls instead of fifty, with a two-second TTL. Server metadata renders as its request finishes instead of waiting for statistics. List first loads no longer incur the artificial 200ms search debounce; typing is still debounced and superseded reads are cancelled. These are tested code-path/query-count improvements; live endpoint p50/p95 is unmeasured. Existing public server/config caches remain intact, and authentication is never cached.

## Update and verification

Run the archive publisher with --publish in Windows PowerShell. It clones a new Git checkout directly from GitHub, imports reviewed commits and pushes only fix/admin-layout-api-performance. Your existing folders/local edits are untouched. Create the PR from the printed URL and wait for all CI jobs, including browser checks, to pass. Merge and wait for Auto Deploy Website to complete (or use Production Deploy on main after its CI/images pass).

No schema change, migration, MFA key rotation or environment change. Do not repeat previous migrations or approval scripts for this update. Existing schema approval guards remain; the Prisma schema is identical to deployed 300a0e6.

On CloudPanel, verify /ready and the OCI revision of both containers against the merged main commit. In the panel press Ctrl+Shift+R. Test at desktop and 360px mobile: toolbar checkbox/input baselines, long IDs, request dropdowns, all tabs, themes, modal table scrolling, Escape/Tab focus, and dirty settings during refresh/navigation. Test a scoped config save and one normal device/promo operation. No Android rebuild is required. If CI browser assertions fail, keep the PR unmerged and inspect the named assertion.
