import { JWT_SECRET } from "../secrets";
import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { JwtDevicePayload, JwtAdminPayload } from '../types';
import { prisma } from '../db';


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
      algorithms: ['HS256'], clockTolerance: 30 // 30 seconds clock skew tolerance
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
    if ((device.tier === 'REWARDED_PASS' || device.tier === 'VIP') && device.passExpiresAt && device.passExpiresAt < new Date()) {
      currentTier = 'FREE';
      const downgraded = await prisma.device.updateMany({
        where: { id: device.id, tier: device.tier, passExpiresAt: device.passExpiresAt },
        data: { tier: 'FREE', passExpiresAt: null }
      });
      if (!downgraded.count) {
        const fresh = await prisma.device.findUnique({ where: { id: device.id } });
        if (!fresh || fresh.isBlocked) return res.status(403).json({ success: false, error: 'DEVICE_BLOCKED' });
        currentTier = fresh.tier;
      }
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
  return async (req: AuthenticatedAdminRequest, res: Response, next: NextFunction) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null;

    if (!token) {
      return res.status(401).json({
        success: false,
        error: 'UNAUTHORIZED',
        message: 'Admin authentication required'
      });
    }

    try {
      const decoded = jwt.verify(token, JWT_SECRET, {
        algorithms: ['HS256'], clockTolerance: 30
      }) as JwtAdminPayload;

      const admin = typeof decoded.adminId === 'string' ? await prisma.adminUser.findUnique({ where: { id: decoded.adminId } }) : null;
      if (!admin) return res.status(401).json({ success: false, error: 'TOKEN_INVALID' });
      decoded.role = admin.role;
      if (requiredRole && admin.role !== requiredRole) {
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
