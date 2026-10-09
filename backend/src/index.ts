import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import jwt from 'jsonwebtoken';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 4000;
const JWT_SECRET = process.env.JWT_SECRET || 'nsec_super_secret_jwt_key_2026';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'admin@nsecvpn.com';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'AdminSecure2026!';

app.use(cors());
app.use(express.json());

// In-Memory Data Store (with persistent defaults)
interface ServerNode {
  id: string;
  name: string;
  countryCode: string;
  countryName: string;
  city: string;
  flagEmoji: string;
  endpoint: string;
  publicKey: string;
  currentLoad: number;
  isPremiumOnly: boolean;
  isRecommended: boolean;
  active: boolean;
}

interface DeviceUser {
  id: string;
  deviceId: string;
  model: string;
  tier: 'FREE' | 'VIP';
  passExpiresAt: string | null;
  registeredAt: string;
  lastConnectedAt: string | null;
}

interface PeerSession {
  peerId: string;
  deviceId: string;
  serverId: string;
  clientIp: string;
  clientPublicKey: string;
  connectedAt: string;
}

let servers: ServerNode[] = [
  {
    id: 'us-nyc-1',
    name: 'US East (New York)',
    countryCode: 'US',
    countryName: 'United States',
    city: 'New York',
    flagEmoji: '🇺🇸',
    endpoint: 'us.nsecvpn.com:51820',
    publicKey: '8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M=',
    currentLoad: 24,
    isPremiumOnly: false,
    isRecommended: true,
    active: true
  },
  {
    id: 'de-fra-1',
    name: 'Germany (Frankfurt)',
    countryCode: 'DE',
    countryName: 'Germany',
    city: 'Frankfurt',
    flagEmoji: '🇩🇪',
    endpoint: 'de.nsecvpn.com:51820',
    publicKey: '9K6bPRw5sUrL4G7nF6oQ8tM0xZ3aV2yW5cO8nR1lQ9N=',
    currentLoad: 38,
    isPremiumOnly: false,
    isRecommended: false,
    active: true
  },
  {
    id: 'sg-sin-1',
    name: 'Singapore (VIP 10G)',
    countryCode: 'SG',
    countryName: 'Singapore',
    city: 'Singapore',
    flagEmoji: '🇸🇬',
    endpoint: 'sg.nsecvpn.com:51820',
    publicKey: '1A7cQSx6tVsM5H8oG7pR9uN1yA4bW3zX6dP9oS2mR0O=',
    currentLoad: 12,
    isPremiumOnly: true,
    isRecommended: false,
    active: true
  },
  {
    id: 'jp-tok-1',
    name: 'Japan (Tokyo VIP)',
    countryCode: 'JP',
    countryName: 'Japan',
    city: 'Tokyo',
    flagEmoji: '🇯🇵',
    endpoint: 'jp.nsecvpn.com:51820',
    publicKey: '2B8dRTy7uWtN6I9pH8qS0vO2zB5cX4aY7eQ0pT3nS1P=',
    currentLoad: 19,
    isPremiumOnly: true,
    isRecommended: false,
    active: true
  }
];

let devices: Map<string, DeviceUser> = new Map();
let activeSessions: Map<string, PeerSession> = new Map();
let nextIpOctet = 2;

let appConfig = {
  app: {
    minAppVersionCode: 1,
    latestAppVersionCode: 1,
    forceUpdate: false
  },
  announcement: {
    active: false,
    title: 'Network Maintenance Notice',
    body: 'All high-speed nodes running WireGuard 1.0 are fully operational.',
    dismissible: true
  },
  ads: {
    bannerEnabled: true,
    interstitialEnabled: true,
    interstitialIntervalSeconds: 120,
    rewardedEnabled: true
  }
};

// Authentication Middleware
const authenticateAppToken = (req: Request, res: Response, next: NextFunction) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  if (!token) return res.status(401).json({ success: false, message: 'Missing auth token' });

  jwt.verify(token, JWT_SECRET, (err, decoded: any) => {
    if (err) return res.status(403).json({ success: false, message: 'Invalid or expired token' });
    (req as any).user = decoded;
    next();
  });
};

