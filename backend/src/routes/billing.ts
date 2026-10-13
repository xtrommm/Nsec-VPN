import { Router, Response } from 'express';
import { prisma } from '../db';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { VerifySubscriptionSchema } from '../types';
import { verifyReward } from '../services/rewardVerification';
import { PlayBillingService } from '../services/billing';
import { applyVerifiedPurchase, purchaseHash } from '../services/purchaseReconciliation';
import { authenticatePlayPush, handlePlayNotification } from '../services/playNotifications';
import { prepareReward, claimReward, grantVerifiedReward } from '../services/rewardClaims';
import { z } from 'zod';
import rateLimit from 'express-rate-limit';

const router = Router();
const rewardLimiter = rateLimit({ windowMs: 3600000, max: 20, standardHeaders: true, legacyHeaders: false,
  keyGenerator: req => (req as AuthenticatedDeviceRequest).device!.id,
  message: { success: false, error: 'REWARD_RATE_LIMIT' } });
router.post('/rewarded/prepare', authenticateDevice, rewardLimiter, async (req: AuthenticatedDeviceRequest, res) => {
  try { return res.json({ success: true, data: await prepareReward(req.device!.id) }); }
  catch (e) { const code = e instanceof Error ? e.message : ''; const status = code === 'REWARDED_ADS_DISABLED' ? 409 : code === 'REWARD_COOLDOWN' ? 429 : 503; return res.status(status).json({ success: false, error: status === 503 ? 'REWARD_PREPARATION_UNAVAILABLE' : code }); }
});
const rewardClaimLimiter = rateLimit({ windowMs: 60000, max: 60, standardHeaders: true, legacyHeaders: false,
  keyGenerator: req => (req as AuthenticatedDeviceRequest).device!.id,
  message: { success: false, error: 'REWARD_RATE_LIMIT' } });
router.post('/rewarded/claim', authenticateDevice, rewardClaimLimiter,
  validateBody(z.object({ ticketId: z.string().uuid() }).strict()), async (req: AuthenticatedDeviceRequest, res) => {
  try { return res.json({ success: true, data: await claimReward(req.device!.id, req.body.ticketId) }); }
  catch (error) {
    const code = error instanceof Error ? error.message : '';
    const errors: Record<string, number> = { DEVICE_BLOCKED: 403, REWARD_TICKET_INVALID: 404, REWARD_TICKET_EXPIRED: 410,
      REWARD_COOLDOWN: 429, REWARDED_ADS_DISABLED: 409, REWARD_VERIFICATION_PENDING: 202 };
    return res.status(errors[code] || 503).json({ success: false, error: errors[code] ? code : 'REWARD_CLAIM_UNAVAILABLE' });
  }
});
router.get('/rewarded/ssv', async (req, res) => {
  try {
    const reward = await verifyReward(req.originalUrl.split('?')[1] || '');
    await grantVerifiedReward(reward);
    return res.json({ success: true });
  } catch (error) {
    if ((error as { code?: string }).code === 'P2002') return res.json({ success: true });
    return res.status(400).json({ success: false, error: 'REWARD_VERIFICATION_FAILED' });
  }
});

router.post('/rtdn', async (req, res) => {
  try { await authenticatePlayPush(req.get('Authorization')); }
  catch(error) {
    const unavailable=error instanceof Error&&error.message==='RTDN_NOT_CONFIGURED';
    return res.status(unavailable?503:403).json({success:false,error:unavailable?'RTDN_NOT_CONFIGURED':'RTDN_UNAUTHORIZED'});
  }
  try { await handlePlayNotification(req.body); return res.status(204).end(); }
  catch(error) {
    const invalid=error instanceof SyntaxError||(error instanceof Error&&error.message==='RTDN_PAYLOAD_INVALID')||error instanceof z.ZodError;
    return res.status(invalid?400:503).json({success:false,error:invalid?'RTDN_PAYLOAD_INVALID':'RTDN_RETRY_REQUIRED'});
  }
});

router.post('/verify', authenticateDevice, validateBody(VerifySubscriptionSchema),
  async (req: AuthenticatedDeviceRequest, res: Response) => {
    const {packageName,productId,purchaseToken}=req.body;
    const device=req.device!;
    const startedAt=new Date();
    try {
      const status=await PlayBillingService.verifySubscription(packageName,productId,purchaseToken);
      if(!status.isValid) {
        const owned=await prisma.purchaseClaim.findUnique({where:{tokenHash:purchaseHash(purchaseToken)}});
        if(owned?.deviceId===device.id) await applyVerifiedPurchase(device.id,productId,purchaseToken,status,startedAt);
        return res.status(400).json({success:false,error:'SUBSCRIPTION_INVALID',message:`Subscription could not be verified (state: ${status.state})`});
      }
      if(!status.expiryTimeMs) throw new Error('BILLING_EXPIRY_MISSING');
      const entitlement=await applyVerifiedPurchase(device.id,productId,purchaseToken,status,startedAt);
      return res.json({success:true,data:{tier:entitlement.tier,status:status.state,
        expiresAt:entitlement.passExpiresAt?.toISOString()||null}});
    } catch(error) {
      const code=error instanceof Error?error.message:'';
      const errors:Record<string,number>={DEVICE_BLOCKED:403,PURCHASE_ALREADY_BOUND:409,PURCHASE_PRODUCT_MISMATCH:400,BILLING_TOKEN_ENCRYPTION_REQUIRED:503};
      return res.status(errors[code]||503).json({success:false,error:errors[code]?code:'BILLING_VERIFICATION_UNAVAILABLE',message:'Subscription verification unavailable'});
    }
  });

export default router;
