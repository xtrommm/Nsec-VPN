import { Router } from 'express';
import crypto from 'crypto';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { prisma } from '../db';
import { authenticateAdmin, authenticateDevice, AuthenticatedAdminRequest, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { CreatePromo, UpdatePromo, PromoText, promoHash, redeemPromotion } from '../services/promotions';

export const adminPromos = Router();
export const devicePromos = Router();
adminPromos.use(authenticateAdmin());
const promoSelect = { id: true, label: true, passMinutes: true, maxRedemptions: true,
  redemptionCount: true, expiresAt: true, active: true, createdAt: true };

adminPromos.get('/', async (_req, res) => {
  try {
    const promos = await prisma.promoCode.findMany({ select: promoSelect, orderBy: { createdAt: 'desc' }, take: 500 });
    return res.json({ success: true, promos });
  } catch { return res.status(503).json({ success: false, error: 'PROMOS_UNAVAILABLE' }); }
});
adminPromos.post('/', authenticateAdmin('SUPER_ADMIN'), validateBody(CreatePromo), async (req: AuthenticatedAdminRequest, res) => {
  try {
    const { code: chosen, expiresAt, ...data } = CreatePromo.parse(req.body);
    if (expiresAt && Date.parse(expiresAt) <= Date.now()) return res.status(400).json({ success: false, error: 'PROMO_EXPIRY_INVALID' });
    const code = chosen || crypto.randomBytes(12).toString('hex').toUpperCase();
    const promo = await prisma.$transaction(async tx => {
      const result = await tx.promoCode.create({ data: { ...data, codeHash: promoHash(code), expiresAt: expiresAt ? new Date(expiresAt) : null }, select: promoSelect });
      await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'CREATE_PROMO', targetResource: 'PROMO', targetId: result.id } });
      return result;
    });
    res.setHeader('Cache-Control', 'no-store');
    return res.status(201).json({ success: true, promo, code });
  } catch (error) {
    return res.status((error as { code?: string }).code === 'P2002' ? 409 : 503).json({ success: false,
      error: (error as { code?: string }).code === 'P2002' ? 'PROMO_CODE_EXISTS' : 'PROMO_CREATE_FAILED' });
  }
});
adminPromos.put('/:id', authenticateAdmin('SUPER_ADMIN'), validateBody(UpdatePromo), async (req: AuthenticatedAdminRequest, res) => {
  try {
    const promo = await prisma.$transaction(async tx => {
      const existing = await tx.promoCode.findUniqueOrThrow({ where: { id: req.params.id } });
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`promo:${existing.codeHash}`}))`;
      const result = await tx.promoCode.update({ where: { id: existing.id }, data: UpdatePromo.parse(req.body), select: promoSelect });
      await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: req.body.active ? 'ENABLE_PROMO' : 'DISABLE_PROMO', targetResource: 'PROMO', targetId: result.id } });
      return result;
    });
    return res.json({ success: true, promo });
  } catch { return res.status(404).json({ success: false, error: 'PROMO_NOT_FOUND' }); }
});

devicePromos.post('/redeem', authenticateDevice, rateLimit({
  windowMs: 3600000, max: 20, standardHeaders: true, legacyHeaders: false,
  keyGenerator: req => (req as AuthenticatedDeviceRequest).device!.id,
  message: { success: false, error: 'PROMO_RATE_LIMIT' }
}), validateBody(z.object({ code: PromoText }).strict()), async (req: AuthenticatedDeviceRequest, res) => {
  try { return res.json({ success: true, data: await redeemPromotion(req.device!.id, req.body.code) }); }
  catch (error) {
    const code = error instanceof Error ? error.message : '';
    const status: Record<string, number> = { DEVICE_BLOCKED: 403, PROMO_INVALID: 404,
      PROMO_EXPIRED: 410, PROMO_ALREADY_REDEEMED: 409, PROMO_EXHAUSTED: 409 };
    return res.status(status[code] || 503).json({ success: false, error: status[code] ? code : 'PROMO_REDEMPTION_UNAVAILABLE' });
  }
});