const authenticateAdmin = (req: Request, res: Response, next: NextFunction) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  if (!token) return res.status(401).json({ success: false, message: 'Admin authentication required' });

  jwt.verify(token, JWT_SECRET, (err, decoded: any) => {
    if (err || decoded.role !== 'admin') {
      return res.status(403).json({ success: false, message: 'Forbidden' });
    }
    next();
  });
};

// ==========================================
// 1. Mobile Client REST APIs
// ==========================================

// Register anonymous device UUID
app.post('/api/v1/auth/register-device', (req: Request, res: Response) => {
  const { deviceId, deviceModel } = req.body;
  if (!deviceId) return res.status(400).json({ success: false, message: 'deviceId is required' });

  let device = devices.get(deviceId);
  if (!device) {
    device = {
      id: `dev_${Date.now()}`,
      deviceId,
      model: deviceModel || 'Android Device',
      tier: 'FREE',
      passExpiresAt: null,
      registeredAt: new Date().toISOString(),
      lastConnectedAt: null
    };
    devices.set(deviceId, device);
  }

  const accessToken = jwt.sign({ deviceId: device.deviceId, tier: device.tier }, JWT_SECRET, { expiresIn: '7d' });
  const refreshToken = jwt.sign({ deviceId: device.deviceId }, JWT_SECRET, { expiresIn: '30d' });

  res.json({
    success: true,
    data: {
      accessToken,
      refreshToken,
      device: {
        id: device.id,
        tier: device.tier,
        passExpiresAt: device.passExpiresAt
      }
    }
  });
});

// Refresh Auth Token
app.post('/api/v1/auth/refresh-token', (req: Request, res: Response) => {
  const { refreshToken } = req.body;
  if (!refreshToken) return res.status(400).json({ success: false, message: 'refreshToken required' });

  jwt.verify(refreshToken, JWT_SECRET, (err: any, decoded: any) => {
    if (err) return res.status(403).json({ success: false, message: 'Invalid refresh token' });
    const newAccessToken = jwt.sign({ deviceId: decoded.deviceId }, JWT_SECRET, { expiresIn: '7d' });
    const newRefreshToken = jwt.sign({ deviceId: decoded.deviceId }, JWT_SECRET, { expiresIn: '30d' });
    res.json({ success: true, data: { accessToken: newAccessToken, refreshToken: newRefreshToken } });
  });
});

// Fetch Active Servers List
app.get('/api/v1/servers', authenticateAppToken, (_req: Request, res: Response) => {
  const activeServers = servers.filter(s => s.active);
  res.json({
    success: true,
    data: { servers: activeServers }
  });
});

// Request WireGuard Peer Handshake Lease
app.post('/api/v1/vpn/connect', authenticateAppToken, (req: Request, res: Response) => {
  const { serverId, clientPublicKey } = req.body;
  const targetServer = servers.find(s => s.id === serverId && s.active);

  if (!targetServer) {
    return res.status(404).json({ success: false, message: 'Server node not found or inactive' });
  }

  // Allocate client IP in 10.8.0.0/24 subnet
  const clientAddress = `10.8.0.${nextIpOctet}/32`;
  nextIpOctet = (nextIpOctet % 250) + 1;

  const peerId = `peer_${Date.now()}`;
  activeSessions.set(peerId, {
    peerId,
    deviceId: (req as any).user.deviceId,
    serverId: targetServer.id,
    clientIp: clientAddress,
    clientPublicKey,
    connectedAt: new Date().toISOString()
  });

  res.json({
    success: true,
    data: {
      peerId,
      tunnelConfig: {
        serverPublicKey: targetServer.publicKey,
        serverEndpoint: targetServer.endpoint,
        clientAddress,
        dnsServers: ['1.1.1.1', '1.0.0.1'],
        allowedIps: ['0.0.0.0/0', '::/0'],
        persistentKeepalive: 25,
        mtu: 1420
      }
    }
  });
});

