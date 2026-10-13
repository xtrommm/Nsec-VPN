jest.mock('../db', () => ({ prisma: { device: { updateMany: jest.fn(), findUnique: jest.fn() } } }));
import { prisma } from '../db';
import { expireEntitlement, preserveVipExpiry } from '../services/entitlements';
const expired = { id: 'device', authEpoch: 0, isBlocked: false, tier: 'VIP' as const, passExpiresAt: new Date(1) };
beforeEach(() => jest.clearAllMocks());
it('a grant racing expiry is read back and cannot be overwritten', async () => {
  const granted = { ...expired, passExpiresAt: new Date(Date.now()+86400000) };
  (prisma.device.updateMany as jest.Mock).mockResolvedValue({count:0});
  (prisma.device.findUnique as jest.Mock).mockResolvedValue(granted);
  expect(await expireEntitlement(expired)).toEqual(granted);
  expect(prisma.device.updateMany).toHaveBeenCalledWith(expect.objectContaining({where:{id:expired.id,tier:'VIP',passExpiresAt:expired.passExpiresAt,authEpoch:0}}));
});
it('successful expiry returns FREE with no stale expired timestamp', async () => {
  (prisma.device.updateMany as jest.Mock).mockResolvedValue({count:1});
  expect(await expireEntitlement(expired)).toEqual({...expired,tier:'FREE',passExpiresAt:null});
});
it('recovery racing expiry cannot upgrade an old credential to the new auth epoch', async () => {
  (prisma.device.updateMany as jest.Mock).mockResolvedValue({count:0});
  (prisma.device.findUnique as jest.Mock).mockResolvedValue({...expired,authEpoch:1});
  await expect(expireEntitlement(expired)).rejects.toThrow('TOKEN_REVOKED');
});
it('an active grant never triggers expiry writes', async () => {
  const active = {...expired,passExpiresAt:new Date(Date.now()+86400000)};
  expect(await expireEntitlement(active)).toBe(active);
  expect(prisma.device.updateMany).not.toHaveBeenCalled();
});
it('purchases preserve a longer promo, reward or admin grant and lifetime VIP', () => {
  const purchase = new Date(Date.now()+1000), longer = new Date(Date.now()+86400000);
  expect(preserveVipExpiry({...expired,passExpiresAt:longer},purchase)).toEqual(longer);
  expect(preserveVipExpiry({...expired,passExpiresAt:new Date(1)},purchase,longer)).toEqual(longer);
  expect(preserveVipExpiry({...expired,passExpiresAt:null},purchase)).toBeNull();
  expect(preserveVipExpiry({tier:'FREE',passExpiresAt:null},purchase)).toEqual(purchase);
});
