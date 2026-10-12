jest.mock('../db', () => ({ prisma: { $transaction: jest.fn() } }));
import { prisma } from '../db';
import { claimReward } from '../services/rewardClaims';
const tx = { $executeRaw: jest.fn(), device: { findUniqueOrThrow: jest.fn(), update: jest.fn() },
  rewardTicket: { findUnique: jest.fn(), findFirst: jest.fn(), update: jest.fn() }, appConfig: { findUnique: jest.fn() } };
beforeEach(() => {
  jest.clearAllMocks();
  (prisma.$transaction as jest.Mock).mockImplementation(work => work(tx));
  tx.device.findUniqueOrThrow.mockResolvedValue({ id: 'device-test', deviceId: 'installation-test', isBlocked: false, tier: 'FREE', passExpiresAt: null });
  tx.rewardTicket.findUnique.mockResolvedValue({ id: 'ticket-test', deviceId: 'device-test', expiresAt: new Date(Date.now() + 60000), claimedAt: null });
  tx.rewardTicket.findFirst.mockResolvedValue(null);
  tx.appConfig.findUnique.mockResolvedValue({ rewardedAdsEnabled: true, rewardedPassMinutes: 30 });
  tx.device.update.mockImplementation(({ data }) => Promise.resolve(data));
});
it('grants thirty-minute VIP on a valid authenticated one-use claim', async () => {
  const start = Date.now(); const result = await claimReward('device-test', 'ticket-test');
  expect(result.tier).toBe('VIP'); expect(result.passExpiresAt!.getTime()).toBeGreaterThanOrEqual(start + 1800000);
  expect(tx.rewardTicket.update.mock.invocationCallOrder[0]).toBeLessThan(tx.device.update.mock.invocationCallOrder[0]);
});
it('rejects another device’s ticket without granting access', async () => {
  tx.rewardTicket.findUnique.mockResolvedValue({ deviceId: 'other-device' });
  await expect(claimReward('device-test', 'ticket-test')).rejects.toThrow('REWARD_TICKET_INVALID');
  expect(tx.device.update).not.toHaveBeenCalled();
});
it('replays are idempotent and do not extend the original entitlement', async () => {
  tx.rewardTicket.findUnique.mockResolvedValue({ deviceId: 'device-test', claimedAt: new Date() });
  await claimReward('device-test', 'ticket-test');
  expect(tx.device.update).not.toHaveBeenCalled(); expect(tx.rewardTicket.update).not.toHaveBeenCalled();
});
it('expired tickets and rapid repeat claims cannot grant VIP', async () => {
  tx.rewardTicket.findUnique.mockResolvedValueOnce({ deviceId: 'device-test', claimedAt: null, expiresAt: new Date(0) });
  await expect(claimReward('device-test', 'ticket-test')).rejects.toThrow('REWARD_TICKET_EXPIRED');
  tx.rewardTicket.findFirst.mockResolvedValue({ id: 'recent-ticket' });
  await expect(claimReward('device-test', 'ticket-test')).rejects.toThrow('REWARD_COOLDOWN');
  expect(tx.device.update).not.toHaveBeenCalled();
});
it('never shortens a longer paid VIP entitlement', async () => {
  const until = new Date(Date.now() + 365 * 86400000);
  tx.device.findUniqueOrThrow.mockResolvedValue({ deviceId: 'installation-test', isBlocked: false, tier: 'VIP', passExpiresAt: until });
  const result = await claimReward('device-test', 'ticket-test');
  expect(result.passExpiresAt).toEqual(until); expect(tx.device.update).not.toHaveBeenCalled();
});
