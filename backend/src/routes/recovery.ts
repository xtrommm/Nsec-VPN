import { Router } from 'express';
import { prisma } from '../db';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';
import { authLimiter } from '../middleware/rateLimit';
import { validateBody } from '../middleware/validate';
import { recoverDevice, setRecoveryPin, RecoverDeviceSchema, SetRecoveryPinSchema } from '../services/deviceRecovery';
const router = Router();
function failed(res: import('express').Response, error: unknown) {
  const code = error instanceof Error ? error.message : '';
  const status: Record<string, number> = { INVALID_RECOVERY_PROOF: 401, RECOVERY_LOCKED: 429, DEVICE_BLOCKED: 403 };
  return res.status(status[code] ?? 500).json({ success: false, error: status[code] ? code : 'RECOVERY_UNAVAILABLE' });
}
router.get('/recovery/status', authenticateDevice, async (req: AuthenticatedDeviceRequest, res) => {
  try {
    const device = await prisma.device.findUniqueOrThrow({ where: { id: req.device!.id }, select: { recoveryPinHash: true } });
    return res.json({ success: true, data: { configured: Boolean(device.recoveryPinHash) } });
  } catch (error) { return failed(res, error); }
});
router.put('/recovery/pin', authenticateDevice, authLimiter, validateBody(SetRecoveryPinSchema), async (req: AuthenticatedDeviceRequest, res) => {
  try { await setRecoveryPin(req.device!.id, req.body.pin, req.body.currentPin); return res.json({ success: true }); }
  catch (error) { return failed(res, error); }
});
router.post('/recover-device', authLimiter, validateBody(RecoverDeviceSchema), async (req, res) => {
  try { return res.json({ success: true, data: await recoverDevice(req.body.deviceId, req.body.pin, req.body.registrationSecret) }); }
  catch (error) { return failed(res, error); }
});
export default router;
