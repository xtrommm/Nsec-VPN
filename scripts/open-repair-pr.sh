#!/usr/bin/env bash
# Requires YOUR authenticated GitHub write access. No deployment or main push.
set -euo pipefail
[[ $(git branch --show-current) == fix/e2e-working-vpn ]] || { echo 'Switch to the reviewed repair branch first'; exit 1; }
[[ -z $(git status --porcelain) ]] || { echo 'Commit reviewed branch changes before publishing'; exit 1; }
command -v gh >/dev/null || { echo 'Install GitHub CLI and authenticate privately first'; exit 1; }
git push origin HEAD:refs/heads/fix/e2e-working-vpn
gh pr create --repo xtrommm/Nsec-VPN --base main --head fix/e2e-working-vpn --draft \
  --title 'Restore real WireGuard negotiation, persistence and verified connection state' \
  --body-file docs/E2E-PR-BODY.md
