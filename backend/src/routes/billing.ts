import { Router, Response } from 'express';
import crypto from 'crypto';
import { prisma } from '../db';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { VerifySubscriptionSchema } from '../types';
import { verifyReward } from '../services/rewardVerification';
import { PlayBillingService } from '../services/billing';

const router = Router();
router.get('/rewarded/ssv', async (req, res) => {
  try {
    const reward = await verifyReward(req.originalUrl.split('?')[1] || '');
    await prisma.$transaction(async tx => {
      const device = await tx.device.findUniqueOrThrow({ where: { deviceId: reward.deviceId } });
      if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
      await tx.rewardGrant.create({ data: { transactionId: reward.transactionId } });
      if (device.tier !== 'VIP') {
        const minutes = Number(process.env.REWARDED_PASS_MINUTES || '30');
        if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) throw new Error('REWARD_DURATION_INVALID');
        await tx.device.update({ where: { id: device.id }, data: { tier: 'REWARDED_PASS', passExpiresAt: new Date(Date.now() + minutes * 60000) } });
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
          await tx.device.update({
          where: { id: device.id },
          data: {
            tier: 'VIP',
            passExpiresAt: expiresAt
          }
        });
        });

        return res.json({
          success: true,
          data: {
            tier: 'VIP',
            status: status.state,
            expiresAt: expiresAt.toISOString()
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
