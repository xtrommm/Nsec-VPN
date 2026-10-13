import { SubscriptionTier } from '@prisma/client';
import { prisma } from '../db';

type Snapshot = { id: string; tier: SubscriptionTier; passExpiresAt: Date | null; authEpoch: number; isBlocked: boolean };
/** Expire only the snapshot we observed. A concurrent grant/recovery must never be overwritten. */
export async function expireEntitlement(device: Snapshot): Promise<Snapshot> {
  if (device.tier === 'FREE' || !device.passExpiresAt || device.passExpiresAt > new Date()) return device;
  const changed = await prisma.device.updateMany({
    where: { id: device.id, tier: device.tier, passExpiresAt: device.passExpiresAt, authEpoch: device.authEpoch },
    data: { tier: 'FREE', passExpiresAt: null }
  });
  if (changed.count) return { ...device, tier: 'FREE', passExpiresAt: null };
  const fresh = await prisma.device.findUnique({ where: { id: device.id } });
  if (!fresh) throw new Error('DEVICE_NOT_FOUND');
  if (fresh.authEpoch !== device.authEpoch) throw new Error('TOKEN_REVOKED');
  return fresh;
}
export function preserveVipExpiry(current: { tier: string; passExpiresAt: Date | null }, purchased: Date, promo?: Date | null): Date | null {
  if (current.tier === 'VIP' && !current.passExpiresAt) return null;
  return new Date(Math.max(purchased.getTime(), promo?.getTime() || 0, current.tier !== 'FREE' ? current.passExpiresAt?.getTime() || 0 : 0));
}
