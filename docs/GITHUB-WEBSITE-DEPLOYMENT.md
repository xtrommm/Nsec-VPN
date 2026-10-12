# GitHub website deployment after the repair

The running backend/admin were manually updated on 2026-10-12. Their authoritative
private Compose file is `/root/nsec-agent-channel/compose.repair.json`. It contains
production credentials. Never print, commit or upload it. The SSH channel and
WireGuard node are separate services that a website release must preserve.

## Install before merging PR #2

Push this update on `fix/e2e-working-vpn` and let PR CI run. Fetch that branch on
CloudPanel into a separate checkout; preserve the original checkout and its local
deployment scripts. Identify the account from the existing `NSEC_DEPLOY_USER`
GitHub secret and its forced SSH deployment executable path. Keep those existing
GitHub deployment secrets: no new private key needs to be pasted into GitHub.

Set `DEPLOY_ACCOUNT` and `EXISTING_FORCED_COMMAND_PATH` to those actual values,
then run this from the reviewed checkout as root:

```bash
python3 deployment/install-website-deployer.py \
  --deploy-user "$DEPLOY_ACCOUNT" \
  --legacy-command "$EXISTING_FORCED_COMMAND_PATH"
```

The installer replaces exactly one matching forced deployment key. It preserves
Windows/other administrative keys, the agent-channel key and existing source
restrictions. It refuses missing or ambiguous matches. It installs a root-owned
release controller, limited sudo permission when needed, and a reviewed running
schema fingerprint. It does not restart applications, SSH or PostgreSQL. Keep
an existing administrative SSH session open while configuring it.

Only `deploy-v2` plus a 40-character SHA is accepted. An old, unupgraded handler
rejects the new workflow command instead of replacing the working configuration.
After installation and successful PR checks, merge through GitHub when ready to
update the live website. Do not push to main. Existing GitHub secrets remain
`NSEC_DEPLOY_SSH_KEY`, `NSEC_DEPLOY_HOST_KEY`, `NSEC_DEPLOY_HOST`, and
`NSEC_DEPLOY_USER`. Root must retain its existing permission to pull both GHCR
packages; keep registry credentials in protected Docker configuration.

## What a release does

Auto Deploy Website starts only after full **CI Pipeline** success on a main push.
Pull-request/fork runs cannot deploy. Manual dispatch requires the same successful
CI evidence, and superseded main commits are skipped. Standalone Build Images is
manual-only to avoid duplicate automatic image publications.

GitHub builds Node 22 images with the configurable 4096 MB TypeScript heap.
Environment files and test fixtures are excluded from image build contexts.
CloudPanel pulls images rather than compiling on its 2 GB host. The deployer
checks source/revision image labels and uses immutable registry digests.

Only the backend/admin images change in the current private Compose model.
Environment values, database volumes, ports and networks must remain identical
when resolved. PostgreSQL is not recreated. The agent channel is not replaced.
A root-only database backup is taken and its archive checked before a release.
Only backend/admin restart. API database readiness and admin HTTP readiness must
pass, otherwise the previous website containers are restored. This is not a
certification of phone traffic, DNS/IPv6, billing, ads or proxy no-logs settings.

A schema change stops before restart with `REVIEWED_DATABASE_MIGRATION_REQUIRED`.
This tool never resets tables, runs db push or guesses a Prisma baseline. Schema
changes require a separate reviewed migration and manual application rollout,
with backups and rollback compatibility. After that approved rollout, record the
new running schema as root before returning to automatic website releases:

```bash
umask 077
docker exec nsecvpn-backend cat /app/prisma/schema.prisma \
  | sha256sum | awk '{print $1}' \
  > /var/lib/nsecvpn-deploy/website-schema.sha256
```

Do not use this to bypass a mismatch without reviewing and applying the actual
migration. Readiness alone does not prove the complete database schema.

## Essential status commands

```bash
cat /var/lib/nsecvpn-deploy/current.env
docker ps --filter name=nsecvpn --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
curl -fsS https://api.nsecvpn.com/ready
curl -fsS -o /dev/null -w 'Admin HTTP %{http_code}\n' https://admin.nsecvpn.com/
```

`current.env` contains only the deployed image tag. Release receipts contain
commit SHA, digests and time, never client addresses or traffic. Do not display
private Compose/environment files. The temporary `/root/nsec-build.swap` was
needed only for the manual image build. Check its use and available memory before
disabling/deleting that specific file; do not disable pre-existing swap.

## Evidence and limits as of 2026-10-12

CI run 39 passed all five jobs at d77734e, with no debug/release lint issues.
An upstream Android Gradle Plugin deprecation warning remained. These new website
deployment changes have targeted local checks; their new GitHub CI run and live
restricted-handler installation still require completion.

The official WireGuard app exited through 47.85.109.170 with a handshake and
rising byte counters. The compromised node key was rotated. Agent health matched
the rotated key; signed responses passed the pinned SSH channel after binding
the agent to localhost. CloudPanel port 51821 belonged to another process, so
the channel uses the existing Docker gateway on port 51823. The unrelated process
was not stopped; ownership/exposure remains a security-review item.

The additive database migration committed, the existing Virginia node was
updated rather than duplicated, and both website images built with the corrected
heap after temporary swap was added. The manually updated API returned READY with
its existing database. A weak JWT secret was rotated during that rollout.

The Windows Android project still needs Java/SDK setup and its APK build. Its local
app/build.gradle.kts edit is excluded from this website update. No repaired Nsec
phone handshake/exit-IP result has been supplied. Broader device/security tests
were deferred by the owner. Google Play publication remains NO-GO until the
remaining phone, DNS/IPv6, failure, billing/ads and no-logs acceptance items pass.
