import { JWT_SECRET } from "../secrets";
import { Router, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import { prisma } from '../db';
import { validateBody } from '../middleware/validate';
import { RegisterDeviceSchema, RefreshTokenSchema, JwtDevicePayload } from '../types';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';
import { authLimiter } from '../middleware/rateLimit';
import { expireEntitlement } from '../services/entitlements';
import { currentAuthEpoch } from '../services/deviceRecovery';

const router = Router();
router.get('/me', authenticateDevice, async (req: AuthenticatedDeviceRequest, res) => {
  try {
    const device = await prisma.device.findUnique({ where: { id: req.device!.id }, select: { id: true, tier: true, passExpiresAt: true } });
    if (!device) return res.status(404).json({ success: false, error: 'DEVICE_NOT_FOUND' });
    return res.json({ success: true, data: device });
  } catch { return res.status(503).json({ success: false, error: 'PROFILE_UNAVAILABLE' }); }
});
const ACCESS_TOKEN_TTL = '7d';
const REFRESH_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

router.post(
  ['/register-device', '/register-device-v2'],
  authLimiter,
  validateBody(RegisterDeviceSchema),
  async (req: Request, res: Response) => {
    const { deviceId, deviceModel, osVersion, appVersion, appVersionCode, registrationSecret, registrationProof } = req.body;
    if (!registrationSecret) return res.status(426).json({ success: false, error: 'APP_UPDATE_REQUIRED', message: 'Registration v2 requires a device-held credential' });

    try {
      const device = await prisma.$transaction(async tx => {
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${deviceId}))`;
      const existing = await tx.device.findUnique({ where: { deviceId } });
      const credentialHash = crypto.createHash('sha256').update(registrationSecret).digest('hex');
      if (existing) {
        let valid = existing.registrationSecretHash === credentialHash;
        if (!existing.registrationSecretHash && registrationProof) {
          try {
            const proof = jwt.verify(registrationProof, JWT_SECRET, { algorithms: ['HS256'] }) as JwtDevicePayload;
            valid = proof.deviceId === deviceId;
          } catch { valid = false; }
        }
        if (!valid) throw new Error('DEVICE_PROOF_REQUIRED');
      }
      return await tx.device.upsert({
        where: { deviceId },
        update: {
          registrationSecretHash: credentialHash,
          deviceModel: deviceModel || undefined,
          osVersion: osVersion || undefined,
          appVersion: appVersion || undefined,
          appVersionCode: appVersionCode || undefined
        },
        create: {
          registrationSecretHash: credentialHash,
          deviceId,
          deviceModel,
          osVersion,
          appVersion,
          appVersionCode,
          tier: 'FREE'
        }
      });

      });

      if (device.isBlocked) {
        return res.status(403).json({
          success: false,
          error: 'DEVICE_BLOCKED',
          message: 'This device is restricted from connecting'
        });
      }

      const entitled = await expireEntitlement(device);
      if (entitled.isBlocked) return res.status(403).json({ success: false, error: 'DEVICE_BLOCKED' });
      const currentTier = entitled.tier;

      const payload: JwtDevicePayload = {
        deviceId: device.deviceId,
        tier: currentTier,
        authEpoch: device.authEpoch
      };
      const accessToken = jwt.sign(payload, JWT_SECRET, { expiresIn: ACCESS_TOKEN_TTL });

      // Generate cryptographic refresh token
      const rawRefreshToken = crypto.randomBytes(32).toString('hex');
      const tokenHash = crypto.createHash('sha256').update(rawRefreshToken).digest('hex');

      await prisma.refreshToken.create({
        data: {
          tokenHash,
          deviceId: device.id,
          authEpoch: device.authEpoch,
          expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_MS)
        }
      });

      return res.json({
        success: true,
        data: {
          accessToken,
          refreshToken: rawRefreshToken,
          device: {
            id: device.id,
            tier: currentTier,
            passExpiresAt: entitled.passExpiresAt?.toISOString() || null
          }
        }
      });
    } catch (_err) {
      if (_err instanceof Error && _err.message === 'TOKEN_REVOKED') return res.status(401).json({ success: false, error: 'TOKEN_REVOKED' });
      if (_err instanceof Error && _err.message === 'DEVICE_PROOF_REQUIRED') return res.status(409).json({ success: false, error: 'DEVICE_PROOF_REQUIRED', message: 'Existing identity requires the device credential or a valid legacy access token' });
      return res.status(500).json({
        success: false,
        error: 'DB_ERROR',
        message: 'Failed to register device'
      });
    }
  }
);

router.post(
  '/refresh-token',
  authLimiter,
  validateBody(RefreshTokenSchema),
  async (req: Request, res: Response) => {
    const { refreshToken } = req.body;
    const tokenHash = crypto.createHash('sha256').update(refreshToken).digest('hex');

    try {
      const storedToken = await prisma.refreshToken.findUnique({
        where: { tokenHash },
        include: { device: true }
      });

      if (!storedToken) {
        return res.status(401).json({
          success: false,
          error: 'INVALID_REFRESH_TOKEN',
          message: 'Refresh token expired or invalid'
        });
      }

      if (!currentAuthEpoch(storedToken.authEpoch, storedToken.device.authEpoch)) {
        return res.status(401).json({ success: false, error: 'TOKEN_REVOKED' });
      }
      // Security defense: Token reuse detection (RFC 6749)
      // If a revoked token is presented, suspect token compromise and revoke all tokens for this device
      if (storedToken.revoked) {
        await prisma.refreshToken.updateMany({
          where: { deviceId: storedToken.deviceId },
          data: { revoked: true }
        });
        return res.status(401).json({
          success: false,
          error: 'TOKEN_COMPROMISED',
          message: 'Security violation: Refresh token reuse detected. Re-authentication required.'
        });
      }

      if (storedToken.expiresAt < new Date()) {
        return res.status(401).json({
          success: false,
          error: 'REFRESH_TOKEN_EXPIRED',
          message: 'Refresh token has expired'
        });
      }

      if (storedToken.device.isBlocked) {
        return res.status(403).json({
          success: false,
          error: 'DEVICE_BLOCKED',
          message: 'This device is restricted from connecting'
        });
      }

      // Generate fresh token pair inside transaction
      const newRawRefreshToken = crypto.randomBytes(32).toString('hex');
      const newTokenHash = crypto.createHash('sha256').update(newRawRefreshToken).digest('hex');

      await prisma.$transaction([
        prisma.refreshToken.update({
          where: { id: storedToken.id, revoked: false },
          data: { revoked: true }
        }),
        prisma.refreshToken.create({
          data: {
            tokenHash: newTokenHash,
            deviceId: storedToken.device.id,
            authEpoch: storedToken.device.authEpoch,
            expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_MS)
          }
        })
      ]);

      const entitled = await expireEntitlement(storedToken.device);
      if (entitled.isBlocked) return res.status(403).json({ success: false, error: 'DEVICE_BLOCKED' });
      const currentTier = entitled.tier;

      const payload: JwtDevicePayload = {
        deviceId: storedToken.device.deviceId,
        tier: currentTier,
        authEpoch: storedToken.device.authEpoch
      };
      const newAccessToken = jwt.sign(payload, JWT_SECRET, { expiresIn: ACCESS_TOKEN_TTL });

      return res.json({
        success: true,
        data: {
          accessToken: newAccessToken,
          refreshToken: newRawRefreshToken
        }
      });
    } catch (_err) {
      if (_err instanceof Error && _err.message === 'TOKEN_REVOKED') return res.status(401).json({ success: false, error: 'TOKEN_REVOKED' });
      return res.status(500).json({
        success: false,
        error: 'INTERNAL_ERROR',
        message: 'Failed to refresh authentication'
      });
    }
  }
);

export default router;
