#!/usr/bin/env bash
set -euo pipefail
: "${API_BASE:?Set API_BASE to https://api.nsecvpn.com/api/v1}"
: "${DEVICE_TOKEN:?Set DEVICE_TOKEN locally; never paste it into chat}"
[[ $API_BASE == https://* ]] || { echo 'HTTPS required'; exit 1; }
# Credentials go over stdin to curl; no token in process arguments or script output.
printf 'header = "Authorization: Bearer %s"\n' "$DEVICE_TOKEN" |
 curl --config - --fail --silent --show-error "$API_BASE/servers?grouped=false" |
 python3 -c 'import json,sys; d=json.load(sys.stdin); assert d["success"]; print(json.dumps(d["data"]["servers"],indent=2))'
