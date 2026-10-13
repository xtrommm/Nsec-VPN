import { Router } from 'express';
import { z } from 'zod';
import rateLimit from 'express-rate-limit';
import { prisma } from '../db';
import { authenticateAdmin, AuthenticatedAdminRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { beginAdminMfa, enableAdminMfa } from '../services/adminMfa';
const router = Router();
router.use(authenticateAdmin());
const limiter = rateLimit({ windowMs: 300000, max: 10, standardHeaders: true, legacyHeaders: false,
  keyGenerator: req => (req as AuthenticatedAdminRequest).admin!.adminId,
  message: { success: false, error: 'MFA_RATE_LIMIT' } });
router.get('/status', async (req: AuthenticatedAdminRequest, res) => {
  try {
    const admin = await prisma.adminUser.findUniqueOrThrow({ where: { id: req.admin!.adminId }, select: { totpEnabled: true } });
    return res.json({ success: true, enabled: admin.totpEnabled,
      enrollmentConfigured: /^[a-f0-9]{64}$/i.test(process.env.ADMIN_TOTP_ENCRYPTION_KEY || '') });
  } catch { return res.status(503).json({ success: false, error: 'MFA_UNAVAILABLE' }); }
});
const password = z.string().min(8).max(128);
const fail = (res: import('express').Response, error: unknown) => {
  const code = error instanceof Error ? error.message : '';
  const codes: Record<string, number> = { REAUTHENTICATION_REQUIRED: 403, TOTP_ALREADY_ENABLED: 409,
    TOTP_ENROLLMENT_EXPIRED: 410, TOTP_INVALID: 400, TOTP_ENCRYPTION_NOT_CONFIGURED: 503 };
  return res.status(codes[code] || 503).json({ success: false, error: codes[code] ? code : 'MFA_UNAVAILABLE' });
};
router.post('/enrollment', limiter, validateBody(z.object({ password }).strict()), async (req: AuthenticatedAdminRequest, res) => {
  try { return res.status(201).json({ success: true, data: await beginAdminMfa(req.admin!.adminId, req.body.password) }); }
  catch (error) { return fail(res, error); }
});
router.put('/enable', limiter, validateBody(z.object({ password, code: z.string().regex(/^\d{6}$/) }).strict()), async (req: AuthenticatedAdminRequest, res) => {
  try { return res.json({ success: true, ...await enableAdminMfa(req.admin!.adminId, req.body.password, req.body.code) }); }
  catch (error) { return fail(res, error); }
});
export default router;
