jest.mock('../db', () => ({ prisma: { appConfig: { findUnique: jest.fn() }, serverNode: { findUnique: jest.fn() }, $transaction: jest.fn() } }));
jest.mock('../services/nodeHealth', () => ({ testNode: jest.fn() }));
jest.mock('../services/agentClient', () => ({ AgentClient: { addPeer: jest.fn(), removePeer: jest.fn() } }));
import { prisma } from '../db';
import { AgentClient } from '../services/agentClient';
import { testNode } from '../services/nodeHealth';
import { provisionPeer, disconnectPeer } from '../services/peerProvisioning';
const node = { id: 'n1', active: true, isPremiumOnly: false, endpoint: '47.85.109.170:51820', publicKey: Buffer.alloc(32, 8).toString('base64'),
  subnetIpv4: '10.8.0.0/24', maxPeers: 250, agentUrl: 'http://node', agentSecretHash: 'env:TEST_SECRET', dnsServers: ['1.1.1.1'], mtu: 1420, name: 'Node', city: 'City', countryName: 'Country' };
const tx = { appConfig: {findUnique: jest.fn()}, device: { findUniqueOrThrow: jest.fn() }, $executeRaw: jest.fn(), serverNode: { findUniqueOrThrow: jest.fn() },
  peerSession: { findFirst: jest.fn(), findMany: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() } };
