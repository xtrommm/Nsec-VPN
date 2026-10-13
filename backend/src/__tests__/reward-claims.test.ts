jest.mock('../db', () => ({ prisma: { $transaction: jest.fn() } }));
import { prisma } from '../db';
import { claimReward, grantVerifiedReward, prepareReward } from '../services/rewardClaims';
const tx = { $executeRaw: jest.fn(), device: { findUniqueOrThrow: jest.fn(), update: jest.fn() },
  rewardTicket: { findUnique: jest.fn(), findFirst: jest.fn(), update: jest.fn(), create: jest.fn() },
  rewardGrant: { create: jest.fn() }, appConfig: { findUnique: jest.fn() } };
const now = new Date();
const reward = { deviceId: 'installation-test', transactionId: 'signed-transaction-test', timestamp: now.getTime(), ticketId: 'ticket-test' };
const ticket = { id: 'ticket-test', deviceId: 'device-test', createdAt: new Date(now.getTime() - 60000), expiresAt: new Date(now.getTime() + 60000), claimedAt: null };
beforeEach(() => {
  jest.clearAllMocks();
  (prisma.$transaction as jest.Mock).mockImplementation(work => work(tx));
  tx.device.findUniqueOrThrow.mockResolvedValue({ id: 'device-test', deviceId: 'installation-test', isBlocked: false, tier: 'FREE', passExpiresAt: null });
  tx.rewardTicket.findUnique.mockResolvedValue(ticket);
  tx.rewardTicket.findFirst.mockResolvedValue(null);
  tx.rewardGrant.create.mockResolvedValue({ transactionId: reward.transactionId });
  tx.appConfig.findUnique.mockResolvedValue({ adsEnabled: true, rewardedAdsEnabled: true, rewardedPassMinutes: 45 });
});
it('an authenticated client cannot grant itself VIP without signed verification', async () => {
  await expect(claimReward('device-test', 'ticket-test')).rejects.toThrow('REWARD_VERIFICATION_PENDING');
  expect(tx.device.update).not.toHaveBeenCalled(); expect(tx.rewardTicket.update).not.toHaveBeenCalled();
});
it('a verified event consumes its ticket and grants exactly the configured duration', async () => {
  await grantVerifiedReward(reward);
  expect(tx.device.update).toHaveBeenCalledWith({ where: { id: 'device-test' }, data: { tier: 'VIP', passExpiresAt: new Date(now.getTime() + 45 * 60000) } });
  expect(tx.rewardTicket.update.mock.invocationCallOrder[0]).toBeLessThan(tx.device.update.mock.invocationCallOrder[0]);
});
it('rejects another device ticket and expired client claims', async () => {
  tx.rewardTicket.findUnique.mockResolvedValueOnce({ ...ticket, deviceId: 'other' });
  await expect(claimReward('device-test', 'ticket-test')).rejects.toThrow('REWARD_TICKET_INVALID');
  tx.rewardTicket.findUnique.mockResolvedValueOnce({ ...ticket, expiresAt: new Date(0) });
  await expect(claimReward('device-test', 'ticket-test')).rejects.toThrow('REWARD_TICKET_EXPIRED');
  expect(tx.device.update).not.toHaveBeenCalled();
});
it('reading a verified grant or delivering another event for its consumed ticket never extends it', async () => {
  tx.rewardTicket.findUnique.mockResolvedValue({ ...ticket, claimedAt: now });
  await claimReward('device-test', 'ticket-test'); await grantVerifiedReward(reward);
  expect(tx.device.update).not.toHaveBeenCalled(); expect(tx.rewardTicket.update).not.toHaveBeenCalled();
});
it('rejects a signed event for a different device or outside the issued ticket window', async () => {
  tx.rewardTicket.findUnique.mockResolvedValueOnce({ ...ticket, deviceId: 'other' });
  await expect(grantVerifiedReward(reward)).rejects.toThrow('SSV_TICKET_INVALID');
  await expect(grantVerifiedReward({ ...reward, timestamp: now.getTime() + 120000 })).rejects.toThrow('SSV_TICKET_INVALID');
  expect(tx.device.update).not.toHaveBeenCalled();
});
it('callback transaction replay cannot grant twice', async () => {
  tx.rewardGrant.create.mockRejectedValueOnce(Object.assign(new Error('duplicate'), { code: 'P2002' }));
  await expect(grantVerifiedReward(reward)).rejects.toMatchObject({ code: 'P2002' });
  expect(tx.device.update).not.toHaveBeenCalled(); expect(tx.rewardTicket.update).not.toHaveBeenCalled();
});
it('global and placement kill switches reject preparation and signed grants', async () => {
  for (const config of [{ adsEnabled: false, rewardedAdsEnabled: true }, { adsEnabled: true, rewardedAdsEnabled: false }]) {
    tx.appConfig.findUnique.mockResolvedValue(config);
    await expect(prepareReward('device-test')).rejects.toThrow('REWARDED_ADS_DISABLED');
    await expect(grantVerifiedReward(reward)).rejects.toThrow('REWARDED_ADS_DISABLED');
  }
  expect(tx.device.update).not.toHaveBeenCalled();
});
it('never shortens longer finite or indefinite verified VIP', async () => {
  for (const expiry of [new Date(now.getTime() + 86400000), null]) {
    tx.device.findUniqueOrThrow.mockResolvedValue({ id: 'device-test', deviceId: reward.deviceId, tier: 'VIP', passExpiresAt: expiry });
    await grantVerifiedReward(reward);
  }
  expect(tx.device.update).not.toHaveBeenCalled();
});
it('delayed callbacks use event time and do not revive already expired rewards', async () => {
  const old = now.getTime() - 2 * 3600000;
  tx.rewardTicket.findUnique.mockResolvedValue({ ...ticket, createdAt: new Date(old - 60000), expiresAt: new Date(old + 60000) });
  await grantVerifiedReward({ ...reward, timestamp: old });
  expect(tx.device.update).not.toHaveBeenCalled();
});
it('rapid repeat events cannot extend access and preparing a new ticket enforces cooldown', async () => {
  tx.rewardTicket.findFirst.mockResolvedValue({ id: 'recent-ticket' });
  await expect(grantVerifiedReward(reward)).rejects.toThrow('REWARD_COOLDOWN');
  tx.rewardTicket.findFirst.mockResolvedValueOnce(null).mockResolvedValueOnce({ id: 'recent-ticket' });
  await expect(prepareReward('device-test')).rejects.toThrow('REWARD_COOLDOWN');
  expect(tx.device.update).not.toHaveBeenCalled();
});
it('legacy callbacks still require a verified Google event but need no client ticket', async () => {
  await grantVerifiedReward({ ...reward, ticketId: null });
  expect(tx.device.update).toHaveBeenCalled(); expect(tx.rewardTicket.update).not.toHaveBeenCalled();
});
