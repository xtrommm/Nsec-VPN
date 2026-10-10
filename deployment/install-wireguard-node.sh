#!/usr/bin/env bash
# ==============================================================================
# Nsec VPN - WireGuard Node & Agent Automated Installer
# Supported: Ubuntu 22.04 LTS / Ubuntu 24.04 LTS
# ==============================================================================

set -euo pipefail

echo "=========================================================="
echo "      Nsec VPN WireGuard Node & Agent Installer           "
echo "=========================================================="

if [ "$(id -u)" != "0" ]; then
   echo "Error: This installer must be executed as root (use sudo)." 1>&2
   exit 1
fi

AGENT_SECRET=${1:-$(openssl rand -hex 24)}
AGENT_PORT=${2:-51821}
WG_PORT=${3:-51820}

echo "[1/6] Installing dependencies (WireGuard, iptables, curl, git)..."
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y wireguard qrencode iptables ufw curl git

# Install Node.js 20 LTS if missing
if ! command -v node >/dev/null 2>&1; then
    echo "[2/6] Installing Node.js 20 LTS for Gateway Agent Daemon..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
    apt-get install -y nodejs
fi

echo "[3/6] Configuring kernel IP forwarding in sysctl..."
cat << 'EOF' > /etc/sysctl.d/99-wireguard-nsec.conf
net.ipv4.ip_forward = 1
net.ipv6.conf.all.forwarding = 1
EOF
sysctl -p /etc/sysctl.d/99-wireguard-nsec.conf >/dev/null

echo "[4/6] Initializing WireGuard server cryptographic keys..."
mkdir -p /etc/wireguard
cd /etc/wireguard
umask 077

if [ ! -f server_private.key ]; then
    wg genkey | tee server_private.key | wg pubkey > server_public.key
fi

SERVER_PRIVKEY=$(cat server_private.key)
SERVER_PUBKEY=$(cat server_public.key)
MAIN_IFACE=$(ip route show default | awk '{print $5}' | head -n 1)

# Note: SaveConfig = true ensures peers added dynamically by the agent survive server reboots
cat << EOF > /etc/wireguard/wg0.conf
[Interface]
Address = 10.8.0.1/24
ListenPort = ${WG_PORT}
PrivateKey = ${SERVER_PRIVKEY}
SaveConfig = true

PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o ${MAIN_IFACE} -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o ${MAIN_IFACE} -j MASQUERADE
EOF

systemctl enable wg-quick@wg0
systemctl restart wg-quick@wg0

echo "[5/6] Deploying Nsec Gateway Node Agent Daemon..."
mkdir -p /opt/nsec-agent
cat << 'EOF' > /opt/nsec-agent/package.json
{
  "name": "nsec-agent",
  "version": "1.0.0",
  "main": "index.js",
  "dependencies": {
    "express": "^4.19.2",
    "helmet": "^7.1.0"
  }
}
EOF

cd /opt/nsec-agent
npm install --silent

cat << 'EOF' > /opt/nsec-agent/index.js
const express = require('express');
const helmet = require('helmet');
const { execFile } = require('child_process');
const crypto = require('crypto');
const os = require('os');
const util = require('util');

const execFileAsync = util.promisify(execFile);
const app = express();
const PORT = process.env.AGENT_PORT || 51821;
const AGENT_SECRET = process.env.AGENT_SECRET;
const WG_INTERFACE = process.env.WG_INTERFACE || 'wg0';

app.use(helmet());
app.use(express.json());

const seenSignatures = new Set();
setInterval(() => seenSignatures.clear(), 60000);

