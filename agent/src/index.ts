import express, { Request, Response, NextFunction } from 'express';
import helmet from 'helmet';
import { execFile } from 'child_process';
import crypto from 'crypto';
import os from 'os';
import util from 'util';

const execFileAsync = util.promisify(execFile);
const app = express();
const PORT = process.env.AGENT_PORT || 51821;
const AGENT_SECRET = process.env.AGENT_SECRET || 'nsec_node_agent_secret_2026';
const WG_INTERFACE = process.env.WG_INTERFACE || 'wg0';

app.use(helmet());
app.use(express.json());

// In-memory cache to prevent replay attacks within the 60s time window
const seenSignatures = new Set<string>();

setInterval(() => {
  seenSignatures.clear();
}, 60 * 1000);

// Strict HMAC-SHA256 Authentication Middleware
const verifyHmacSignature = (req: Request, res: Response, next: NextFunction) => {
  const timestamp = req.headers['x-agent-timestamp'] as string;
  const signature = req.headers['x-agent-signature'] as string;

  if (!timestamp || !signature) {
    return res.status(401).json({ success: false, error: 'MISSING_SIGNATURE' });
  }

  // Prevent replay attacks (timestamp within 60 seconds)
  const timeDiff = Math.abs(Date.now() - parseInt(timestamp, 10));
  if (isNaN(timeDiff) || timeDiff > 60000) {
    return res.status(401).json({ success: false, error: 'REQUEST_EXPIRED' });
  }

  // Deduplication check
  if (seenSignatures.has(signature)) {
    return res.status(401).json({ success: false, error: 'REPLAY_DETECTED' });
  }
  seenSignatures.add(signature);

  const payload = req.method === 'GET' ? timestamp : `${timestamp}.${JSON.stringify(req.body)}`;
  const expectedSig = crypto.createHmac('sha256', AGENT_SECRET).update(payload).digest('hex');

  const sigBuffer = Buffer.from(signature);
  const expectedBuffer = Buffer.from(expectedSig);

  if (sigBuffer.length !== expectedBuffer.length || !crypto.timingSafeEqual(sigBuffer, expectedBuffer)) {
    return res.status(403).json({ success: false, error: 'INVALID_SIGNATURE' });
  }

  next();
};

/**
 * Persists current WireGuard interface state to disk to survive server reboots.
 */
async function persistWireGuardState() {
  try {
    // If wg-quick is managing wg0, sync configuration file
    await execFileAsync('wg-quick', ['save', WG_INTERFACE]);
  } catch (_e) {
    // Graceful fallback if wg-quick save is not supported on this platform
  }
}

// Add peer via parameterized command execution
app.post('/api/v1/agent/peers', verifyHmacSignature, async (req: Request, res: Response) => {
  const { publicKey, allocatedIp } = req.body;

  // Strict format validation
  if (!/^[A-Za-z0-9+/]{43}=$/.test(publicKey)) {
    return res.status(400).json({ success: false, error: 'INVALID_PUBLIC_KEY' });
  }
  if (!/^10\.\d{1,3}\.\d{1,3}\.\d{1,3}\/32$/.test(allocatedIp)) {
    return res.status(400).json({ success: false, error: 'INVALID_IP_FORMAT' });
  }

  try {
    // Parameterized call: strictly eliminates shell injection risks
    await execFileAsync('wg', ['set', WG_INTERFACE, 'peer', publicKey, 'allowed-ips', allocatedIp]);
    await persistWireGuardState();
    return res.json({ success: true, message: 'Peer added successfully' });
  } catch (err: any) {
    return res.status(500).json({ success: false, error: 'WG_EXEC_ERROR', details: err.message });
  }
});

// Remove peer
app.delete('/api/v1/agent/peers', verifyHmacSignature, async (req: Request, res: Response) => {
  const { publicKey } = req.body;

  if (!/^[A-Za-z0-9+/]{43}=$/.test(publicKey)) {
    return res.status(400).json({ success: false, error: 'INVALID_PUBLIC_KEY' });
  }

  try {
    await execFileAsync('wg', ['set', WG_INTERFACE, 'peer', publicKey, 'remove']);
    await persistWireGuardState();
    return res.json({ success: true, message: 'Peer removed' });
  } catch (err: any) {
    return res.status(500).json({ success: false, error: 'WG_EXEC_ERROR', details: err.message });
  }
});

// Node Health & Telemetry
app.get('/api/v1/agent/health', verifyHmacSignature, async (_req: Request, res: Response) => {
  try {
    const { stdout } = await execFileAsync('wg', ['show', WG_INTERFACE, 'peers']);
    const peers = stdout.trim().split('\n').filter(Boolean);

    // Compute CPU Load Average
    const loadAvg = os.loadavg()[0];
    const cpuCount = os.cpus().length || 1;
    const loadPercent = Math.min(Math.round((loadAvg / cpuCount) * 100), 100);

    return res.json({
      online: true,
      interface: WG_INTERFACE,
      peerCount: peers.length,
      loadPercent
    });
  } catch (_e) {
    return res.json({ online: false, peerCount: 0, loadPercent: 0 });
  }
});

app.listen(PORT, () => {
  console.log(`🛡️ Nsec WireGuard Node Agent listening on port ${PORT}`);
});

export default app;
