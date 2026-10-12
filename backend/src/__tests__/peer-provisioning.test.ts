jest.mock('../db', () => ({ prisma: { serverNode: { findUnique: jest.fn() }, $transaction: jest.fn() } }));
jest.mock('../services/nodeHealth', () => ({ testNode: jest.fn() }));
jest.mock('../services/agentClient', () => ({ AgentClient: { addPeer: jest.fn(), removePeer: jest.fn() } }));
import { prisma } from '../db';
import { AgentClient } from '../services/agentClient';
import { testNode } from '../services/nodeHealth';
import { provisionPeer } from '../services/peerProvisioning';
const node = { id: 'n1', active: true, isPremiumOnly: false, endpoint: '47.85.109.170:51820', publicKey: Buffer.alloc(32, 8).toString('base64'),
  subnetIpv4: '10.8.0.0/24', maxPeers: 250, agentUrl: 'http://node', agentSecretHash: 'x'.repeat(32), dnsServers: ['1.1.1.1'], mtu: 1420, name: 'Node', city: 'City', countryName: 'Country' };
const tx = { device: { findUniqueOrThrow: jest.fn() }, $executeRaw: jest.fn(), serverNode: { findUniqueOrThrow: jest.fn() }, peerSession: { findFirst: jest.fn(), findMany: jest.fn(), create: jest.fn() } };
const clientKey = Buffer.alloc(32, 9).toString('base64');
beforeEach(() => {
  jest.clearAllMocks(); tx.device.findUniqueOrThrow.mockResolvedValue({ isBlocked: false, tier: 'FREE', passExpiresAt: null }); (prisma.serverNode.findUnique as jest.Mock).mockResolvedValue(node);
  (testNode as jest.Mock).mockResolvedValue({ online: true, agentOnline: true, publicIpv4: '47.85.109.170' });
  tx.serverNode.findUniqueOrThrow.mockResolvedValue(node); tx.peerSession.findMany.mockResolvedValue([]);
  tx.peerSession.create.mockResolvedValue({ peerId: 'peer_test' });
  (prisma.$transaction as jest.Mock).mockImplementation(async work => work(tx));
  (AgentClient.addPeer as jest.Mock).mockResolvedValue(undefined);
});
it('locks and provisions before returning a real endpoint and node IP', async () => {
  const response = await provisionPeer('d1', 'n1', clientKey, 'FREE');
  expect(tx.$executeRaw.mock.invocationCallOrder[0]).toBeLessThan((AgentClient.addPeer as jest.Mock).mock.invocationCallOrder[0]);
  expect((AgentClient.addPeer as jest.Mock).mock.invocationCallOrder[0]).toBeLessThan(tx.peerSession.create.mock.invocationCallOrder[0]);
  expect(response.tunnelConfig.publicIpv4).toBe('47.85.109.170');
  expect(response.tunnelConfig.clientAddress).toBe('10.8.0.2/32');
});
it('agent rejection prevents a session/config and attempts compensation', async () => {
  (AgentClient.addPeer as jest.Mock).mockRejectedValue(new Error('timeout'));
  await expect(provisionPeer('d1', 'n1', clientKey, 'FREE')).rejects.toThrow('AGENT_UNREACHABLE');
  expect(tx.peerSession.create).not.toHaveBeenCalled(); expect(AgentClient.removePeer).toHaveBeenCalled();
});
it('offline node fails before allocating or provisioning', async () => {
  (testNode as jest.Mock).mockResolvedValue({ online: false, agentOnline: false });
  await expect(provisionPeer('d1', 'n1', clientKey, 'FREE')).rejects.toThrow('AGENT_UNREACHABLE');
  expect(prisma.$transaction).not.toHaveBeenCalled();
});
it('respects capacity and reserved leases', async () => {
  (prisma.serverNode.findUnique as jest.Mock).mockResolvedValue({ ...node, maxPeers: 1 });
  tx.peerSession.findMany.mockResolvedValueOnce([]).mockResolvedValueOnce([{ allocatedIp: '10.8.0.2/32' }]);
  await expect(provisionPeer('d1', 'n1', clientKey, 'FREE')).rejects.toThrow('SERVER_FULL');
  expect(AgentClient.addPeer).not.toHaveBeenCalled();
});

it('a blocked device cannot create a peer even if authentication happened before revocation', async () => {
  tx.device.findUniqueOrThrow.mockResolvedValue({ isBlocked: true, tier: 'FREE' });
  await expect(provisionPeer('d1','n1',clientKey,'FREE')).rejects.toThrow('DEVICE_BLOCKED');
  expect(AgentClient.addPeer).not.toHaveBeenCalled();
});

it('a premium peer lease cannot outlive the verified entitlement', async () => {
  const expiresAt = new Date(Date.now() + 30 * 60 * 1000);
  const premium = { ...node, isPremiumOnly: true };
  (prisma.serverNode.findUnique as jest.Mock).mockResolvedValue(premium);
  tx.serverNode.findUniqueOrThrow.mockResolvedValue(premium);
  tx.device.findUniqueOrThrow.mockResolvedValue({ isBlocked: false, tier: 'REWARDED_PASS', passExpiresAt: expiresAt });
  await provisionPeer('d1', 'n1', clientKey, 'REWARDED_PASS');
  expect(AgentClient.addPeer).toHaveBeenCalledWith(node.agentUrl, node.agentSecretHash,
    expect.objectContaining({ expiresAt: expiresAt.toISOString() }));
});
