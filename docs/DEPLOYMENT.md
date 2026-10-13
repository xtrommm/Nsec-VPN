# Deployment of the repair candidate

For CloudPanel manually upgraded on 2026-10-12, follow
[GitHub website deployment](GITHUB-WEBSITE-DEPLOYMENT.md). Its private Compose
model and encrypted agent channel supersede the old generated deployment files.
Install the restricted v2 handler before merging into automatic deployment.

Release is NO-GO until the [repair report](E2E-REPAIR-REPORT.md) acceptance gates pass.
Do not run an unreviewed installer piped from main. Review and check out the repair
branch or apply the source package to its documented base commit first.

1. Back up the production database privately. Compare the baseline migration to
   the existing schema before marking it applied; do not recreate populated tables.
   Review `scripts/migrate-e2e.sql`, resolve duplicate active peer rows, then migrate.
2. Set BACKEND_ENV_FILE to a protected file with actual per-node, billing and reward environment values; Compose loads it into the backend. Supply backend secrets through protected environment variables: JWT signing,
   bootstrap admin credentials, each node's agent credential, and enabled TOTP
   seeds. `agentSecretHash` stores an env:VARIABLE reference. Legacy `totpSecret` references remain supported; new authenticator enrollment stores AES-GCM ciphertext encrypted with the dedicated ADMIN_TOTP_ENCRYPTION_KEY. See docs/audit/BACKEND-ADMIN-ROLLOUT.md before enabling it.
   Privately migrate legacy raw credentials. Never paste environment contents.
3. Install/build/test the authoritative agent source and run the reviewed checkout:
   `sudo bash deployment/install-wireguard-node.sh`. Supply the real backend egress
   IPv4 and agent credential privately when requested. Existing WireGuard identity
   and peers are preserved; rotate the compromised node key separately.
4. Open Alibaba inbound UDP51820 publicly. Restrict management TCP51821 to the
   actual backend source IP. Use a private management network or a verified HTTPS
   proxy; HMAC does not encrypt HTTP. Preserve SSH recovery access.
5. Run `sudo bash scripts/node-health.sh` and the independent official-WireGuard
   test in `scripts/manual-peer-test.sh`. Store client config/QR privately.
6. Deploy agent first, then migrations/backend/admin and APK. Perform the explicit
   v2 device-registration/version switch described in `scripts/verify-system.md`.
   New clients fail clearly against old servers; old unproved registration receives
   APP_UPDATE_REQUIRED. Configure real Play Billing and AdMob SSV credentials.
7. Enter only the actual current node public key, public endpoint and credential
   reference. Run admin Test node and explicit public-key sync. Enable a node only
   after its health and real phone traffic pass.
8. Complete `scripts/verify-system.md`, including reboot persistence, DNS/IPv6,
   revoked peer, outages, failover and Chrome exit-IP checks before publication.

Never include GitHub tokens in command arguments or downloaded shell pipelines.
Use an authenticated git credential helper or copy the reviewed source privately.
Keep service environment/lease files root-readable and disable request/access logs
in every external proxy/CDN as well as the supplied configuration. Confirm the
production signing key and ad IDs without changing them as part of this repair.
