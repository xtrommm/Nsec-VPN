# Security and rotation runbook

Do not paste credentials, private keys, signed request bodies or private configs
into chat. Rotate every deployed default credential, even if its value appears
only as a former source-code default. The node key shared in chat is compromised.

## Node key (single-interface interruption is unavoidable)

The existing single wg0 identity cannot accept both the old and new server
private keys simultaneously. Claiming zero downtime here would be false.
For this node, schedule a brief maintenance window, disable it in admin, run:

```sh
sudo bash scripts/rotate-node-key.sh
sudo wg show wg0 public-key
```

Use **Sync public key from node** as SUPER_ADMIN, then **Test node**. Confirm
endpoint 47.85.109.170:51820, observed IPv4, public key and listening port match;
enable the node, reconnect phones, repeat official-app and Nsec traffic checks.
The script saves runtime peers before rotation, backs up configuration locally
under root-only permissions and writes the current key safely. Never restore the
compromised old key to service. Destroy old protected backups after recovery is
verified, including backups/chat copies outside this machine.

For uninterrupted service, first provision an independent healthy replacement
node in the same location. Migrate clients using failover, rotate the old node
while disabled, verify it and return it to service. Existing tunnel migration
is still a reconnect; a seamless dual-identity tunnel needs a larger design.

## JWT secret

Generate a fresh random secret privately using a trusted password manager or
`openssl rand -hex 32 > /root/nsec-jwt-next` (umask 077). Transfer it into the
backend environment through your secret manager. Restart the backend. This
implementation has ONE active HS256 signing key; changing it invalidates all
access tokens. Hashed refresh tokens remain available to obtain new access
JWTs. Test refresh and expired-token recovery before the maintenance window.
Do not claim uninterrupted JWT-key rotation without an explicit previous-key
verification window. Revoke compromised refresh credentials per device as well.
Raw legacy agent credentials in ServerNode must be migrated to env:VARIABLE references before enabling nodes on this backend. Configure the actual credential privately in that backend environment variable; new backend refuses raw DB credentials. Rotate database/admin/bootstrap credentials if former defaults were deployed.
Enable TOTP for administrators through a reviewed enrollment process before
publishing; login enforcement is implemented, enrollment UI is not implemented.

## Agent credential

Disable one node, pause peer provisioning to it, generate a new random >=32-byte
credential, update root-only /etc/nsec-agent.env locally and restart nsec-agent.
Update the matching private backend environment variable and restart backend; keep only its env:VARIABLE reference in the admin node credential field.
Test node, then re-enable it. Never print the environment file. Installer reruns
retain it; they do not silently rotate it. No dual-key credential window exists;
use another healthy node to cover the brief management-channel interruption.

## Management channel and no-logs

Request/response HMAC covers method, path, timestamp and body; replay and expired
requests are rejected. Response integrity is checked before config is returned.
Restrict TCP51821 in BOTH Alibaba Security Group and host firewall to the
backend egress IPv4. Prefer a private management network or a verified HTTPS
reverse proxy with access/error request logs disabled; HTTP does not provide
confidentiality. Certificate issuance/management topology is an operator choice,
not silently fabricated here. Do not expose agent TCP to 0.0.0.0/0.

Disable source-IP request logs in cloud load balancers, CDN, reverse proxies and
external log aggregation too. Repository changes cannot verify those systems.
Kernel WireGuard necessarily holds the current peer endpoint, handshakes and
counters in memory. Persistent expiring public-key/address leases are required
for reboot recovery; auth/subscription/admin-audit records are also stored.
No browsing payloads, DNS queries, source client IPs or packet captures should
be persisted. Audit logs record admin actions and field names, never secrets.
Ads and billing providers process data under their own policies; absolute
claims that no information is shared with any third party would be inaccurate.

## DNS / IPv6 policy

The implemented node mode uses real public DNS resolvers, explicit IPv4-only
routes and GoBackend's blocking of unconfigured address families. It does not
add ::/0 to an IPv4-only node. Private DNS addresses are rejected until a local
resolver has actually been installed and its reachability verified. Apps can
use DoH over their tunneled HTTPS connection; resolver preference cannot force
all third-party apps to stop using DoH. Included apps must still exit the VPN.
Explicitly excluded apps (including Nsec for ads) intentionally use normal
network DNS/IPv6 and are outside this leak guarantee. Verify from another app.

Optional future local Unbound: bind only wg0 10.8.0.1, restrict recursion to
10.8.0.0/24, disable query logging, use validated DNS-over-TLS upstreams, add
interface-bound UDP/TCP53 firewall rules, probe resolution through WireGuard,
then add an explicit validated private-resolver capability to the node model.
This is proposed, not silently configured or reported as working.

Enabled admin TOTP also requires an `env:VARIABLE` reference in the legacy `totpSecret` column. Move each existing authenticator seed privately to its own backend environment variable, replace the column with its reference, and restart the backend. Raw stored seeds fail closed; do not disable 2FA to bypass this migration. New enrollment/recovery remains an operator prerequisite.
