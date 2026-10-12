#!/usr/bin/env bash
# Offline from Nsec components. Config contains a secret; it is never printed to logs/chat.
set -euo pipefail
umask 077
[[ $EUID == 0 ]] || { echo 'Run with sudo'; exit 1; }
iface=${WG_INTERFACE:-wg0}
[[ $iface =~ ^[a-zA-Z0-9_-]{1,15}$ ]] || exit 1
public_ip=$(curl -4fsS --max-time 10 https://ifconfig.me/ip)
python3 - "$public_ip" <<'PY'
import ipaddress,sys
if not ipaddress.IPv4Address(sys.argv[1]).is_global: raise SystemExit('Invalid public IPv4')
PY
node_key=$(wg show "$iface" public-key)
port=$(wg show "$iface" listen-port)
TASK_DIR=$(mktemp -d /root/nsec-manual-peer.XXXXXX)
wg genkey > "$TASK_DIR/client.key"
wg pubkey < "$TASK_DIR/client.key" > "$TASK_DIR/client.pub"
client_pub=$(cat "$TASK_DIR/client.pub")
address=$(python3 - "$iface" <<'PY'
import ipaddress,subprocess,sys,json
iface=sys.argv[1]
info=json.loads(subprocess.check_output(['ip','-j','-4','addr','show','dev',iface]))
a=next(x for x in info[0]['addr_info'] if x['family']=='inet')
network=ipaddress.ip_network(f"{a['local']}/{a['prefixlen']}",strict=False)
used={a['local']}
for line in subprocess.check_output(['wg','show',iface,'allowed-ips'],text=True).splitlines():
    for cidr in line.split()[1:]:
        for c in cidr.split(','):
            if c!='(none)': used.add(c.split('/')[0])
for host in network.hosts():
    if str(host) not in used:
        print(f'{host}/32');break
else: raise SystemExit('No free address')
PY
)
wg set "$iface" peer "$client_pub" allowed-ips "$address"
{
 printf '[Interface]\nPrivateKey = '; cat "$TASK_DIR/client.key"
 printf 'Address = %s\nDNS = 1.1.1.1\nMTU = 1420\n\n[Peer]\nPublicKey = %s\nEndpoint = %s:%s\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 25\n' "$address" "$node_key" "$public_ip" "$port"
} > "$TASK_DIR/client.conf"
printf '#!/usr/bin/env bash\nset -euo pipefail\nwg set %q peer %q remove\nwg-quick save %q\nrm -rf -- %q\n' "$iface" "$client_pub" "$iface" "$TASK_DIR" > "$TASK_DIR/cleanup.sh"
chmod 700 "$TASK_DIR/cleanup.sh"
echo 'TEST IN THE OFFICIAL WIREGUARD APP. Keep Nsec disconnected during this test.'
echo "Ready-to-import config: $TASK_DIR/client.conf"
if command -v qrencode >/dev/null; then qrencode -o "$TASK_DIR/client-qr.png" < "$TASK_DIR/client.conf"; echo "Private QR image: $TASK_DIR/client-qr.png"; fi
echo 'Config/QR contain a private key. Transfer locally to your phone; do not paste into chat.'
printf 'Cleanup: sudo %s/cleanup.sh\n' "$TASK_DIR"
printf "Watch: sudo watch -n1 'wg show %s'; sudo tcpdump -ni any udp port %s\n" "$iface" "$port"
echo 'Successful official-app traffic isolates Nsec software. Failure: inspect cloud UDP ingress, endpoint and key matching; other phone/network causes remain possible.'