// Teardown Peer Handshake Lease
app.post('/api/v1/vpn/disconnect', authenticateAppToken, (req: Request, res: Response) => {
  const { peerId } = req.body;
  if (peerId) activeSessions.delete(peerId);
  res.json({ success: true, message: 'Disconnected' });
});

// Fetch Remote App Configuration
app.get('/api/v1/config', (_req: Request, res: Response) => {
  res.json({ success: true, data: appConfig });
});

// Legal documents (Privacy & Terms)
app.get('/api/v1/legal/:type', (req: Request, res: Response) => {
  res.json({
    success: true,
    data: {
      type: req.params.type,
      version: '1.0.0',
      contentMarkdown: '# Nsec VPN Zero-Logs Policy\n\nNsec VPN strictly guarantees zero logs of internet traffic.'
    }
  });
});

// ==========================================
// 2. Web Admin Panel Management APIs
// ==========================================

// Admin Login
app.post('/api/v1/admin/login', (req: Request, res: Response) => {
  const { email, password } = req.body;
  if (email === ADMIN_EMAIL && password === ADMIN_PASSWORD) {
    const token = jwt.sign({ email, role: 'admin' }, JWT_SECRET, { expiresIn: '24h' });
    return res.json({ success: true, token, email });
  }
  res.status(401).json({ success: false, message: 'Invalid credentials' });
});

// Real-time Overview Statistics
app.get('/api/v1/admin/stats', authenticateAdmin, (_req: Request, res: Response) => {
  res.json({
    success: true,
    data: {
      totalServers: servers.length,
      activeTunnels: activeSessions.size,
      totalDevices: devices.size,
      vipSubscribers: Array.from(devices.values()).filter(d => d.tier === 'VIP').length,
      avgServerLoad: Math.round(servers.reduce((acc, s) => acc + s.currentLoad, 0) / (servers.length || 1))
    }
  });
});

// Manage Servers
app.get('/api/v1/admin/servers', authenticateAdmin, (_req: Request, res: Response) => {
  res.json({ success: true, servers });
});

app.post('/api/v1/admin/servers', authenticateAdmin, (req: Request, res: Response) => {
  const newServer: ServerNode = {
    id: `srv_${Date.now()}`,
    ...req.body,
    currentLoad: 5,
    active: true
  };
  servers.push(newServer);
  res.json({ success: true, server: newServer });
});

app.put('/api/v1/admin/servers/:id', authenticateAdmin, (req: Request, res: Response) => {
  const index = servers.findIndex(s => s.id === req.params.id);
  if (index === -1) return res.status(404).json({ success: false, message: 'Server not found' });
  servers[index] = { ...servers[index], ...req.body };
  res.json({ success: true, server: servers[index] });
});

app.delete('/api/v1/admin/servers/:id', authenticateAdmin, (req: Request, res: Response) => {
  servers = servers.filter(s => s.id !== req.params.id);
  res.json({ success: true, message: 'Server deleted' });
});

// Manage Devices & Subscriptions
app.get('/api/v1/admin/devices', authenticateAdmin, (_req: Request, res: Response) => {
  res.json({ success: true, devices: Array.from(devices.values()) });
});

app.put('/api/v1/admin/devices/:id/tier', authenticateAdmin, (req: Request, res: Response) => {
  const { tier } = req.body;
  const device = Array.from(devices.values()).find(d => d.id === req.params.id);
  if (!device) return res.status(404).json({ success: false, message: 'Device not found' });
  device.tier = tier;
  devices.set(device.deviceId, device);
  res.json({ success: true, device });
});

// Update Remote Configuration & AdMob Controls
app.put('/api/v1/admin/config', authenticateAdmin, (req: Request, res: Response) => {
  appConfig = { ...appConfig, ...req.body };
  res.json({ success: true, config: appConfig });
});

app.listen(PORT, () => {
  console.log(`🚀 Nsec VPN Backend API listening on port ${PORT}`);
});
