import crypto from 'crypto';
import { z } from 'zod';
import { prisma } from '../db';
import { preserveNonPurchaseGrant } from './purchaseReconciliation';

export const PromoText = z.string().trim().toUpperCase().regex(/^[A-Z0-9-]{6,32}$/);
const fields = {
  code: PromoText.optional(), label: z.string().trim().min(1).max(80),
  passDays: z.number().int().min(1).max(365).optional(),
  // Older consoles keep working. Redemption always uses exact canonical minutes.
  passMinutes: z.number().int().min(1).max(525600).optional(),
  maxRedemptions: z.number().int().min(1).max(1000000),
  startsAt: z.string().datetime().nullable().optional(),
  expiresAt: z.string().datetime().nullable().optional(),
  active: z.boolean().optional()
};
export const CreatePromo = z.object(fields).strict().superRefine((v, ctx) => {
  if (v.passDays === undefined && v.passMinutes === undefined)
    ctx.addIssue({ code: 'custom', path: ['passDays'], message: 'Duration is required' });
  if (v.passDays !== undefined && v.passMinutes !== undefined && v.passDays * 1440 !== v.passMinutes)
    ctx.addIssue({ code: 'custom', path: ['passDays'], message: 'Conflicting duration fields' });
  if (v.startsAt && v.expiresAt && Date.parse(v.startsAt) >= Date.parse(v.expiresAt))
    ctx.addIssue({ code: 'custom', path: ['expiresAt'], message: 'Expiry must follow start' });
});
export const UpdatePromo = z.object(fields).partial().strict().refine(v => Object.keys(v).length > 0, 'No changes provided');
export const BulkPromo = z.object({ count: z.number().int().min(1).max(100),
  label: fields.label, passDays: z.number().int().min(1).max(365),
  maxRedemptions: fields.maxRedemptions, startsAt: fields.startsAt, expiresAt: fields.expiresAt,
  active: fields.active }).strict().refine(v => !v.startsAt || !v.expiresAt || Date.parse(v.startsAt) < Date.parse(v.expiresAt), 'Expiry must follow start');
export const promoHash = (code: string) => crypto.createHash('sha256').update(PromoText.parse(code)).digest('hex');

export function promoStatus(p: { deletedAt?: Date | null; active: boolean; startsAt?: Date | null; expiresAt: Date | null; redemptionCount: number; maxRedemptions: number }, now = new Date()): string {
  if (p.deletedAt) return 'deleted';
  if (!p.active) return 'inactive';
  if (p.expiresAt && p.expiresAt <= now) return 'expired';
  if (p.redemptionCount >= p.maxRedemptions) return 'used-up';
  if (p.startsAt && p.startsAt > now) return 'scheduled';
  return 'active';
}

export function promoUpdate(existing: { codeHash: string; redemptionCount: number; startsAt: Date | null; expiresAt: Date | null }, input: z.infer<typeof UpdatePromo>) {
  if (input.code && promoHash(input.code) !== existing.codeHash) throw new Error('PROMO_CODE_IMMUTABLE');
  if (input.maxRedemptions !== undefined && input.maxRedemptions < existing.redemptionCount) throw new Error('PROMO_LIMIT_BELOW_USAGE');
  if (input.passDays !== undefined && input.passMinutes !== undefined && input.passDays * 1440 !== input.passMinutes) throw new Error('PROMO_DURATION_CONFLICT');
  const starts = input.startsAt === undefined ? existing.startsAt : input.startsAt ? new Date(input.startsAt) : null;
  const expires = input.expiresAt === undefined ? existing.expiresAt : input.expiresAt ? new Date(input.expiresAt) : null;
  if (starts && expires && starts >= expires) throw new Error('PROMO_DATE_RANGE_INVALID');
  const { code, startsAt, expiresAt, passDays, passMinutes, ...rest } = input;
  const minutes = passDays !== undefined ? passDays * 1440 : passMinutes;
  return { ...rest, ...(startsAt !== undefined ? { startsAt: starts } : {}), ...(expiresAt !== undefined ? { expiresAt: expires } : {}),
    ...(minutes !== undefined ? { passMinutes: minutes, passDays: minutes / 1440 } : {}) };
}

export async function redeemPromotion(deviceId: string, rawCode: string) {
  const hash = promoHash(rawCode);
  return prisma.$transaction(async tx => {
    const identity = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${identity.deviceId}`}))`;
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`promo:${hash}`}))`;
    const device = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
    const promo = await tx.promoCode.findUnique({ where: { codeHash: hash } });
    if (!promo || !promo.active || promo.deletedAt) throw new Error('PROMO_INVALID');
    const now = new Date();
    if (promo.startsAt && promo.startsAt > now) throw new Error('PROMO_NOT_STARTED');
    if (promo.expiresAt && promo.expiresAt <= now) throw new Error('PROMO_EXPIRED');
    if (await tx.promoRedemption.findUnique({ where: { promoId_deviceId: { promoId: promo.id, deviceId } } })) throw new Error('PROMO_ALREADY_REDEEMED');
    if (promo.redemptionCount >= promo.maxRedemptions) throw new Error('PROMO_EXHAUSTED');
    if (device.tier === 'VIP' && !device.passExpiresAt) throw new Error('PROMO_ALREADY_VIP');
    // Coupons extend non-purchase VIP only. A refunded paid year must never
    // become protected coupon time through stacking.
    const purchases = await tx.purchaseClaim.findMany({where:{deviceId}});
    const legacy = purchases.some(p=>!p.provenanceInitialized);
    const protectedExpiry = purchases.reduce((maximum,p)=>Math.max(maximum,p.protectedVipUntil?.getTime()||0),0);
    const base = purchases.length&&!legacy?protectedExpiry:device.passExpiresAt?.getTime()||0;
    const until = new Date(Math.max(now.getTime(), base) + promo.passMinutes * 60000);
    const paidExpiry = purchases.reduce((maximum,p)=>Math.max(maximum,p.active&&!p.adminRevoked?p.expiresAt.getTime():0),0);
    const effectiveUntil = new Date(Math.max(until.getTime(),paidExpiry));
    await tx.promoRedemption.create({ data: { promoId: promo.id, deviceId, grantExpiresAt: until, durationMinutes: promo.passMinutes } });
    await tx.promoCode.update({ where: { id: promo.id }, data: { redemptionCount: { increment: 1 } } });
    const updated = await tx.device.update({ where: { id: deviceId }, data: { tier: 'VIP', passExpiresAt: effectiveUntil } });
    await preserveNonPurchaseGrant(tx,deviceId,until);
    return { tier: updated.tier, passExpiresAt: updated.passExpiresAt };
  }, { timeout: 10000 });
}
