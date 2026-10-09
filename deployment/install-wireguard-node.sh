#!/usr/bin/env bash
# ==============================================================================
# Nsec VPN - WireGuard Node Automated Installer
# Target OS: Ubuntu 22.04 LTS / Ubuntu 24.04 LTS
# ==============================================================================

set -euo pipefail

echo "========================================="
echo "   Nsec VPN WireGuard Node Installer    "
echo "========================================="

if [ "$(id -u)" != "0" ]; then
   echo "Error: This script must be run as root (use sudo)." 1>&2
   exit 1
fi

echo "[1/5] Updating packages and installing WireGuard & iptables..."
apt-get update -y
apt-get install -y wireguard qrencode iptables ufw curl

echo "[2/5] Enabling IPv4 and IPv6 packet forwarding in sysctl..."
cat << 'EOF' > /etc/sysctl.d/99-wireguard-nsec.conf
net.ipv4.ip_forward = 1
net.ipv6.conf.all.forwarding = 1
EOF
sysctl -p /etc/sysctl.d/99-wireguard-nsec.conf

echo "[3/5] Generating WireGuard server cryptographic keypair..."
mkdir -p /etc/wireguard
cd /etc/wireguard
umask 077
if [ ! -f server_private.key ]; then
    wg genkey | tee server_private.key | wg pubkey > server_public.key
fi

SERVER_PRIVKEY=$(cat server_private.key)
SERVER_PUBKEY=$(cat server_public.key)
MAIN_IFACE=$(ip route show default | awk '{print $5}' | head -n 1)

echo "[4/5] Creating /etc/wireguard/wg0.conf configuration..."
cat << EOF > /etc/wireguard/wg0.conf
[Interface]
Address = 10.8.0.1/24
ListenPort = 51820
PrivateKey = ${SERVER_PRIVKEY}
SaveConfig = false

# IP Masquerading NAT routing rules
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ${MAIN_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ${MAIN_IFACE} -j MASQUERADE
EOF

echo "[5/5] Enabling and starting WireGuard systemd service..."
systemctl enable wg-quick@wg0
systemctl restart wg-quick@wg0

PUBLIC_IP=$(curl -s4 ifconfig.me || curl -s4 icanhazip.com || echo "YOUR_SERVER_IP")

echo ""
echo "=========================================================="
echo "    WireGuard Node Configured Successfully!"
echo "=========================================================="
echo "Public IP / Endpoint: ${PUBLIC_IP}:51820"
echo "WireGuard Public Key: ${SERVER_PUBKEY}"
echo "=========================================================="
echo "Copy the Public Key and Endpoint into your Nsec VPN Admin Panel"
echo "under 'Add New Server' to immediately activate this node."
echo "=========================================================="
