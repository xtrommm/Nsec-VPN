jest.mock('../db', () => ({ prisma: { peerSession: { findMany: jest.fn() }, $transaction: jest.fn() } }));
jest.mock('../services/agentClient', () => ({ AgentClient: { removePeer: jest.fn() } }));
import { prisma } from '../db';
import { AgentClient } from '../services/agentClient';
import { expirePeers } from '../services/peerProvisioning';
const tx = { $executeRaw: jest.fn(), peerSession: { findFirst: jest.fn(), delete: jest.fn() } };
const row = { id: 'test-peer',deviceId:'test-device',pendingPublicKey:null,updatedAt:new Date(),serverId:'test-node',clientPublicKey:Buffer.alloc(32,8).toString('base64'),server:{active:true,agentUrl:'http://test.invalid',agentSecretHash:'env:NSEC_TEST_AGENT_KEY'},device:{isBlocked:false} };
beforeEach(() => {
  jest.clearAllMocks();
  (prisma.peerSession.findMany as jest.Mock).mockResolvedValue([{id:row.id,deviceId:row.deviceId,serverId:row.serverId}]);
  (prisma.$transaction as jest.Mock).mockImplementation(async work=>work(tx));
  (AgentClient.removePeer as jest.Mock).mockResolvedValue(undefined);
});
it('never removes a lease renewed after the expiry scan', async () => {
  tx.peerSession.findFirst.mockResolvedValue({...row,expiresAt:new Date(Date.now()+60000)});
  await expirePeers();expect(AgentClient.removePeer).not.toHaveBeenCalled();expect(tx.peerSession.delete).not.toHaveBeenCalled();
});
it('removes an actually expired lease only after agent confirmation', async () => {
  tx.peerSession.findFirst.mockResolvedValue({...row,expiresAt:new Date(Date.now()-60000)});
  await expirePeers();expect(AgentClient.removePeer).toHaveBeenCalled();
  expect((AgentClient.removePeer as jest.Mock).mock.invocationCallOrder[0]).toBeLessThan(tx.peerSession.delete.mock.invocationCallOrder[0]);
});
it('retains the reservation when the agent is unreachable', async () => {
  tx.peerSession.findFirst.mockResolvedValue({...row,expiresAt:new Date(Date.now()-60000)});
  (AgentClient.removePeer as jest.Mock).mockRejectedValue(new Error('unreachable'));
  await expirePeers();expect(tx.peerSession.delete).not.toHaveBeenCalled();
});

it('reconciles abandoned pending keys only after confirming every tracked key removal', async () => {
  const pending = Buffer.alloc(32,9).toString('base64');
  tx.peerSession.findFirst.mockResolvedValue({...row, pendingPublicKey:pending, updatedAt:new Date(Date.now()-180000), expiresAt:new Date(Date.now()+60000)});
  await expirePeers();
  expect(AgentClient.removePeer).toHaveBeenCalledTimes(2);
  expect(AgentClient.removePeer).toHaveBeenCalledWith(row.server.agentUrl,row.server.agentSecretHash,{publicKey:pending});
  expect(tx.peerSession.delete).toHaveBeenCalled();
});
it('an in-progress reservation is not removed by cleanup', async () => {
  tx.peerSession.findFirst.mockResolvedValue({...row,pendingPublicKey:Buffer.alloc(32,9).toString('base64'),expiresAt:new Date(Date.now()+60000)});
  await expirePeers();expect(AgentClient.removePeer).not.toHaveBeenCalled();expect(tx.peerSession.delete).not.toHaveBeenCalled();
});
it('advances beyond unreachable candidates so later leases are not starved', async () => {
  (prisma.peerSession.findMany as jest.Mock).mockResolvedValue([{id:'z-cursor-fixture',deviceId:row.deviceId,serverId:row.serverId}]);
  tx.peerSession.findFirst.mockResolvedValue({...row,id:'z-cursor-fixture',expiresAt:new Date(0)});
  (AgentClient.removePeer as jest.Mock).mockRejectedValue(new Error('unreachable'));
  await expirePeers();
  (prisma.peerSession.findMany as jest.Mock).mockClear();
  (prisma.peerSession.findMany as jest.Mock).mockResolvedValue([]);
  await expirePeers();
  expect(prisma.peerSession.findMany).toHaveBeenNthCalledWith(1, expect.objectContaining({where:expect.objectContaining({id:{gt:'z-cursor-fixture'}}),orderBy:{id:'asc'}}));
  expect(prisma.peerSession.findMany).toHaveBeenCalledTimes(2);
});
