# Real-system verification — required before release

These steps are not execution evidence. The code checks do not prove a live VPN.
Use a rebuilt APK from `fix/e2e-working-vpn`. Keep tokens, config files/QR images,
private keys, `.env`, full HTTP logs and full WireGuard dump output out of chat.
Use only public configuration, counts and pass/fail results as evidence.

## 0. Stage and migrate

Back up PostgreSQL and `/etc/wireguard` to protected local storage. Existing
initialized databases have no migration history in the original checkout:

```sh
cd backend
npx prisma migrate resolve --applied 0001_baseline
npx prisma migrate deploy
```

Mark the baseline only after confirming the existing schema matches the original
`0001_baseline/migration.sql`. For a NEW empty database, only run `migrate deploy`.
Alternatively, on an existing initialized database, run `scripts/migrate-e2e.sql`
with `psql -v ON_ERROR_STOP=1` before deployment; do not subsequently apply the
same migration independently without reconciling Prisma's migration history.
Duplicate active device/node leases cause migration to stop, without deleting
records. Reconcile live peers before continuing. Never drop a production DB.

Install agent from the reviewed checkout on the node:

```sh
sudo BACKEND_IPV4='<BACKEND_EGRESS_IPV4>' bash deployment/install-wireguard-node.sh
```

The agent credential is entered privately, not echoed. Put the same value into
a private backend environment variable; enter only its `env:VARIABLE` reference in the admin node credential field. Existing `/etc/nsec-agent.env` is retained on
reruns. This installer does not run on the backend Docker host.
Update agent FIRST, migrate DB, deploy backend/admin, then install new Android
APK (versionCode 2). Set admin minimum version to 2 with force-update only after the new APK is available to users. New registration requires device-held v2 proof; old registration returns 426 APP_UPDATE_REQUIRED. Existing valid tokens can still use legacy config fields. Updated agent rejects legacy signatures by default; a protected temporary migration may explicitly set ALLOW_LEGACY_AGENT_V1=true, then disable it immediately after backend upgrade; updated backend requires
signed agent responses and durable peer confirmation. Older agents fail closed.

## 1. Prove node independently

```sh
sudo bash scripts/node-health.sh
sudo bash scripts/manual-peer-test.sh
```

Transfer the printed **file path's contents privately** to the phone or use the
private QR image. Do not paste the config into chat. Import into the official
WireGuard app, with Nsec disconnected. Open Chrome at `https://ifconfig.me`.
Expected exit IPv4: **47.85.109.170**. Watch on the node:

```sh
sudo watch -n1 'wg show wg0 latest-handshakes; wg show wg0 transfer'
```

Handshake timestamps must become nonzero/recent and RX/TX increase when browsing.
These subcommands do not print client source endpoint IPs or private keys.
For local investigation only, observe packets (do not save captures or paste
raw source-IP output):

```sh
sudo tcpdump -ni any -q 'udp port 51820'
sudo tcpdump -ni wg0 -q 'port 53 or port 443'
sudo tcpdump -ni eth0 -q 'port 53 or port 443'
```

Official app works: this proves this node/config/phone-network combination works;
focus Nsec negotiation/config/engine. It fails: check cloud UDP ingress, mobile
network UDP restrictions, correct endpoint and matching keys. Failure does not
uniquely identify one of those causes. Clean up with the command printed by the
script. Temporary peer is not deliberately saved, but SaveConfig on shutdown
could persist it; cleanup removes it from runtime and saves the interface.

## 2. Admin → database → server list

Use current public key from `sudo wg show wg0 public-key`. Set node endpoint
`47.85.109.170:51820`, private tunnel subnet `10.8.0.0/24`, DNS `1.1.1.1,1.0.0.1`,
MTU 1420. Location/name must reflect YOUR real location, never inferred from IP.
Agent URL is the actual backend-reachable node management origin, TCP 51821.
Click **Test node**, then enable the node. Key, UDP port and resolved endpoint
must match agent evidence. Only healthy enabled nodes with recent checks appear.

Exact API shapes (tokens shown only as placeholders):

```sh
curl -fsS -H 'Authorization: Bearer <ADMIN_TOKEN>' \
  -H 'Content-Type: application/json' -X POST \
  https://api.nsecvpn.com/api/v1/admin/servers \
  --data-binary @node-registration.json
curl -fsS -H 'Authorization: Bearer <ADMIN_TOKEN>' -X POST \
  'https://api.nsecvpn.com/api/v1/admin/servers/<NODE_ID>/ping'
curl -fsS -H 'Authorization: Bearer <DEVICE_TOKEN>' \
  'https://api.nsecvpn.com/api/v1/servers?grouped=false'
```

`node-registration.json` is a root/user-private local file containing real
`name,countryCode,countryName,city,flagEmoji,endpoint,publicKey,subnetIpv4,
dnsServers,mtu,maxPeers,agentUrl,agentSecret,active,isPremiumOnly,isRecommended`.
The agentSecret field now contains only an env:VARIABLE reference. Keep admin tokens and actual environment credentials private.
The returned server list contains no agent credential. Refresh the app and match
node ID/name/location. Do not infer deployed data from the repository.

