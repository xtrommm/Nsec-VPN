import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import { prisma } from '../db';
import { JWT_SECRET } from '../secrets';
import { currentAuthEpoch } from './deviceRecovery';

/** Serialize rotation with registration/recovery. Return errors so replay revocations commit. */
export async function rotateRefreshToken(raw: string) {
  const tokenHash = crypto.createHash('sha256').update(raw).digest('hex');
  return prisma.$transaction(async tx => {
    const candidate = await tx.refreshToken.findUnique({ where: { tokenHash }, include: { device: true } });
    if (!candidate) return { error: 'INVALID_REFRESH_TOKEN', status: 401 };
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${candidate.device.deviceId}))`;
    const token = await tx.refreshToken.findUnique({ where: { tokenHash }, include: { device: true } });
    if (!token || !currentAuthEpoch(token.authEpoch, token.device.authEpoch)) return { error: 'TOKEN_REVOKED', status: 401 };
    if (token.revoked) {
      await tx.refreshToken.updateMany({ where: { deviceId: token.deviceId }, data: { revoked: true } });
      await tx.device.update({ where: { id: token.deviceId }, data: { authEpoch: { increment: 1 } } });
      await tx.peerSession.updateMany({ where: { deviceId: token.deviceId, active: true }, data: { expiresAt: new Date() } });
      return { error: 'TOKEN_COMPROMISED', status: 401 };
    }
    if (token.expiresAt <= new Date()) return { error: 'REFRESH_TOKEN_EXPIRED', status: 401 };
    if (token.device.isBlocked) return { error: 'DEVICE_BLOCKED', status: 403 };
    const device = token.device;
    if (device.passExpiresAt && device.passExpiresAt <= new Date() && device.tier !== 'FREE') {
      await tx.device.updateMany({ where: { id: device.id, tier: device.tier, passExpiresAt: device.passExpiresAt,
        authEpoch: device.authEpoch }, data: { tier: 'FREE', passExpiresAt: null } });
    }
    const current = await tx.device.findUniqueOrThrow({ where: { id: device.id } });
    if (current.isBlocked) return { error: 'DEVICE_BLOCKED', status: 403 };
    const refreshToken = crypto.randomBytes(32).toString('hex');
    await tx.refreshToken.update({ where: { id: token.id, revoked: false }, data: { revoked: true } });
    await tx.refreshToken.create({ data: { tokenHash: crypto.createHash('sha256').update(refreshToken).digest('hex'),
      deviceId: current.id, authEpoch: current.authEpoch, expiresAt: new Date(Date.now() + 30 * 86400000) } });
    const accessToken = jwt.sign({ deviceId: current.deviceId, tier: current.tier, authEpoch: current.authEpoch }, JWT_SECRET, { expiresIn: '7d' });
    return { data: { accessToken, refreshToken } };
  }, { timeout: 10000 });
}