const verifyHmac = (req, res, next) => {
  const timestamp = req.headers['x-agent-timestamp'];
  const signature = req.headers['x-agent-signature'];
  if (!timestamp || !signature) return res.status(401).json({ success: false, error: 'MISSING_SIGNATURE' });

  const diff = Math.abs(Date.now() - parseInt(timestamp, 10));
  if (isNaN(diff) || diff > 60000) return res.status(401).json({ success: false, error: 'REQUEST_EXPIRED' });

  if (seenSignatures.has(signature)) return res.status(401).json({ success: false, error: 'REPLAY_DETECTED' });
  seenSignatures.add(signature);

  const payload = req.method === 'GET' ? timestamp : `${timestamp}.${JSON.stringify(req.body)}`;
  const expected = crypto.createHmac('sha256', AGENT_SECRET).update(payload).digest('hex');

  const sigBuf = Buffer.from(signature);
  const expBuf = Buffer.from(expected);
  if (sigBuf.length !== expBuf.length || !crypto.timingSafeEqual(sigBuf, expBuf)) {
    return res.status(403).json({ success: false, error: 'INVALID_SIGNATURE' });
  }
  next();
};

async function persistWireGuardState() {
  try {
    await execFileAsync('wg-quick', ['save', WG_INTERFACE]);
  } catch (_e) {}
}

app.post('/api/v1/agent/peers', verifyHmac, async (req, res) => {
  const { publicKey, allocatedIp } = req.body;
  if (!/^[A-Za-z0-9+/]{43}=$/.test(publicKey) || !/^10\.\d{1,3}\.\d{1,3}\.\d{1,3}\/32$/.test(allocatedIp)) {
    return res.status(400).json({ success: false, error: 'INVALID_INPUT' });
  }
  try {
    await execFileAsync('wg', ['set', WG_INTERFACE, 'peer', publicKey, 'allowed-ips', allocatedIp]);
    await persistWireGuardState();
    res.json({ success: true, message: 'Peer added' });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.delete('/api/v1/agent/peers', verifyHmac, async (req, res) => {
  const { publicKey } = req.body;
  if (!/^[A-Za-z0-9+/]{43}=$/.test(publicKey)) return res.status(400).json({ success: false, error: 'INVALID_KEY' });
  try {
    await execFileAsync('wg', ['set', WG_INTERFACE, 'peer', publicKey, 'remove']);
    await persistWireGuardState();
    res.json({ success: true, message: 'Peer removed' });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.get('/api/v1/agent/health', verifyHmac, async (_req, res) => {
  try {
    const { stdout } = await execFileAsync('wg', ['show', WG_INTERFACE, 'peers']);
    const peers = stdout.trim().split('\n').filter(Boolean);
    const loadPercent = Math.min(Math.round((os.loadavg()[0] / (os.cpus().length || 1)) * 100), 100);
    res.json({ online: true, interface: WG_INTERFACE, peerCount: peers.length, loadPercent });
  } catch (_e) {
    res.json({ online: false, peerCount: 0, loadPercent: 0 });
  }
});

app.listen(PORT, () => console.log(`Agent running on port ${PORT}`));
EOF

echo "[6/6] Creating systemd service unit for Agent Daemon..."
cat << EOF > /etc/systemd/system/nsec-agent.service
[Unit]
Description=Nsec VPN Node Management Agent
After=network.target wg-quick@wg0.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/nsec-agent
Environment=AGENT_PORT=${AGENT_PORT}
Environment=AGENT_SECRET=${AGENT_SECRET}
Environment=WG_INTERFACE=wg0
ExecStart=/usr/bin/node /opt/nsec-agent/index.js
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable nsec-agent
systemctl restart nsec-agent

PUBLIC_IP=$(curl -s4 ifconfig.me || curl -s4 icanhazip.com || echo "YOUR_SERVER_IP")

echo ""
echo "=========================================================="
echo "    WireGuard Node & Agent Configured Successfully!       "
echo "=========================================================="
echo "WireGuard Endpoint: ${PUBLIC_IP}:${WG_PORT}"
echo "Server Public Key : ${SERVER_PUBKEY}"
echo "Agent URL         : http://${PUBLIC_IP}:${AGENT_PORT}"
echo "Agent Shared Secret: ${AGENT_SECRET}"
echo "=========================================================="
echo "Register this node in your Admin Panel at admin.nsecvpn.com"
echo "under 'Add New Node' using the values printed above."
echo "=========================================================="
