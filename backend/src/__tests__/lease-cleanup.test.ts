jest.mock('../db', () => ({ prisma: { peerSession: { findMany: jest.fn() }, $transaction: jest.fn() } }));
jest.mock('../services/agentClient', () => ({ AgentClient: { removePeer: jest.fn() } }));
import { prisma } from '../db';
import { AgentClient } from '../services/agentClient';
import { expirePeers } from '../services/peerProvisioning';
const tx = { $executeRaw: jest.fn(), peerSession: { findFirst: jest.fn(), delete: jest.fn() } };
const row = { id: 'test-peer',serverId:'test-node',clientPublicKey:Buffer.alloc(32,8).toString('base64'),server:{active:true,agentUrl:'http://test.invalid',agentSecretHash:'env:NSEC_TEST_AGENT_KEY'},device:{isBlocked:false} };
beforeEach(() => {
  jest.clearAllMocks();
  (prisma.peerSession.findMany as jest.Mock).mockResolvedValue([{id:row.id,serverId:row.serverId}]);
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