const clientKey = Buffer.alloc(32, 9).toString('base64');
let rows: any[];
let commits: number;
function find(where: any) {
  return rows.find(row => Object.entries(where).every(([key, value]) => key === 'OR' ? (value as any[]).some(filter => Object.entries(filter).every(([k,v]) => row[k] === v)) : row[key] === value));
}
beforeEach(() => {
  jest.clearAllMocks(); rows = []; commits = 0;
  (prisma.appConfig.findUnique as jest.Mock).mockResolvedValue({ maintenanceMode: false });
  tx.appConfig.findUnique.mockResolvedValue({maintenanceMode:false});
  tx.device.findUniqueOrThrow.mockResolvedValue({ isBlocked: false, tier: 'FREE', passExpiresAt: null });
  (prisma.serverNode.findUnique as jest.Mock).mockResolvedValue(node);
  (testNode as jest.Mock).mockResolvedValue({ online: true, agentOnline: true, publicIpv4: '47.85.109.170' });
  tx.serverNode.findUniqueOrThrow.mockResolvedValue(node);
  tx.peerSession.findFirst.mockImplementation(({where,include}) => Promise.resolve(find(where) ? { ...find(where), ...(include ? { server: node } : {}) } : null));
  tx.peerSession.findMany.mockImplementation(() => Promise.resolve(rows));
  tx.peerSession.create.mockImplementation(({data}) => { const row = { id: 'session-' + rows.length, ...data }; rows.push(row); return Promise.resolve({...row}); });
  tx.peerSession.update.mockImplementation(({where,data}) => { const row = find(where); Object.assign(row, data); return Promise.resolve({...row}); });
  tx.peerSession.delete.mockImplementation(({where}) => { rows = rows.filter(row => row.id !== where.id); return Promise.resolve(); });
  (prisma.$transaction as jest.Mock).mockImplementation(async work => {
    const before = rows.map(row => ({...row}));
    try { const result = await work(tx); commits++; return result; }
    catch (error) { rows = before; throw error; }
  });
  (AgentClient.addPeer as jest.Mock).mockResolvedValue(undefined);
  (AgentClient.removePeer as jest.Mock).mockResolvedValue(undefined);
});
it('forced updates reject a stale client before reserving an IP or calling the agent', async () => {
  (prisma.appConfig.findUnique as jest.Mock).mockResolvedValue({forceUpdate:true,minAppVersionCode:3});
  await expect(provisionPeer('d1','n1',clientKey,'FREE',2)).rejects.toThrow('APP_UPDATE_REQUIRED');
  expect(AgentClient.addPeer).not.toHaveBeenCalled(); expect(rows).toHaveLength(0);
});
it('a forced update enabled after health probing is checked again in the transaction', async () => {
  tx.appConfig.findUnique.mockResolvedValue({forceUpdate:true,minAppVersionCode:3});
  await expect(provisionPeer('d1','n1',clientKey,'FREE',2)).rejects.toThrow('APP_UPDATE_REQUIRED');
  expect(AgentClient.addPeer).not.toHaveBeenCalled(); expect(rows).toHaveLength(0);
});
it('commits a reservation before external mutation and returns config only after confirmation', async () => {
  (AgentClient.addPeer as jest.Mock).mockImplementation(async () => {
    expect(commits).toBe(1); expect(rows[0].pendingPublicKey).toBe(clientKey);
  });
  const result = await provisionPeer('d1', 'n1', clientKey, 'FREE');
  expect(result.tunnelConfig.publicIpv4).toBe('47.85.109.170');
  expect(result.tunnelConfig.clientAddress).toBe('10.8.0.2/32');
  expect(rows[0].pendingPublicKey).toBeNull();
});
it('a rejected add releases the reservation only after acknowledged compensation', async () => {
  (AgentClient.addPeer as jest.Mock).mockRejectedValue(new Error('timeout'));
  await expect(provisionPeer('d1', 'n1', clientKey, 'FREE')).rejects.toThrow('AGENT_UNREACHABLE');
  expect(rows).toHaveLength(0); expect(AgentClient.removePeer).toHaveBeenCalled();
  expect((AgentClient.removePeer as jest.Mock).mock.invocationCallOrder[0]).toBeLessThan(tx.peerSession.delete.mock.invocationCallOrder[0]);
});
it('an ambiguous add/removal timeout retains its IP so another device cannot reuse it', async () => {
  tx.serverNode.findUniqueOrThrow.mockResolvedValue({...node, maxPeers: 1});
  (AgentClient.addPeer as jest.Mock).mockRejectedValue(new Error('timeout'));
  (AgentClient.removePeer as jest.Mock).mockRejectedValue(new Error('unreachable'));
  await expect(provisionPeer('d1','n1',clientKey,'FREE')).rejects.toThrow('AGENT_UNREACHABLE');
  expect(rows[0].pendingPublicKey).toBe(clientKey);
  await expect(provisionPeer('d2','n1',Buffer.alloc(32,10).toString('base64'),'FREE')).rejects.toThrow('SERVER_FULL');
  expect(rows).toHaveLength(1);
});
it('DB failure after agent confirmation rolls back only the confirmation, preserving the durable reservation', async () => {
  tx.peerSession.update.mockRejectedValueOnce(new Error('commit unavailable'));
  await expect(provisionPeer('d1','n1',clientKey,'FREE')).rejects.toThrow('commit unavailable');
  expect(rows[0].pendingPublicKey).toBe(clientKey); expect(rows[0].allocatedIp).toBe('10.8.0.2/32');
});
it('renewal preserves the address but rotates the disconnect handle; late disconnect cannot remove the new peer', async () => {
  const old = await provisionPeer('d1','n1',clientKey,'FREE');
  const renewed = await provisionPeer('d1','n1',clientKey,'FREE');
  expect(renewed.peerId).not.toBe(old.peerId);
  expect(renewed.tunnelConfig.clientAddress).toBe(old.tunnelConfig.clientAddress);
  await disconnectPeer('d1',old.peerId); expect(rows).toHaveLength(1);
  expect(AgentClient.removePeer).not.toHaveBeenCalled();
  await disconnectPeer('d1',renewed.peerId); expect(rows).toHaveLength(0);
});
it('disconnect is owned by the authenticated device and never uses a supplied public key', async () => {
  const peer = await provisionPeer('d1','n1',clientKey,'FREE');
  await disconnectPeer('other',peer.peerId); expect(rows).toHaveLength(1);
  await disconnectPeer('d1',peer.peerId);
  expect(AgentClient.removePeer).toHaveBeenCalledWith(node.agentUrl,node.agentSecretHash,{publicKey:clientKey});
});
it('offline nodes and blocked devices cannot reserve or add peers', async () => {
  (testNode as jest.Mock).mockResolvedValueOnce({online:false,agentOnline:false});
  await expect(provisionPeer('d1','n1',clientKey,'FREE')).rejects.toThrow('AGENT_UNREACHABLE');
  tx.device.findUniqueOrThrow.mockResolvedValueOnce({isBlocked:true,tier:'FREE'});
  await expect(provisionPeer('d1','n1',clientKey,'FREE')).rejects.toThrow('DEVICE_BLOCKED');
  expect(rows).toHaveLength(0); expect(AgentClient.addPeer).not.toHaveBeenCalled();
});
it('re-reads premium expiry and node credential before mutation', async () => {
  const expiry = new Date(Date.now() + 30 * 60000);
  const premium = {...node,isPremiumOnly:true};
  (prisma.serverNode.findUnique as jest.Mock).mockResolvedValue(premium);
  tx.serverNode.findUniqueOrThrow.mockResolvedValue(premium);
  tx.device.findUniqueOrThrow.mockResolvedValue({isBlocked:false,tier:'VIP',passExpiresAt:expiry});
  await provisionPeer('d1','n1',clientKey,'VIP');
  expect(AgentClient.addPeer).toHaveBeenCalledWith(node.agentUrl,node.agentSecretHash,expect.objectContaining({expiresAt:expiry.toISOString()}));
  tx.serverNode.findUniqueOrThrow.mockResolvedValue({...premium,agentSecretHash:'env:CHANGED_SECRET'});
  await expect(provisionPeer('d1','n1',clientKey,'VIP')).rejects.toThrow('NODE_DISABLED');
});
it('maintenance fails before agent or allocation calls', async () => {
  (prisma.appConfig.findUnique as jest.Mock).mockResolvedValue({maintenanceMode:true});
  await expect(provisionPeer('d1','n1',clientKey,'FREE')).rejects.toThrow('MAINTENANCE_MODE');
  expect(AgentClient.addPeer).not.toHaveBeenCalled();
});
