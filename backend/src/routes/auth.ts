import { Router, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import { prisma } from '../db';
import { validateBody } from '../middleware/validate';
import { RegisterDeviceSchema, RefreshTokenSchema, JwtDevicePayload } from '../types';
import { authLimiter } from '../middleware/rateLimit';

const router = Router();
const JWT_SECRET = process.env.JWT_SECRET || 'nsec_vpn_production_secret_change_me_2026';
const ACCESS_TOKEN_TTL = '7d';
const REFRESH_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

router.post(
  '/register-device',
  authLimiter,
  validateBody(RegisterDeviceSchema),
  async (req: Request, res: Response) => {
    const { deviceId, deviceModel, osVersion, appVersion } = req.body;

    try {
      const device = await prisma.device.upsert({
        where: { deviceId },
        update: {
          deviceModel: deviceModel || undefined,
          osVersion: osVersion || undefined,
          appVersion: appVersion || undefined
        },
        create: {
          deviceId,
          deviceModel,
          osVersion,
          appVersion,
          tier: 'FREE'
        }
      });

      if (device.isBlocked) {
        return res.status(403).json({
          success: false,
          error: 'DEVICE_BLOCKED',
          message: 'This device is restricted from connecting'
        });
      }

      // Automatically downgrade expired rewarded pass if present
      let currentTier = device.tier;
      if (device.tier === 'REWARDED_PASS' && device.passExpiresAt && device.passExpiresAt < new Date()) {
        currentTier = 'FREE';
        await prisma.device.update({
          where: { id: device.id },
          data: { tier: 'FREE', passExpiresAt: null }
        });
      }

      const payload: JwtDevicePayload = {
        deviceId: device.deviceId,
        tier: currentTier
      };
      const accessToken = jwt.sign(payload, JWT_SECRET, { expiresIn: ACCESS_TOKEN_TTL });

      // Generate cryptographic refresh token
      const rawRefreshToken = crypto.randomBytes(32).toString('hex');
      const tokenHash = crypto.createHash('sha256').update(rawRefreshToken).digest('hex');

      await prisma.refreshToken.create({
        data: {
          tokenHash,
          deviceId: device.id,
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
            passExpiresAt: device.passExpiresAt?.toISOString() || null
          }
        }
      });
    } catch (_err) {
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
          where: { id: storedToken.id },
          data: { revoked: true }
        }),
        prisma.refreshToken.create({
          data: {
            tokenHash: newTokenHash,
            deviceId: storedToken.device.id,
            expiresAt: new Date(Date.now() + REFRESH_TOKEN_TTL_MS)
          }
        })
      ]);

      // Downgrade expired rewarded pass if needed
      let currentTier = storedToken.device.tier;
      if (
        storedToken.device.tier === 'REWARDED_PASS' &&
        storedToken.device.passExpiresAt &&
        storedToken.device.passExpiresAt < new Date()
      ) {
        currentTier = 'FREE';
        await prisma.device.update({
          where: { id: storedToken.device.id },
          data: { tier: 'FREE', passExpiresAt: null }
        });
      }

      const payload: JwtDevicePayload = {
        deviceId: storedToken.device.deviceId,
        tier: currentTier
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
      return res.status(500).json({
        success: false,
        error: 'INTERNAL_ERROR',
        message: 'Failed to refresh authentication'
      });
    }
  }
);

export default router;
