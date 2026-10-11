import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { JwtDevicePayload, JwtAdminPayload } from '../types';
import { prisma } from '../db';

const JWT_SECRET = process.env.JWT_SECRET || 'nsec_vpn_production_secret_change_me_2026';

if (process.env.NODE_ENV === 'production' && JWT_SECRET.includes('change_me')) {
  console.warn('⚠️ WARNING: Using default JWT_SECRET in production! Please set JWT_SECRET in your environment.');
}

export interface AuthenticatedDeviceRequest extends Request {
  device?: {
    id: string;
    deviceId: string;
    tier: 'FREE' | 'VIP' | 'REWARDED_PASS';
  };
}

export interface AuthenticatedAdminRequest extends Request {
  admin?: JwtAdminPayload;
}

export const authenticateDevice = async (
  req: AuthenticatedDeviceRequest,
  res: Response,
  next: NextFunction
) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null;

  if (!token) {
    return res.status(401).json({
      success: false,
      error: 'UNAUTHORIZED',
      message: 'Missing or malformed Authorization header'
    });
  }

  try {
    const decoded = jwt.verify(token, JWT_SECRET, {
      clockTolerance: 30 // 30 seconds clock skew tolerance
    }) as JwtDevicePayload;

    const device = await prisma.device.findUnique({
      where: { deviceId: decoded.deviceId },
      select: { id: true, deviceId: true, tier: true, isBlocked: true, passExpiresAt: true }
    });

    if (!device) {
      return res.status(401).json({
        success: false,
        error: 'DEVICE_NOT_FOUND',
        message: 'Device registration not found'
      });
    }

    if (device.isBlocked) {
      return res.status(403).json({
        success: false,
        error: 'DEVICE_BLOCKED',
        message: 'This device is restricted from connecting'
      });
    }

    // Auto-downgrade expired rewarded pass
    let currentTier = device.tier;
    if (device.tier === 'REWARDED_PASS' && device.passExpiresAt && device.passExpiresAt < new Date()) {
      currentTier = 'FREE';
      await prisma.device.update({
        where: { id: device.id },
        data: { tier: 'FREE', passExpiresAt: null }
      });
    }

    req.device = {
      id: device.id,
      deviceId: device.deviceId,
      tier: currentTier
    };

    next();
  } catch (err: any) {
    return res.status(401).json({
      success: false,
      error: 'TOKEN_INVALID',
      message: err.name === 'TokenExpiredError' ? 'Access token expired' : 'Invalid authorization token'
    });
  }
};

export const authenticateAdmin = (requiredRole?: 'SUPER_ADMIN') => {
  return (req: AuthenticatedAdminRequest, res: Response, next: NextFunction) => {
    const authHeader = req.headers['authorization'];
    const token = (authHeader && authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null)
      || (typeof req.query.token === 'string' ? req.query.token : null);

    if (!token) {
      return res.status(401).json({
        success: false,
        error: 'UNAUTHORIZED',
        message: 'Admin authentication required'
      });
    }

    try {
      const decoded = jwt.verify(token, JWT_SECRET, {
        clockTolerance: 30
      }) as JwtAdminPayload;

      if (requiredRole && decoded.role !== requiredRole) {
        return res.status(403).json({
          success: false,
          error: 'FORBIDDEN',
          message: 'Insufficient administrative privileges'
        });
      }

      req.admin = decoded;
      next();
    } catch (_err) {
      return res.status(401).json({
        success: false,
        error: 'TOKEN_INVALID',
        message: 'Invalid or expired admin token'
      });
    }
  };
};
