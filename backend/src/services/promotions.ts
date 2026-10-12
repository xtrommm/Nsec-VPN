import crypto from 'crypto';
import { z } from 'zod';
import { prisma } from '../db';

export const PromoText = z.string().trim().toUpperCase().regex(/^[A-Z0-9-]{6,32}$/);
export const CreatePromo = z.object({
  code: PromoText.optional(), label: z.string().trim().min(1).max(80),
  passMinutes: z.number().int().min(1).max(525600),
  maxRedemptions: z.number().int().min(1).max(1000000),
  expiresAt: z.string().datetime().nullable().default(null),
  active: z.boolean().default(true)
}).strict();
export const UpdatePromo = z.object({ active: z.boolean() }).strict();
export const promoHash = (code: string) => crypto.createHash('sha256').update(PromoText.parse(code)).digest('hex');

export async function redeemPromotion(deviceId: string, rawCode: string) {
  const hash = promoHash(rawCode);
  return prisma.$transaction(async tx => {
    const identity = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${identity.deviceId}`}))`;
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`promo:${hash}`}))`;
    const device = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
    const promo = await tx.promoCode.findUnique({ where: { codeHash: hash } });
    if (!promo || !promo.active) throw new Error('PROMO_INVALID');
    if (promo.expiresAt && promo.expiresAt <= new Date()) throw new Error('PROMO_EXPIRED');
    if (await tx.promoRedemption.findUnique({ where: { promoId_deviceId: { promoId: promo.id, deviceId } } }))
      throw new Error('PROMO_ALREADY_REDEEMED');
    if (promo.redemptionCount >= promo.maxRedemptions) throw new Error('PROMO_EXHAUSTED');
    await tx.promoRedemption.create({ data: { promoId: promo.id, deviceId } });
    await tx.promoCode.update({ where: { id: promo.id }, data: { redemptionCount: { increment: 1 } } });
    // Never replace a paid or lifetime VIP entitlement with a shorter promotion.
    if (device.tier === 'VIP' && (!device.passExpiresAt || device.passExpiresAt > new Date()))
      return { tier: device.tier, passExpiresAt: device.passExpiresAt };
    const until = new Date(Math.max(Date.now(), device.passExpiresAt?.getTime() || 0) + promo.passMinutes * 60000);
    const updated = await tx.device.update({ where: { id: deviceId }, data: { tier: 'VIP', passExpiresAt: until } });
    return { tier: updated.tier, passExpiresAt: updated.passExpiresAt };
  }, { timeout: 10000 });
}
