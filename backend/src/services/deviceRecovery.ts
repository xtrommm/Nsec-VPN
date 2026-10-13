import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import { z } from 'zod';
import { prisma } from '../db';
import { DeviceIdSchema } from '../types';
import { JWT_SECRET } from '../secrets';

export const RecoveryPinSchema = z.string().regex(/^[0-9]{8,12}$/)
  .refine(pin => !/^(\d)\1+$/.test(pin) && !['12345678', '87654321', '01234567', '1234567890', '123456789012'].includes(pin));
export const SetRecoveryPinSchema = z.object({ pin: RecoveryPinSchema, currentPin: z.string().regex(/^[0-9]{8,12}$/).optional() }).strict();
export const RecoverDeviceSchema = z.object({ deviceId: DeviceIdSchema, pin: RecoveryPinSchema,
  registrationSecret: z.string().regex(/^[a-f0-9]{64}$/) }).strict();
export function currentAuthEpoch(tokenEpoch: number | undefined, deviceEpoch: number | undefined): boolean {
  const epoch = tokenEpoch ?? 0;
  return Number.isInteger(epoch) && epoch >= 0 && epoch === (deviceEpoch ?? 0);
}
const LOCK_MS = 15 * 60 * 1000;

/** Device IDs are public lookup keys. Only the privately chosen PIN is recovery proof. */
export async function setRecoveryPin(id: string, pin: string, currentPin?: string) {
  RecoveryPinSchema.parse(pin);
  const hash = await bcrypt.hash(pin, 12);
  const result = await prisma.$transaction(async tx => {
    const candidate = await tx.device.findUniqueOrThrow({ where: { id } });
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${candidate.deviceId}))`;
    const device = await tx.device.findUniqueOrThrow({ where: { id } });
    if (device.isBlocked) return 'DEVICE_BLOCKED';
    if (device.recoveryBlockedUntil && device.recoveryBlockedUntil > new Date()) return 'RECOVERY_LOCKED';
    if (device.recoveryPinHash && (!currentPin || !await bcrypt.compare(currentPin, device.recoveryPinHash))) {
      const failures = (device.recoveryFailures ?? 0) + 1;
      await tx.device.update({ where: { id }, data: { recoveryFailures: failures >= 5 ? 0 : failures,
        recoveryBlockedUntil: failures >= 5 ? new Date(Date.now() + LOCK_MS) : null } });
      return 'INVALID_RECOVERY_PROOF';
    }
    await tx.device.update({ where: { id }, data: { recoveryPinHash: hash, recoveryFailures: 0, recoveryBlockedUntil: null } });
    return null;
  }, { timeout: 20000 });
  if (result) throw new Error(result);
}

export async function recoverDevice(deviceId: string, pin: string, registrationSecret: string) {
  RecoverDeviceSchema.parse({ deviceId, pin, registrationSecret });
  const outcome = await prisma.$transaction(async tx => {
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${deviceId}))`;
    const device = await tx.device.findUnique({ where: { deviceId } });
    if (!device || !device.recoveryPinHash) return { error: 'INVALID_RECOVERY_PROOF' };
    if (device.isBlocked) return { error: 'INVALID_RECOVERY_PROOF' };
    if (device.recoveryBlockedUntil && device.recoveryBlockedUntil > new Date()) return { error: 'RECOVERY_LOCKED' };
    if (!await bcrypt.compare(pin, device.recoveryPinHash)) {
      const failures = device.recoveryFailures + 1;
      await tx.device.update({ where: { id: device.id }, data: { recoveryFailures: failures >= 5 ? 0 : failures,
        recoveryBlockedUntil: failures >= 5 ? new Date(Date.now() + LOCK_MS) : null } });
      return { error: 'INVALID_RECOVERY_PROOF' };
    }
    const expired = device.passExpiresAt && device.passExpiresAt <= new Date();
    const updated = await tx.device.update({ where: { id: device.id }, data: {
      registrationSecretHash: crypto.createHash('sha256').update(registrationSecret).digest('hex'),
      authEpoch: { increment: 1 }, recoveryFailures: 0, recoveryBlockedUntil: null,
      ...(expired ? { tier: 'FREE', passExpiresAt: null } : {})
    } });
    await tx.refreshToken.updateMany({ where: { deviceId: device.id }, data: { revoked: true } });
    // Keep addresses reserved until the cleanup worker confirms agent removal.
    await tx.peerSession.updateMany({ where: { deviceId: device.id, active: true }, data: { expiresAt: new Date() } });
    const refreshToken = crypto.randomBytes(32).toString('hex');
    await tx.refreshToken.create({ data: { deviceId: device.id, authEpoch: updated.authEpoch,
      tokenHash: crypto.createHash('sha256').update(refreshToken).digest('hex'), expiresAt: new Date(Date.now() + 30 * 86400000) } });
    const accessToken = jwt.sign({ deviceId, tier: updated.tier, authEpoch: updated.authEpoch }, JWT_SECRET, { expiresIn: '7d' });
    return { data: { accessToken, refreshToken, device: { id: updated.id, tier: updated.tier,
      passExpiresAt: updated.passExpiresAt?.toISOString() ?? null } } };
  }, { timeout: 20000 });
  if (outcome.error) throw new Error(outcome.error);
  return outcome.data!;
}
