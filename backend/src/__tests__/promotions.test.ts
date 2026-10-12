jest.mock('../db', () => ({ prisma: { $transaction: jest.fn() } }));
import { prisma } from '../db';
import { CreatePromo, promoHash, redeemPromotion } from '../services/promotions';
import { UpdateConfigSchema } from '../types';

const tx = { $executeRaw: jest.fn(), device: { findUniqueOrThrow: jest.fn(), update: jest.fn() },
  promoCode: { findUnique: jest.fn(), update: jest.fn() },
  promoRedemption: { findUnique: jest.fn(), create: jest.fn() } };
beforeEach(() => {
  jest.clearAllMocks();
  (prisma.$transaction as jest.Mock).mockImplementation(work => work(tx));
  tx.device.findUniqueOrThrow.mockResolvedValue({ id: 'device-test', deviceId: 'installation-test', isBlocked: false, tier: 'FREE', passExpiresAt: null });
  tx.promoCode.findUnique.mockResolvedValue({ id: 'promo-test', active: true, expiresAt: null, passMinutes: 30, redemptionCount: 0, maxRedemptions: 1 });
  tx.promoRedemption.findUnique.mockResolvedValue(null);
  tx.device.update.mockImplementation(({ data }) => Promise.resolve(data));
});
it('normalizes codes before hashing and rejects invalid identifiers', () => {
  expect(promoHash('  test-code  ')).toBe(promoHash('TEST-CODE'));
  expect(() => promoHash('bad')).toThrow();
  expect(() => CreatePromo.parse({ label: 'Test campaign', passMinutes: 0, maxRedemptions: 1 })).toThrow();
});
it('locks identities and campaigns before granting a time-limited pass', async () => {
  const now = Date.now(); const result = await redeemPromotion('device-test', 'TEST-CODE');
  expect(tx.$executeRaw).toHaveBeenCalledTimes(2);
  expect(tx.$executeRaw.mock.invocationCallOrder[1]).toBeLessThan(tx.promoRedemption.create.mock.invocationCallOrder[0]);
  expect(result.tier).toBe('VIP');
  expect(result.passExpiresAt!.getTime()).toBeGreaterThanOrEqual(now + 30 * 60000);
  expect(tx.promoCode.update).toHaveBeenCalledWith({ where: { id: 'promo-test' }, data: { redemptionCount: { increment: 1 } } });
});
it.each(['blocked', 'expired', 'duplicate', 'exhausted', 'disabled'])('rejects %s without changing entitlements', async kind => {
  if (kind === 'blocked') tx.device.findUniqueOrThrow.mockResolvedValue({ isBlocked: true, deviceId: 'installation-test' });
  if (kind === 'expired') tx.promoCode.findUnique.mockResolvedValue({ active: true, expiresAt: new Date(0) });
  if (kind === 'duplicate') tx.promoRedemption.findUnique.mockResolvedValue({ id: 'already-used' });
  if (kind === 'exhausted') tx.promoCode.findUnique.mockResolvedValue({ active: true, redemptionCount: 1, maxRedemptions: 1 });
  if (kind === 'disabled') tx.promoCode.findUnique.mockResolvedValue({ active: false });
  await expect(redeemPromotion('device-test', 'TEST-CODE')).rejects.toThrow();
  expect(tx.promoRedemption.create).not.toHaveBeenCalled(); expect(tx.device.update).not.toHaveBeenCalled();
});
it('does not consume a campaign use for lifetime VIP', async () => {
  tx.device.findUniqueOrThrow.mockResolvedValue({ deviceId: 'installation-test', isBlocked: false, tier: 'VIP', passExpiresAt: null });
  await expect(redeemPromotion('device-test', 'TEST-CODE')).rejects.toThrow('PROMO_ALREADY_VIP');
  expect(tx.device.update).not.toHaveBeenCalled();expect(tx.promoRedemption.create).not.toHaveBeenCalled();
});
it('does not update entitlements after a redemption insertion fails', async () => {
  tx.promoRedemption.create.mockRejectedValueOnce(new Error('database unavailable'));
  await expect(redeemPromotion('device-test', 'TEST-CODE')).rejects.toThrow();
  expect(tx.device.update).not.toHaveBeenCalled(); expect(tx.promoCode.update).not.toHaveBeenCalled();
});
it('persists native-ad controls and only accepts production ad-unit identifiers', () => {
  expect(UpdateConfigSchema.parse({ nativeAdsEnabled: false, rewardedPassMinutes: 30 })).toEqual({ nativeAdsEnabled: false, rewardedPassMinutes: 30 });
  expect(() => UpdateConfigSchema.parse({ rewardedAdUnitId: 'invalid' })).toThrow();
  expect(() => UpdateConfigSchema.parse({ rewardedAdUnitId: 'ca-app-pub-3940256099942544/5224354917' })).toThrow();
  expect(() => UpdateConfigSchema.parse({ rewardedPassMinutes: 0 })).toThrow();
  expect(() => UpdateConfigSchema.parse({ id: 'arbitrary-config' })).toThrow();
});

it('extends finite VIP by the exact campaign duration instead of consuming an unusable code', async () => {
 const expiry=new Date(Date.now()+86400000);
 tx.device.findUniqueOrThrow.mockResolvedValue({deviceId:'installation-test',isBlocked:false,tier:'VIP',passExpiresAt:expiry});
 const r=await redeemPromotion('device-test','TEST-CODE');
 expect(r.passExpiresAt!.getTime()).toBe(expiry.getTime()+30*60000);
 expect(tx.promoRedemption.create).toHaveBeenCalledWith(expect.objectContaining({data:expect.objectContaining({grantExpiresAt:r.passExpiresAt,durationMinutes:30})}));
});
it.each(['scheduled','deleted'])('rejects %s campaigns before consuming a use',async kind=>{
 tx.promoCode.findUnique.mockResolvedValue({active:true,startsAt:kind==='scheduled'?new Date(Date.now()+600000):null,deletedAt:kind==='deleted'?new Date():null});
 await expect(redeemPromotion('device-test','TEST-CODE')).rejects.toThrow(kind==='scheduled'?'PROMO_NOT_STARTED':'PROMO_INVALID');
 expect(tx.promoRedemption.create).not.toHaveBeenCalled();
});
