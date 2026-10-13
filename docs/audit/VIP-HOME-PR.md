Fix VIP access prompts and compact requested UI layouts. Previously selected premium nodes now check entitlement before tunnel start; Settings ends with Device ID; filters fill their row; speed metrics show Ping/Jitter/real Peak in one row. Home announcements are centered, dismissible live banners.

Reward confirmation polls faster after a signed grant, with explicit debug-test/delayed-callback feedback. No unauthenticated grants, backend/API/schema changes, or migrations. AdMob SSV must be configured for release rewards; callback delivery time is external.

Validation: 38 local Python tests and Kotlin syntax checks pass. Android tests/lint/build require CI because the local Gradle download is blocked. Added Robolectric regression coverage. See docs/audit/VIP-HOME-UPDATE.md for manual setup and verification.
