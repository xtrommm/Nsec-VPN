import { Router } from 'express';
import crypto from 'crypto';
import { Prisma } from '@prisma/client';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { prisma } from '../db';
import { authenticateAdmin, authenticateDevice, AuthenticatedAdminRequest, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { CreatePromo, UpdatePromo, BulkPromo, PromoText, archivedPromoHash, promoHash, promoStatus, promoUpdate, redeemPromotion } from '../services/promotions';
import { encryptPromo, decryptPromo } from '../services/promoCipher';
import { PageQuery, pagination, databaseError } from '../services/consoleQuery';

export const adminPromos = Router();
export const devicePromos = Router();
adminPromos.use(authenticateAdmin());
adminPromos.use((_req, res, next) => { res.setHeader('Cache-Control', 'no-store'); next(); });
const promoSelect = { id: true, label: true, passMinutes: true, passDays: true, maxRedemptions: true,
  redemptionCount: true, startsAt: true, expiresAt: true, active: true, deletedAt: true, createdAt: true, updatedAt: true, codeCiphertext: true } as const;
function safe(p: Prisma.PromoCodeGetPayload<{ select: typeof promoSelect }>) {
  const { codeCiphertext, ...record } = p;
  return { ...record, passDays: Number(p.passDays), status: promoStatus(p), remainingUses: Math.max(0, p.maxRedemptions - p.redemptionCount), codeAvailable: !!codeCiphertext };
}
function fail(res: any, error: unknown, fallback: string) {
  const code = error instanceof Error ? error.message : '';
  const statuses: Record<string, number> = { PROMO_CODE_EXISTS: 409, PROMO_ENCRYPTION_NOT_CONFIGURED: 503, PROMO_CODE_UNAVAILABLE: 409,
    PROMO_CODE_IMMUTABLE: 400, PROMO_LIMIT_BELOW_USAGE: 409, PROMO_DATE_RANGE_INVALID: 400, PROMO_DURATION_CONFLICT: 400 };
  if (databaseError(error) === 'P2002') return res.status(409).json({ success: false, error: 'PROMO_CODE_EXISTS' });
  if (databaseError(error) === 'P2025') return res.status(404).json({ success: false, error: 'PROMO_NOT_FOUND' });
  return res.status(statuses[code] || 503).json({ success: false, error: statuses[code] ? code : fallback });
}
const listQuery = PageQuery.extend({ status: z.enum(['all', 'active', 'scheduled', 'inactive', 'expired', 'used-up', 'deleted']).default('all') });
adminPromos.get('/', async (req, res) => {
  const parsed = listQuery.safeParse(req.query);
  if (!parsed.success) return res.status(400).json({ success: false, error: 'INVALID_QUERY' });
  try {
    const { page, limit, search, sort, status } = parsed.data;
    const now = new Date();
    const where: Prisma.PromoCodeWhereInput = { deletedAt: status === 'deleted' ? { not: null } : null };
    if (search) where.label = { contains: search, mode: 'insensitive' };
    if (status === 'inactive') where.active = false;
    if (status === 'expired') where.AND = [{ active: true }, { expiresAt: { lte: now } }];
    if (status === 'used-up') where.AND = [{ active: true }, { OR: [{ expiresAt: null }, { expiresAt: { gt: now } }] }, { redemptionCount: { gte: prisma.promoCode.fields.maxRedemptions } }];
    if (status === 'active' || status === 'scheduled') where.AND = [{ active: true }, { OR: [{ expiresAt: null }, { expiresAt: { gt: now } }] },
      { redemptionCount: { lt: prisma.promoCode.fields.maxRedemptions } }, status === 'scheduled' ? { startsAt: { gt: now } } : { OR: [{ startsAt: null }, { startsAt: { lte: now } }] }];
    // Hash comparison enables exact code search without decrypting the catalog.
    if (search && PromoText.safeParse(search).success) {
      delete where.label;
      where.OR = [{ label: { contains: search, mode: 'insensitive' } }, { codeHash: promoHash(search) }];
    }
    const orderBy: Prisma.PromoCodeOrderByWithRelationInput = sort === 'name' ? { label: 'asc' } : { createdAt: sort === 'oldest' ? 'asc' : 'desc' };
    const [total, promos] = await prisma.$transaction([prisma.promoCode.count({ where }), prisma.promoCode.findMany({ where, select: promoSelect, orderBy: [orderBy, { id: 'asc' }], skip: (page - 1) * limit, take: limit })]);
    return res.json({ success: true, promos: promos.map(safe), pagination: pagination(total, page, limit) });
  } catch (e) { return fail(res, e, 'PROMOS_UNAVAILABLE'); }
});
async function create(req: AuthenticatedAdminRequest, res: any, bulk: boolean) {
  try {
    const body = bulk ? BulkPromo.parse(req.body) : CreatePromo.parse(req.body);
    const count = 'count' in body ? body.count : 1;
    const minutes = body.passDays !== undefined ? body.passDays * 1440 : ('passMinutes' in body ? body.passMinutes! : 0);
    const chosen = 'code' in body ? body.code : undefined;
    const startsAt = body.startsAt ? new Date(body.startsAt) : null;
    const expiresAt = body.expiresAt ? new Date(body.expiresAt) : null;
    if (expiresAt && expiresAt <= new Date()) return res.status(400).json({ success: false, error: 'PROMO_EXPIRY_INVALID' });
    // Validate encryption before any database writes. Never log raw codes.
    const prepared = Array.from({ length: count }, () => { const code = chosen || crypto.randomBytes(12).toString('hex').toUpperCase(); return { code, cipher: encryptPromo(code) }; });
    const created = await prisma.$transaction(async tx => {
      const records = [];
      for (const item of prepared) {
        const hash = promoHash(item.code);
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`promo:${hash}`}))`;
        const previous = await tx.promoCode.findUnique({ where: { codeHash: hash } });
        if (previous && !previous.deletedAt) throw new Error('PROMO_CODE_EXISTS');
        if (previous) await tx.promoCode.update({ where: { id: previous.id }, data: { codeHash: archivedPromoHash(previous.id) } });
        const p = await tx.promoCode.create({ data: { label: body.label, passMinutes: minutes, passDays: minutes / 1440, startsAt, expiresAt,
          maxRedemptions: body.maxRedemptions, active: body.active ?? true, codeHash: promoHash(item.code), codeCiphertext: item.cipher }, select: promoSelect });
        await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'CREATE_PROMO', targetResource: 'PROMO', targetId: p.id } });
        records.push({ ...safe(p), code: item.code });
      }
      return records;
    }, { timeout: 15000 });
    return res.status(201).json(bulk ? { success: true, promos: created } : { success: true, promo: created[0], code: created[0].code });
  } catch (e) { return fail(res, e, 'PROMO_CREATE_FAILED'); }
}
adminPromos.post('/bulk', authenticateAdmin('SUPER_ADMIN'), validateBody(BulkPromo), (req: AuthenticatedAdminRequest, res) => create(req, res, true));
adminPromos.post('/', authenticateAdmin('SUPER_ADMIN'), validateBody(CreatePromo), (req: AuthenticatedAdminRequest, res) => create(req, res, false));
adminPromos.get('/:id/code', authenticateAdmin('SUPER_ADMIN'), async (req: AuthenticatedAdminRequest, res) => {
  try {
    const p = await prisma.promoCode.findFirst({ where: { id: req.params.id, deletedAt: null } });
    if (!p) return res.status(404).json({ success: false, error: 'PROMO_NOT_FOUND' });
    if (!p.codeCiphertext) return res.status(409).json({ success: false, error: 'PROMO_LEGACY_CODE_UNAVAILABLE', message: 'Supply the original matching code when editing; hashes cannot be reversed.' });
    const code = decryptPromo(p.codeCiphertext);
    await prisma.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'VIEW_PROMO_CODE', targetResource: 'PROMO', targetId: p.id } });
    return res.json({ success: true, code });
  } catch (e) { return fail(res, e, 'PROMO_CODE_UNAVAILABLE'); }
});
adminPromos.get('/:id/redemptions', async (req, res) => {
  const query = PageQuery.safeParse(req.query);
  if (!query.success) return res.status(400).json({ success: false, error: 'INVALID_QUERY' });
  try {
    const promo = await prisma.promoCode.findUnique({ where: { id: req.params.id }, select: promoSelect });
    if (!promo) return res.status(404).json({ success: false, error: 'PROMO_NOT_FOUND' });
    const { page, limit, search } = query.data;
    const where: Prisma.PromoRedemptionWhereInput = { promoId: promo.id, ...(search ? { device: { deviceId: { contains: search, mode: 'insensitive' } } } : {}) };
    const [total, redemptions] = await prisma.$transaction([prisma.promoRedemption.count({ where }), prisma.promoRedemption.findMany({ where, orderBy: [{ redeemedAt: 'desc' }, { id: 'asc' }], skip: (page - 1) * limit, take: limit,
      select: { id: true, redeemedAt: true, grantExpiresAt: true, durationMinutes: true, device: { select: { deviceId: true } } } })]);
    return res.json({ success: true, promo: safe(promo), redemptions, pagination: pagination(total, page, limit) });
  } catch (e) { return fail(res, e, 'PROMO_USAGE_UNAVAILABLE'); }
});
adminPromos.put('/:id', authenticateAdmin('SUPER_ADMIN'), validateBody(UpdatePromo), async (req: AuthenticatedAdminRequest, res) => {
  try {
    const input = UpdatePromo.parse(req.body);
    const cipher = input.code ? encryptPromo(input.code) : undefined;
    const p = await prisma.$transaction(async tx => {
      const first = await tx.promoCode.findFirstOrThrow({ where: { id: req.params.id, deletedAt: null } });
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`promo:${first.codeHash}`}))`;
      const existing = await tx.promoCode.findFirstOrThrow({ where: { id: first.id, deletedAt: null } });
      const data = promoUpdate(existing, input);
      const result = await tx.promoCode.update({ where: { id: existing.id }, data: { ...data, ...(cipher ? { codeCiphertext: cipher } : {}) }, select: promoSelect });
      await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'UPDATE_PROMO', targetResource: 'PROMO', targetId: result.id, metadata: JSON.stringify({ fields: Object.keys(input).filter(k => k !== 'code') }) } });
      return result;
    });
    return res.json({ success: true, promo: safe(p) });
  } catch (e) { return fail(res, e, 'PROMO_UPDATE_FAILED'); }
});
adminPromos.delete('/:id', authenticateAdmin('SUPER_ADMIN'), async (req: AuthenticatedAdminRequest, res) => {
  try {
    await prisma.$transaction(async tx => {
      const p = await tx.promoCode.findUniqueOrThrow({ where: { id: req.params.id } });
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`promo:${p.codeHash}`}))`;
      await tx.promoCode.update({ where: { id: p.id }, data: { active: false, deletedAt: p.deletedAt || new Date(), codeCiphertext: null, codeHash: archivedPromoHash(p.id) } });
      await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'DELETE_PROMO', targetResource: 'PROMO', targetId: p.id } });
    });
    return res.json({ success: true });
  } catch (e) { return fail(res, e, 'PROMO_DELETE_FAILED'); }
});
devicePromos.post('/redeem', authenticateDevice, rateLimit({ windowMs: 3600000, max: 20, standardHeaders: true, legacyHeaders: false,
  keyGenerator: req => (req as AuthenticatedDeviceRequest).device!.id, message: { success: false, error: 'PROMO_RATE_LIMIT' }
}), validateBody(z.object({ code: PromoText }).strict()), async (req: AuthenticatedDeviceRequest, res) => {
  try { return res.json({ success: true, data: await redeemPromotion(req.device!.id, req.body.code) }); }
  catch (e) {
    const code = e instanceof Error ? e.message : '';
    const status: Record<string, number> = { DEVICE_BLOCKED: 403, PROMO_INVALID: 404, PROMO_NOT_STARTED: 409, PROMO_ALREADY_VIP: 409,
      PROMO_EXPIRED: 410, PROMO_ALREADY_REDEEMED: 409, PROMO_EXHAUSTED: 409 };
    return res.status(status[code] || 503).json({ success: false, error: status[code] ? code : 'PROMO_REDEMPTION_UNAVAILABLE' });
  }
});
