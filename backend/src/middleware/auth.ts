import { JWT_SECRET } from "../secrets";
import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { JwtDevicePayload, JwtAdminPayload } from '../types';
import { prisma } from '../db';
import { expireEntitlement } from '../services/entitlements';
import { currentAuthEpoch } from '../services/deviceRecovery';


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
      select: { id: true, deviceId: true, tier: true, isBlocked: true, passExpiresAt: true, authEpoch: true }
    });

    if (!device) {
      return res.status(401).json({
        success: false,
        error: 'DEVICE_NOT_FOUND',
        message: 'Device registration not found'
      });
    }

    if (!currentAuthEpoch(decoded.authEpoch, device.authEpoch)) {
      return res.status(401).json({ success: false, error: 'TOKEN_REVOKED' });
    }
    if (device.isBlocked) {
      return res.status(403).json({
        success: false,
        error: 'DEVICE_BLOCKED',
        message: 'This device is restricted from connecting'
      });
    }

    const entitled = await expireEntitlement(device);
    if (entitled.isBlocked) return res.status(403).json({ success: false, error: 'DEVICE_BLOCKED' });

    req.device = {
      id: device.id,
      deviceId: device.deviceId,
      tier: entitled.tier
    };

    next();
  } catch (err: any) {
    const rejected = ['TokenExpiredError', 'JsonWebTokenError', 'NotBeforeError'].includes(err?.name) || ['TOKEN_REVOKED', 'DEVICE_NOT_FOUND'].includes(err?.message);
    return res.status(rejected ? 401 : 503).json({
      success: false,
      error: rejected ? (err?.message === 'TOKEN_REVOKED' ? 'TOKEN_REVOKED' : 'TOKEN_INVALID') : 'AUTH_UNAVAILABLE',
      message: rejected ? 'Invalid or expired authorization token' : 'Authentication is temporarily unavailable'
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
    } catch (error) {
      const rejected = error instanceof Error && ['TokenExpiredError', 'JsonWebTokenError', 'NotBeforeError'].includes(error.name);
      return res.status(rejected ? 401 : 503).json({
        success: false,
        error: rejected ? 'TOKEN_INVALID' : 'ADMIN_AUTH_UNAVAILABLE',
        message: rejected ? 'Invalid or expired admin token' : 'Administrator authentication is temporarily unavailable'
      });
    }
  };
};
