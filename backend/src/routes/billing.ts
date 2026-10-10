import { Router, Response } from 'express';
import { prisma } from '../db';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { VerifySubscriptionSchema } from '../types';
import { PlayBillingService } from '../services/billing';

const router = Router();

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
        const expiresAt = status.expiryTimeMs ? new Date(status.expiryTimeMs) : new Date(Date.now() + 30 * 86400000);
        await prisma.device.update({
          where: { id: device.id },
          data: {
            tier: 'VIP',
            passExpiresAt: expiresAt
          }
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
    } catch (err: any) {
      return res.status(500).json({ success: false, error: 'BILLING_ERROR', message: err.message });
    }
  }
);

export default router;
