jest.mock('../db', () => ({ prisma: { device: { findUniqueOrThrow: jest.fn() }, serverNode: { findUnique: jest.fn(), findMany: jest.fn() }, peerSession: { findMany: jest.fn(), groupBy: jest.fn() } } }));
jest.mock('../services/peerProvisioning', () => ({ provisionPeer: jest.fn() }));
import { prisma } from '../db';
import { provisionPeer } from '../services/peerProvisioning';
import { provisionLocationPeer } from '../services/locationSelection';
import { ConnectVpnSchema } from '../types';
function leases(rows: { serverId: string; deviceId: string }[]) {
  const counts = new Map<string, number>();
  rows.forEach(r => counts.set(r.serverId, (counts.get(r.serverId) ?? 0) + 1));
  (prisma.peerSession.groupBy as jest.Mock).mockResolvedValue([...counts].map(([serverId, count]) => ({ serverId, _count: { _all: count } })));
  (prisma.peerSession.findMany as jest.Mock).mockResolvedValue(rows.filter(r => r.deviceId === 'device'));
}
const nodes = Array.from({ length: 10 }, (_, i) => ({ id: `node-${i}`, countryCode: 'SG', city: 'Singapore', maxPeers: 1, isPremiumOnly: false }));
beforeEach(() => {
  jest.clearAllMocks();
  (prisma.device.findUniqueOrThrow as jest.Mock).mockResolvedValue({ id: 'device', tier: 'FREE', isBlocked: false });
  (prisma.serverNode.findUnique as jest.Mock).mockResolvedValue(nodes[0]);
  (prisma.serverNode.findMany as jest.Mock).mockResolvedValue(nodes);
  leases([]);
  (provisionPeer as jest.Mock).mockImplementation(async (_device, id) => ({ peerId: id }));
});
it('reaches the tenth node when the first nine are full without making nine agent calls', async () => {
  leases(nodes.slice(0, 9).map(n => ({ serverId: n.id, deviceId: 'other' })));
  expect(await provisionLocationPeer('device', 'node-0', 'key')).toEqual({ peerId: 'node-9' });
  expect(provisionPeer).toHaveBeenCalledTimes(1);
});
it('returns all-full only when every eligible node has reserved capacity', async () => {
  leases(nodes.map(n => ({ serverId: n.id, deviceId: 'other' })));
  await expect(provisionLocationPeer('device', 'node-0', 'key')).rejects.toThrow('ALL_SERVERS_FULL');
  expect(provisionPeer).not.toHaveBeenCalled();
});
it('falls through all concurrent capacity misses beyond the old three-node cap', async () => {
  (provisionPeer as jest.Mock).mockImplementation(async (_d, id) => { if (id !== 'node-9') throw new Error('SERVER_FULL'); return { peerId: id }; });
  expect(await provisionLocationPeer('device', 'node-0', 'key')).toEqual({ peerId: 'node-9' });
  expect(provisionPeer).toHaveBeenCalledTimes(10);
});
it('latency selects among available nodes, and queries enforce location and tier', async () => {
  expect(await provisionLocationPeer('device', 'node-0', 'key', [{ nodeId: 'node-8', pingMs: 10 }])).toEqual({ peerId: 'node-8' });
  expect(prisma.serverNode.findMany).toHaveBeenCalledWith(expect.objectContaining({ where: expect.objectContaining({
    active: true, healthy: true, isPremiumOnly: false, countryCode: { equals: 'SG', mode: 'insensitive' }
  }) }));
});
it('can reuse the same device reservation at capacity', async () => {
  leases(nodes.map(n => ({ serverId: n.id, deviceId: n.id === 'node-0' ? 'device' : 'other' })));
  expect(await provisionLocationPeer('device', 'node-0', 'key')).toEqual({ peerId: 'node-0' });
});
it('bounds network failures and never reports them as full', async () => {
  (provisionPeer as jest.Mock).mockRejectedValue(new Error('AGENT_UNREACHABLE'));
  await expect(provisionLocationPeer('device', 'node-0', 'key')).rejects.toThrow('AGENT_UNREACHABLE');
  expect(provisionPeer).toHaveBeenCalledTimes(3);
});
it('blocked devices fail before capacity disclosure and no-node is offline', async () => {
  (prisma.device.findUniqueOrThrow as jest.Mock).mockResolvedValue({ isBlocked: true });
  await expect(provisionLocationPeer('device', 'node-0', 'key')).rejects.toThrow('DEVICE_BLOCKED');
  expect(prisma.serverNode.findMany).not.toHaveBeenCalled();
});
it('rejects invalid advisory measurements while retaining the old request shape', () => {
  const request = { serverId: 'node-0', clientPublicKey: Buffer.alloc(32, 8).toString('base64') };
  expect(ConnectVpnSchema.safeParse(request).success).toBe(true);
  expect(ConnectVpnSchema.safeParse({ ...request, locationSelection: true, nodeLatencies: [{ nodeId: 'node-1', pingMs: -1 }] }).success).toBe(false);
});

it('excludes failed handshake nodes in the database selection', async () => {
  await provisionLocationPeer('device', 'node-0', 'key', [], ['node-3']);
  expect(prisma.serverNode.findMany).toHaveBeenCalledWith(expect.objectContaining({ where: expect.objectContaining({ id: { notIn: ['node-3'] } }) }));
});
it('does not call a partly failed location entirely full', async () => {
  leases(nodes.map(n => ({ serverId: n.id, deviceId: 'other' })));
  await expect(provisionLocationPeer('device', 'node-0', 'key', [], ['node-3'])).rejects.toThrow('NODE_OFFLINE');
});

it('normalizes city casing and surrounding space and never crosses cities', async () => {
  (prisma.serverNode.findMany as jest.Mock).mockResolvedValue([
    { ...nodes[0], city: 'Elsewhere' }, { ...nodes[1], city: ' singapore ' }
  ]);
  expect(await provisionLocationPeer('device', 'node-0', 'key')).toEqual({ peerId: 'node-1' });
});
