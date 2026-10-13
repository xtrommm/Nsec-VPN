Redesign VIP access and centered Home announcement popups with consistent themed cards and explicit close actions. Add a pinned X to Device VIP Recovery with its existing busy guard. Explain reward availability without changing grants or callbacks.

Refresh live server metadata every five seconds on the visible Servers tab, bypassing the local refresh throttle and keeping background loading silent. Free/VIP badges, filters and selected-node metadata use the existing validated mapping.

Validation: 38 local Python checks and diff checks pass. Added Android regressions; Android tests/lint/build require CI. No backend/API/schema changes, migration or deployment needed. See docs/audit/POPUPS-LIVE-SERVERS.md.
