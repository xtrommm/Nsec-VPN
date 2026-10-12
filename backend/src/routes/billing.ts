import { Router, Response } from 'express';
import crypto from 'crypto';
import { prisma } from '../db';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { VerifySubscriptionSchema } from '../types';
import { verifyReward } from '../services/rewardVerification';
import { PlayBillingService } from '../services/billing';
import { prepareReward, claimReward } from '../services/rewardClaims';
import { z } from 'zod';
import rateLimit from 'express-rate-limit';

const router = Router();
const rewardLimiter = rateLimit({ windowMs: 3600000, max: 20, standardHeaders: true, legacyHeaders: false,
  keyGenerator: req => (req as AuthenticatedDeviceRequest).device!.id,
  message: { success: false, error: 'REWARD_RATE_LIMIT' } });
router.post('/rewarded/prepare', authenticateDevice, rewardLimiter, async (req: AuthenticatedDeviceRequest, res) => {
  try { return res.json({ success: true, data: await prepareReward(req.device!.id) }); }
  catch (e) { const disabled = e instanceof Error && e.message === 'REWARDED_ADS_DISABLED'; return res.status(disabled ? 409 : 503).json({ success: false, error: disabled ? 'REWARDED_ADS_DISABLED' : 'REWARD_PREPARATION_UNAVAILABLE' }); }
});
router.post('/rewarded/claim', authenticateDevice, rewardLimiter,
  validateBody(z.object({ ticketId: z.string().uuid() }).strict()), async (req: AuthenticatedDeviceRequest, res) => {
  try { return res.json({ success: true, data: await claimReward(req.device!.id, req.body.ticketId) }); }
  catch (error) {
    const code = error instanceof Error ? error.message : '';
    const errors: Record<string, number> = { DEVICE_BLOCKED: 403, REWARD_TICKET_INVALID: 404, REWARD_TICKET_EXPIRED: 410,
      REWARD_COOLDOWN: 429, REWARDED_ADS_DISABLED: 409 };
    return res.status(errors[code] || 503).json({ success: false, error: errors[code] ? code : 'REWARD_CLAIM_UNAVAILABLE' });
  }
});
router.get('/rewarded/ssv', async (req, res) => {
  try {
    const reward = await verifyReward(req.originalUrl.split('?')[1] || '');
    await prisma.$transaction(async tx => {
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${reward.deviceId}`}))`;
      const device = await tx.device.findUniqueOrThrow({ where: { deviceId: reward.deviceId } });
      if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
      if (reward.ticketId) {
        const ticket = await tx.rewardTicket.findUnique({ where: { id: reward.ticketId } });
        if (!ticket || ticket.deviceId !== device.id) throw new Error('SSV_TICKET_INVALID');
      }
      await tx.rewardGrant.create({ data: { transactionId: reward.transactionId } });
      if (device.tier !== 'VIP' || (device.passExpiresAt && device.passExpiresAt < new Date())) {
        const config = await tx.appConfig.findUnique({ where: { id: 'global_config' } });
        const minutes = config?.rewardedPassMinutes ?? Number(process.env.REWARDED_PASS_MINUTES || '30');
        if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) throw new Error('REWARD_DURATION_INVALID');
        await tx.device.update({ where: { id: device.id }, data: { tier: 'VIP', passExpiresAt: new Date(Math.max(reward.timestamp + minutes * 60000, device.passExpiresAt?.getTime() || 0)) } });
      }
    });
    return res.json({ success: true });
  } catch (error) {
    if ((error as { code?: string }).code === 'P2002') return res.json({ success: true });
    return res.status(400).json({ success: false, error: 'REWARD_VERIFICATION_FAILED' });
  }
});

router.post(
  '/verify',
  authenticateDevice,
  validateBody(VerifySubscriptionSchema),
  async (req: AuthenticatedDeviceRequest, res: Response) => {
    const { packageName, productId, purchaseToken } = req.body;
    const device = req.device!;

    try {
      const status = await PlayBillingService.verifySubscription(packageName, productId, purchaseToken);

      if (status.isValid && status.tier === 'VIP') {
        if (!status.expiryTimeMs) throw new Error('BILLING_EXPIRY_MISSING');
        const expiresAt = new Date(status.expiryTimeMs);
        await prisma.$transaction(async tx => {
          const tokenHash = crypto.createHash('sha256').update(purchaseToken).digest('hex');
          await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${tokenHash}))`;
          const existing = await tx.purchaseClaim.findUnique({ where: { tokenHash } });
          if (existing && existing.deviceId !== device.id) throw new Error('PURCHASE_ALREADY_BOUND');
          await tx.purchaseClaim.upsert({ where: { tokenHash }, create: { tokenHash, deviceId: device.id, expiresAt }, update: { expiresAt } });
          const current = await tx.device.findUniqueOrThrow({ where: { id: device.id } });
          await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${current.deviceId}`}))`;
          const fresh = await tx.device.findUniqueOrThrow({ where: { id: device.id } });
          if (fresh.isBlocked) throw new Error('DEVICE_BLOCKED');
          const grant = await tx.promoRedemption.findFirst({ where: { deviceId: device.id, grantExpiresAt: { gt: expiresAt } }, orderBy: { grantExpiresAt: 'desc' } });
          const effectiveExpiry = grant?.grantExpiresAt || expiresAt;
          await tx.device.update({
          where: { id: device.id },
          data: {
            tier: 'VIP',
            passExpiresAt: fresh.tier === 'VIP' && !fresh.passExpiresAt ? null : effectiveExpiry
          }
        });
        });

        const entitlement = await prisma.device.findUniqueOrThrow({ where: { id: device.id }, select: { tier: true, passExpiresAt: true } });
        return res.json({
          success: true,
          data: {
            tier: 'VIP',
            status: status.state,
            expiresAt: entitlement.passExpiresAt?.toISOString() || null
          }
        });
      }

      return res.status(400).json({
        success: false,
        error: 'SUBSCRIPTION_INVALID',
        message: `Subscription could not be verified (state: ${status.state})`
      });
    } catch (err: unknown) {
      if (err instanceof Error && err.message === 'PURCHASE_ALREADY_BOUND') return res.status(409).json({ success: false, error: 'PURCHASE_ALREADY_BOUND', message: 'Purchase belongs to another device identity' });
      return res.status(500).json({ success: false, error: 'BILLING_ERROR', message: 'Subscription verification unavailable' });
    }
  }
);

export default router;