## 3. Nsec peer and persistence

A device-generated public key is safe; its private key MUST remain on the phone.
Use only the public key from a debug APK if making this manual API call:

```sh
curl -fsS -H 'Authorization: Bearer <DEVICE_TOKEN>' \
  -H 'Content-Type: application/json' -X POST \
  https://api.nsecvpn.com/api/v1/vpn/connect \
  -d '{"serverId":"<NODE_ID>","clientPublicKey":"<DEVICE_PUBLIC_KEY>"}'
sudo wg show wg0 allowed-ips
sudo systemctl restart wg-quick@wg0
sudo wg show wg0 allowed-ips
```

API response must contain public IP/endpoint/port/current server key, /32 client
address, explicit DNS, MTU, IPv4 default route, node ID/name/location. Match
public key + /32 address on the node. **Maintenance window:** restart/reboot
interrupts active connections. After reboot, repeat allowed-ips, then connect
and check handshakes/counters again. Presence alone does not prove forwarding.

## 4. Real Android traffic/IP/DNS

Connect from Nsec. Remain Connecting until a real WireGuard handshake. Home and
Connected must show **47.85.109.170**, actual node name and location. In debug
only, inspect tunnel /32, handshake age and real byte counters. Chrome must
independently show the same public IPv4 while generating traffic.

Run browser DNS tests at `https://www.dnsleaktest.com` and
`https://browserleaks.com/ip`. Resolve ordinary domains and verify no ISP resolver
and no reachable public IPv6. Public-resolver test labels can differ from the
configured resolver IP because resolvers use anycast/upstream infrastructure.
Disable Chrome Secure DNS and Android Private DNS for the baseline test, then
repeat with each enabled: app-managed DoH may use a different resolver but must
still traverse the VPN for included apps. A different resolver label alone is
not an ISP-path leak. The excluded Nsec app and explicitly excluded apps use the
normal network by design; do not count their IP lookups as VPN verification.

IPv4-only mode: exactly one WireGuard peer and `0.0.0.0/0`; no `::/0` and no
`allowFamily(AF_INET6)`. Official GoBackend's full-route kill-switch semantics
block the unconfigured family. Verify on an IPv6-capable phone network.
Always-On/Block connections without VPN can conflict with excluded ad traffic;
test that combination explicitly before offering it as guaranteed behavior.

## 5. Failures and failover

On a spare/test node or maintenance window, block its WireGuard UDP temporarily:

```sh
sudo iptables -I INPUT 1 -p udp --dport 51820 -j DROP
# Test connection. Then RESTORE immediately:
sudo iptables -D INPUT -p udp --dport 51820 -j DROP
```

No false Connected state/ad. Timeout is 15 seconds per attempt, at most three
distinct eligible nodes in the same city/country. If no alternative exists,
show an error. After failover, IP/name must come from the new confirmed node.
Stop agent; API must return `AGENT_UNREACHABLE`, no config:

```sh
sudo systemctl stop nsec-agent
# Test connect API, then restore:
sudo systemctl start nsec-agent
```

Power off one test node via cloud console; verify bounded reconnect/failover.
Block a connected device from admin; live peer must disappear. After a stale
handshake (180 seconds), app clears connected-node display and reconnects with
bounded backoff. A handshake only proves peer identity, not upstream internet;
Chrome exit-IP and DNS checks are still mandatory.

## 6. Build/test commands

```sh
(cd backend && npm ci && npm run prisma:generate && npm run lint && npm test -- --runInBand && npm run build && npm audit)
(cd agent && npm ci && npm run lint && npm test && npm audit)
(cd admin-panel && npm ci && npm run lint && npm test && npm run build && npm audit)
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --no-daemon
shellcheck deployment/install-wireguard-node.sh scripts/*.sh
```

Use JDK21 + Android SDK36.1, protect your actual signing credentials, and retain
existing ad IDs/signing configuration/licenses. Never publish a debug-signed
release. Physical-device API24/34/35/36 tests must include tile, notification,
permission denial/revocation, repeated taps, airplane mode, background/process
termination, ads, actual purchases, verified rewards and split tunneling.

### PostgreSQL integration harness

CI creates a dedicated PostgreSQL 16 service and runs `prisma migrate deploy`
and `npm run test:integration`. Locally use a separate database whose name
contains `test` or `_ci`; the harness refuses production-looking names. It
proves transactional uniqueness, idempotency and rollback with a mocked agent;
it does not prove real WireGuard or cloud reachability.

## Safe output to paste back

Share node-health PASS/FAIL, admin Test-node public fields, API error codes,
assigned tunnel /32, handshake ages and aggregate RX/TX counts, Chrome public
exit IPv4, DNS/IPv6 pass/fail, and build/test errors with credentials redacted.
Never share `.env`, private keys, QR/configs, JWTs, agent credentials, purchase
receipts or raw packet captures.
