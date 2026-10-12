import express, { Request, Response, NextFunction } from 'express';
import helmet from 'helmet';
import { execFile } from 'child_process';
import crypto from 'crypto';
import os from 'os';
import util from 'util';
import { readFile, writeFile, rename, mkdir } from 'fs/promises';

const exec = util.promisify(execFile);
const app = express();
const secret = process.env.AGENT_SECRET;
if (!secret || secret.length < 32) throw new Error('AGENT_SECRET must be configured with at least 32 characters');
const iface = process.env.WG_INTERFACE || 'wg0';
if (!/^[a-zA-Z0-9_-]{1,15}$/.test(iface)) throw new Error('Invalid interface name');
const stateDir = process.env.AGENT_STATE_DIR || '/var/lib/nsec-agent';
const leaseFile = `${stateDir}/leases.json`;
const port = Number(process.env.AGENT_PORT || 51821);
app.use(helmet());
app.use(express.json({ limit: '16kb' }));
app.use((req, res, next) => {
  const originalJson = res.json.bind(res);
  res.json = ((body: unknown) => {
    if (req.get('X-Agent-Version') === '2') {
      const value = `response.${req.get('X-Agent-Timestamp')}.${req.method}.${req.path}.${JSON.stringify(body)}`;
      res.set('X-Agent-Response-Signature', crypto.createHmac('sha256', secret!).update(value).digest('hex'));
    }
    return originalJson(body);
  }) as typeof res.json;
  next();
});
const seen = new Map<string, number>();
function auth(req: Request, res: Response, next: NextFunction) {
  if (req.get('X-Agent-Version') !== '2' && process.env.ALLOW_LEGACY_AGENT_V1 !== 'true')
    return res.status(426).json({ success: false, error: 'AGENT_PROTOCOL_REQUIRED' });
  const ts = req.get('X-Agent-Timestamp') || '';
  const signature = req.get('X-Agent-Signature') || '';
  const now = Date.now();
  for (const [key, expiry] of seen) if (expiry < now) seen.delete(key);
  if (!/^\d{13}$/.test(ts) || !/^[a-f0-9]{64}$/.test(signature) || Math.abs(now - Number(ts)) > 60000)
    return res.status(401).json({ success: false, error: 'REQUEST_EXPIRED' });
  const body = req.method === 'GET' ? '' : JSON.stringify(req.body);
  const payload = req.get('X-Agent-Version') === '2' ? `${ts}.${req.method}.${req.path}.${body}` :
    req.method === 'GET' ? ts : `${ts}.${body}`;
  const expected = crypto.createHmac('sha256', secret!).update(payload).digest('hex');
  if (!crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(expected)))
    return res.status(403).json({ success: false, error: 'INVALID_SIGNATURE' });
  if (seen.has(signature)) return res.status(401).json({ success: false, error: 'REPLAY_DETECTED' });
  seen.set(signature, Number(ts) + 60000);
  next();
}
export function validKey(value: unknown): value is string {
  return typeof value === 'string' && /^[A-Za-z0-9+/]{43}=$/.test(value) &&
    Buffer.from(value, 'base64').toString('base64') === value && Buffer.from(value, 'base64').some(x => x !== 0);
}
export function validAddress(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d+\.\d+\.\d+\.\d+\/32$/.test(value)) return false;
  const parts = value.split('/')[0].split('.').map(Number);
  return parts.every(p => p >= 0 && p <= 255) && parts[3] >= 2 && parts[3] <= 254 &&
    (parts[0] === 10 || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) || (parts[0] === 192 && parts[1] === 168));
}
export function publicIpv4(value: string): boolean {
  const a = value.split('.').map(Number);
  return /^\d+\.\d+\.\d+\.\d+$/.test(value) && a.every(x => x <= 255) &&
    !(a[0] === 0 || a[0] === 10 || a[0] === 127 || a[0] >= 224 ||
    (a[0] === 172 && a[1] >= 16 && a[1] <= 31) || (a[0] === 192 && a[1] === 168) ||
    (a[0] === 169 && a[1] === 254) || (a[0] === 100 && a[1] >= 64 && a[1] <= 127) ||
    (a[0] === 192 && a[1] === 0) || (a[0] === 198 && (a[1] === 18 || a[1] === 19 || (a[1] === 51 && a[2] === 100))) ||
    (a[0] === 203 && a[1] === 0 && a[2] === 113));
}
type Lease = { publicKey: string; allocatedIp: string; expiresAt: number };
let leases: Record<string, Lease> = {};
let queue: Promise<unknown> = Promise.resolve();
function serialize<T>(work: () => Promise<T>): Promise<T> {
  const result = queue.then(work);
  queue = result.catch(() => undefined);
  return result;
}
const run = (command: string, args: string[]) => exec(command, args, { timeout: 5000, maxBuffer: 1024 * 1024 });
async function saveLeases() {
  await mkdir(stateDir, { recursive: true, mode: 0o700 });
  await writeFile(`${leaseFile}.tmp`, JSON.stringify(leases), { mode: 0o600 });
  await rename(`${leaseFile}.tmp`, leaseFile);
}
async function saveInterface() { await run('wg-quick', ['save', iface]); }
async function add(publicKey: string, allocatedIp: string, expiresAt: number) {
  const { stdout } = await run('ip', ['-4', '-o', 'addr', 'show', 'dev', iface]);
  const address = /inet (\d+\.\d+\.\d+)\.\d+\/24/.exec(stdout);
  if (!address || !allocatedIp.startsWith(`${address[1]}.`)) throw new Error('SUBNET_MISMATCH');
  const { stdout: configured } = await run('wg', ['show', iface, 'allowed-ips']);
  for (const line of configured.trim().split('\n')) {
    const [key, ...ips] = line.split(/\s+/);
    if (key !== publicKey && ips.join(' ').includes(allocatedIp)) throw new Error('ADDRESS_IN_USE');
  }
  const previous = leases[publicKey];
  // Write lease first: a crash after wg set still leaves an expiry record for cleanup.
  leases[publicKey] = { publicKey, allocatedIp, expiresAt };
  await saveLeases();
  try {
    await run('wg', ['set', iface, 'peer', publicKey, 'allowed-ips', allocatedIp]);
    await saveInterface();
    const confirmation = await run('wg', ['show', iface, 'allowed-ips']);
    if (!confirmation.stdout.split('\n').some(line => line.split(/\s+/)[0] === publicKey && line.includes(allocatedIp))) throw new Error('PEER_NOT_CONFIRMED');
  } catch {
    await run('wg', ['set', iface, 'peer', publicKey, 'remove']);
    if (previous) await run('wg', ['set', iface, 'peer', publicKey, 'allowed-ips', previous.allocatedIp]);
    await saveInterface();
    if (previous) leases[publicKey] = previous; else delete leases[publicKey];
    await saveLeases();
    throw new Error('PEER_PERSISTENCE_FAILED');
  }
}
async function remove(publicKey: string) {
  await run('wg', ['set', iface, 'peer', publicKey, 'remove']);
  await saveInterface();
  delete leases[publicKey];
  await saveLeases();
}
app.post('/api/v1/agent/peers', auth, async (req, res) => {
  const { publicKey, allocatedIp, expiresAt } = req.body;
  // Legacy backend requests get a real, bounded lease, never a simulated peer.
  const expiry = expiresAt === undefined ? Date.now() + 86400000 : Date.parse(expiresAt);
  if (!validKey(publicKey) || !validAddress(allocatedIp) || !Number.isFinite(expiry) || expiry <= Date.now() || expiry > Date.now() + 86460000)
    return res.status(400).json({ success: false, error: 'INVALID_INPUT' });
  try { await serialize(() => add(publicKey, allocatedIp, expiry)); return res.json({ success: true, persisted: true }); }
  catch { return res.status(503).json({ success: false, error: 'PEER_PERSISTENCE_FAILED' }); }
});
app.delete('/api/v1/agent/peers', auth, async (req, res) => {
  const { publicKey } = req.body;
  if (!validKey(publicKey)) return res.status(400).json({ success: false, error: 'INVALID_PUBLIC_KEY' });
  try { await serialize(() => remove(publicKey)); return res.json({ success: true, persisted: true }); }
  catch { return res.status(503).json({ success: false, error: 'PEER_REMOVAL_FAILED' }); }
});
let observedIp = '';
let observedAt = 0;
async function observePublicIp() {
  if (Date.now() - observedAt < 60000 && observedIp) return observedIp;
  const response = await fetch('https://ifconfig.me/ip', { signal: AbortSignal.timeout(3000) });
  if (!response.ok) throw new Error('PUBLIC_IP_UNAVAILABLE');
  const ip = (await response.text()).trim();
  if (!publicIpv4(ip)) throw new Error('INVALID_PUBLIC_IP');
  observedIp = ip; observedAt = Date.now(); return ip;
}
app.get('/api/v1/agent/health', auth, async (_req, res) => {
  try {
    const [key, listen, peers, handshakes, publicIp] = await Promise.all([
      run('wg', ['show', iface, 'public-key']), run('wg', ['show', iface, 'listen-port']),
      run('wg', ['show', iface, 'peers']), run('wg', ['show', iface, 'latest-handshakes']), observePublicIp()
    ]);
    const publicKey = key.stdout.trim();
    if (!validKey(publicKey)) throw new Error('INVALID_NODE_KEY');
    const handshakeCount = handshakes.stdout.trim().split('\n').filter(line => {
      const ts = Number(line.split(/\s+/)[1]); return ts > 0 && Date.now() / 1000 - ts < 180;
    }).length;
    return res.json({ online: true, publicKey, publicIpv4: publicIp, listenPort: Number(listen.stdout.trim()),
      peerCount: peers.stdout.trim().split('\n').filter(Boolean).length, handshakeCount,
      loadPercent: Math.min(Math.round(os.loadavg()[0] / Math.max(os.cpus().length, 1) * 100), 100) });
  } catch { return res.status(503).json({ online: false, error: 'NODE_HEALTH_FAILED' }); }
});
async function start() {
  try { leases = JSON.parse(await readFile(leaseFile, 'utf8')); }
  catch (e) { if ((e as NodeJS.ErrnoException).code !== 'ENOENT') throw new Error('LEASE_STATE_INVALID'); }
  await serialize(async () => {
    for (const lease of Object.values(leases)) {
      if (!validKey(lease.publicKey) || !validAddress(lease.allocatedIp) || !Number.isFinite(lease.expiresAt)) throw new Error('LEASE_STATE_INVALID');
      if (lease.expiresAt <= Date.now()) await remove(lease.publicKey);
      else await add(lease.publicKey, lease.allocatedIp, lease.expiresAt);
    }
  });
  setInterval(() => { void serialize(async () => {
    for (const lease of Object.values(leases)) if (lease.expiresAt <= Date.now()) await remove(lease.publicKey);
  }).catch(() => console.error('Lease cleanup failed; will retry')); }, 30000).unref();
  app.listen(port, process.env.AGENT_BIND || '127.0.0.1', () => console.log('Node agent started'));
}
if (require.main === module) void start().catch(() => { console.error('Node agent startup failed'); process.exit(1); });
export default app;
