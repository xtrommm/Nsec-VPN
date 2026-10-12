#!/usr/bin/env bash
# Run from a reviewed checkout: sudo BACKEND_IPV4=... bash deployment/install-wireguard-node.sh
set -euo pipefail
umask 077
[[ $EUID == 0 ]] || { echo 'Run as root'; exit 1; }
# shellcheck source=/dev/null
source /etc/os-release
[[ $ID == ubuntu && $VERSION_ID =~ ^(20.04|22.04|24.04)$ ]] || { echo 'Supported: Ubuntu 20.04, 22.04, 24.04'; exit 1; }
: "${BACKEND_IPV4:?Set BACKEND_IPV4 to the backend egress IPv4; do not use the phone IP}"
[[ $BACKEND_IPV4 =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || { echo 'Invalid backend IPv4'; exit 1; }
WG_PORT=${WG_PORT:-51820}
AGENT_PORT=${AGENT_PORT:-51821}
[[ $WG_PORT =~ ^[0-9]+$ && $AGENT_PORT =~ ^[0-9]+$ && $WG_PORT -gt 0 && $WG_PORT -le 65535 && $AGENT_PORT -gt 0 && $AGENT_PORT -le 65535 && $WG_PORT != "$AGENT_PORT" ]] || exit 1
TASK_ROOT=$(cd "$(dirname "$0")/.." && pwd)
[[ -f $TASK_ROOT/agent/src/index.ts ]] || { echo 'Run installer from a complete reviewed repository checkout'; exit 1; }
PUBLIC_IP=$(curl -4fsS --max-time 10 https://ifconfig.me/ip)
python3 - "$PUBLIC_IP" "$BACKEND_IPV4" <<'PY'
import ipaddress,sys
for value in sys.argv[1:]:
    if not ipaddress.IPv4Address(value).is_global: raise SystemExit('A public IPv4 is required')
PY
MAIN_IFACE=$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="dev") {print $(i+1);exit}}')
[[ $MAIN_IFACE =~ ^[a-zA-Z0-9_.:-]{1,15}$ ]] || { echo 'Cannot determine uplink interface'; exit 1; }
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y wireguard iptables curl ca-certificates openssl python3 qrencode dnsutils
if ! command -v node >/dev/null || [[ $(node -p 'Number(process.versions.node.split(".")[0])') -lt 20 ]]; then
  curl -fsSL https://deb.nodesource.com/setup_22.x -o /tmp/nsec-nodesource-setup.sh
  bash /tmp/nsec-nodesource-setup.sh
  apt-get install -y nodejs
fi
cat > /etc/sysctl.d/99-wireguard-nsec.conf <<'SYSCTL'
net.ipv4.ip_forward = 1
SYSCTL
sysctl -p /etc/sysctl.d/99-wireguard-nsec.conf >/dev/null
mkdir -p /etc/wireguard /opt/nsec-agent /var/lib/nsec-agent
chmod 700 /etc/wireguard /var/lib/nsec-agent
# Preserve interface, keys, peers, hooks and existing config on reruns.
if [[ ! -f /etc/wireguard/wg0.conf ]]; then
  wg genkey > /etc/wireguard/server_private.key
  wg pubkey < /etc/wireguard/server_private.key > /etc/wireguard/server_public.key
  {
    printf '[Interface]\nAddress = 10.8.0.1/24\nListenPort = %s\nPrivateKey = ' "$WG_PORT"
    cat /etc/wireguard/server_private.key
    printf 'MTU = 1420\nSaveConfig = true\n'
    printf 'PostUp = /usr/local/sbin/nsec-wg-firewall up %%i\nPostDown = /usr/local/sbin/nsec-wg-firewall down %%i\n'
  } > /etc/wireguard/wg0.conf
else
  echo 'Existing wg0.conf retained. Firewall helper is applied below; review old hooks separately.'
  if ip link show wg0 >/dev/null 2>&1; then wg-quick save wg0; fi
fi
# Scoped NAT; insertion precedes existing reject rules. Delete only our exact rules.
cat > /usr/local/sbin/nsec-wg-firewall <<'FIREWALL'
#!/usr/bin/env bash
set -euo pipefail
mode=$1; iface=$2
[[ $mode =~ ^(up|down)$ && $iface =~ ^[a-zA-Z0-9_-]{1,15}$ ]] || exit 1
umask 077
state=${NSEC_WG_FIREWALL_STATE_DIR:-/run/nsec-wg-firewall}
mkdir -p "$state"; chmod 700 "$state"
if [[ $mode == down && -s $state/$iface.state ]]; then
  { read -r uplink; read -r subnet; } < "$state/$iface.state"
else
uplink=$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="dev") {print $(i+1);exit}}')
subnet=$(ip -4 -o addr show dev "$iface" | awk '{print $4;exit}')
subnet=$(python3 - "$subnet" <<'PY'
import ipaddress,sys
print(ipaddress.ip_network(sys.argv[1],strict=False))
PY
)
[[ $uplink =~ ^[a-zA-Z0-9_.:-]{1,15}$ && $subnet =~ ^[0-9./]+$ ]] || exit 1
printf '%s\n%s\n' "$uplink" "$subnet" > "$state/$iface.state"
fi
rule() {
  table=$1; chain=$2; shift 2
  if [[ $mode == up ]]; then iptables -t "$table" -C "$chain" "$@" 2>/dev/null || iptables -t "$table" -I "$chain" 1 "$@"
  elif iptables -t "$table" -C "$chain" "$@" 2>/dev/null; then iptables -t "$table" -D "$chain" "$@"
  fi
}
rule filter FORWARD -i "$iface" -o "$uplink" -j ACCEPT
rule filter FORWARD -i "$uplink" -o "$iface" -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
rule nat POSTROUTING -s "$subnet" -o "$uplink" -j MASQUERADE
if [[ $mode == down ]]; then rm -f "$state/$iface.state"; fi
FIREWALL
chmod 700 /usr/local/sbin/nsec-wg-firewall
chmod 600 /etc/wireguard/wg0.conf
systemctl enable --now wg-quick@wg0
/usr/local/sbin/nsec-wg-firewall up wg0
cat > /etc/systemd/system/nsec-wg-firewall.service <<'UNIT'
[Unit]
Description=Nsec WireGuard forwarding firewall
Requires=wg-quick@wg0.service
After=wg-quick@wg0.service
Before=nsec-agent.service
[Service]
Type=oneshot
ExecStart=/usr/local/sbin/nsec-wg-firewall up wg0
RemainAfterExit=yes
[Install]
WantedBy=multi-user.target
UNIT
# Host firewall restrictions persist with a dedicated service; unrelated ports/SSH untouched.
cat > /usr/local/sbin/nsec-agent-firewall <<RULES
#!/usr/bin/env bash
set -euo pipefail
iptables -N NSEC_AGENT 2>/dev/null || true
iptables -F NSEC_AGENT
iptables -A NSEC_AGENT -i lo -j ACCEPT
iptables -A NSEC_AGENT -s $BACKEND_IPV4/32 -j ACCEPT
iptables -A NSEC_AGENT -j DROP
iptables -C INPUT -p tcp --dport $AGENT_PORT -j NSEC_AGENT 2>/dev/null || iptables -I INPUT 1 -p tcp --dport $AGENT_PORT -j NSEC_AGENT
iptables -C INPUT -p udp --dport $WG_PORT -j ACCEPT 2>/dev/null || iptables -I INPUT 1 -p udp --dport $WG_PORT -j ACCEPT
RULES
chmod 700 /usr/local/sbin/nsec-agent-firewall
cat > /etc/systemd/system/nsec-agent-firewall.service <<'UNIT'
[Unit]
Description=Nsec scoped node firewall
Before=nsec-agent.service
After=network-online.target
[Service]
Type=oneshot
ExecStart=/usr/local/sbin/nsec-agent-firewall
RemainAfterExit=yes
[Install]
WantedBy=multi-user.target
UNIT
# Build the single authoritative agent source, no embedded second implementation.
cp "$TASK_ROOT/agent/package.json" "$TASK_ROOT/agent/package-lock.json" "$TASK_ROOT/agent/tsconfig.json" /opt/nsec-agent/
cp -r "$TASK_ROOT/agent/src" /opt/nsec-agent/
(cd /opt/nsec-agent && npm ci --no-audit --no-fund && npm run build)
if [[ ! -f /etc/nsec-agent.env ]]; then
  if [[ -z ${AGENT_SECRET:-} ]]; then read -r -s -p 'Agent shared credential (at least 32 characters; input hidden): ' AGENT_SECRET; printf '\n'; fi
  [[ ${#AGENT_SECRET} -ge 32 && $AGENT_SECRET =~ ^[A-Za-z0-9_-]+$ ]] || { echo 'Use a random alphanumeric/hex credential of at least 32 characters'; exit 1; }
  printf 'AGENT_SECRET=%s\nAGENT_PORT=%s\nAGENT_BIND=0.0.0.0\nWG_INTERFACE=wg0\n' "$AGENT_SECRET" "$AGENT_PORT" > /etc/nsec-agent.env
fi
chmod 600 /etc/nsec-agent.env
cat > /etc/systemd/system/nsec-agent.service <<'UNIT'
[Unit]
Description=Nsec WireGuard node agent
Requires=wg-quick@wg0.service nsec-agent-firewall.service nsec-wg-firewall.service
After=network-online.target wg-quick@wg0.service nsec-agent-firewall.service
[Service]
Type=simple
User=root
EnvironmentFile=/etc/nsec-agent.env
WorkingDirectory=/opt/nsec-agent
ExecStart=/usr/bin/node /opt/nsec-agent/dist/index.js
Restart=on-failure
RestartSec=5
UMask=0077
PrivateTmp=true
ProtectHome=true
NoNewPrivileges=true
[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now nsec-wg-firewall
systemctl enable --now nsec-agent-firewall
systemctl restart nsec-agent-firewall
systemctl enable nsec-agent
systemctl restart nsec-agent
printf 'WireGuard endpoint: %s:%s\nNode public key: ' "$PUBLIC_IP" "$(wg show wg0 listen-port)"
wg show wg0 public-key
printf 'Agent URL: http://%s:%s (backend source restricted)\n' "$PUBLIC_IP" "$AGENT_PORT"
echo 'Credential retained in root-only /etc/nsec-agent.env; never paste it into chat.'
echo 'Configure the same credential privately in a backend environment variable. Enter its env:VARIABLE reference in the admin node form; do not store the raw credential in the database.'
echo 'Cloud Security Group: allow WireGuard UDP publicly; allow agent TCP only from backend egress IPv4.'
echo 'Prefer HTTPS/mTLS or a private management network for agent traffic before production.'
