import { JWT_SECRET } from "../secrets";
import { Router, Response } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { prisma } from '../db';
import { authenticateAdmin, AuthenticatedAdminRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import {
  AdminLoginSchema,
  CreateServerSchema,
  UpdateServerSchema,
  UpdateConfigSchema,
  JwtAdminPayload
} from '../types';
import { adminLoginLimiter } from '../middleware/rateLimit';
import { StatsService } from '../services/stats';
import { AgentClient } from '../services/agentClient';
import { verifyConfiguredTotp } from '../services/totp';
import { testNode } from '../services/nodeHealth';
import { invalidateConfigCache } from './config';

const router = Router();

// Helper to record audit trail
async function logAudit(
  adminId: string,
  action: string,
  resource: string,
  targetId?: string,
  meta?: any
) {
  try {
    await prisma.auditLog.create({
      data: {
        adminId,
        action,
        targetResource: resource,
        targetId,
        metadata: meta ? JSON.stringify(meta) : null
      }
    });
  } catch (_e) {
    // Audit logging should not crash the primary operational path
  }
}

// 1. Admin Login
router.post(
  '/login',
  adminLoginLimiter,
  validateBody(AdminLoginSchema),
  async (req, res: Response) => {
    const { email, password } = req.body;

    try {
      const admin = await prisma.adminUser.findUnique({ where: { email } });
      if (!admin) {
        return res.status(401).json({
          success: false,
          error: 'INVALID_CREDENTIALS',
          message: 'Invalid email or password'
        });
      }

      const isValid = await bcrypt.compare(password, admin.passwordHash);
      if (!isValid) {
        return res.status(401).json({
          success: false,
          error: 'INVALID_CREDENTIALS',
          message: 'Invalid email or password'
        });
      }

      if (admin.totpEnabled && (!verifyConfiguredTotp(admin.totpSecret, req.body.totpCode || ''))) return res.status(401).json({ success: false, error: 'TOTP_REQUIRED', message: 'Valid authenticator code required' });
      const payload: JwtAdminPayload = {
        adminId: admin.id,
        email: admin.email,
        role: admin.role
      };

      const token = jwt.sign(payload, JWT_SECRET, { expiresIn: '12h' });

      await logAudit(admin.id, 'ADMIN_LOGIN', 'AUTH');

      return res.json({
        success: true,
        token,
        admin: {
          id: admin.id,
          email: admin.email,
          role: admin.role
        }
      });
    } catch (_err) {
      return res.status(500).json({
        success: false,
        error: 'INTERNAL_ERROR',
        message: 'Login processing failed'
      });
    }
  }
);

// 2. Overview Statistics
router.get('/stats', authenticateAdmin(), async (_req, res: Response) => {
  try {
    const metrics = await StatsService.getDashboardMetrics();
    return res.json({ success: true, data: metrics });
  } catch (_err) {
    return res.status(500).json({
      success: false,
      error: 'DB_ERROR',
      message: 'Failed to aggregate statistics'
    });
  }
});

// 3. Server Management
router.get('/servers', authenticateAdmin(), async (_req, res: Response) => {
  try {
    const servers = await prisma.serverNode.findMany({
      orderBy: { createdAt: 'desc' }
    });
    return res.json({ success: true, servers: servers.map(({ agentSecretHash, ...node }) => node) });
  } catch (_err) {
    return res.status(500).json({
      success: false,
      error: 'DB_ERROR',
      message: 'Failed to fetch servers'
    });
  }
});

router.post(
  '/servers',
  authenticateAdmin(),
  validateBody(CreateServerSchema),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    try {
      const { agentSecret, ...serverData } = req.body;
      const server = await prisma.serverNode.create({
        data: {
          ...serverData,
          agentSecretHash: agentSecret
        }
      });

      await logAudit(req.admin!.adminId, 'CREATE_SERVER', 'SERVER', server.id, { name: server.name });
      await testNode(server);
      const checked = await prisma.serverNode.findUniqueOrThrow({ where: { id: server.id } });
      const { agentSecretHash, ...safeServer } = checked;
      return res.status(201).json({ success: true, server: safeServer });
    } catch (err: any) {
      return res.status(500).json({
        success: false,
        error: 'DB_ERROR',
        message: 'Failed to create server'
      });
    }
  }
);

router.put(
  '/servers/:id',
  authenticateAdmin(),
  validateBody(UpdateServerSchema),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    const { id } = req.params;
    const { agentSecret, ...updateData } = req.body;

    try {
      const dataToUpdate: any = { ...updateData };
      if (agentSecret) {
        dataToUpdate.agentSecretHash = agentSecret;
      }

      const server = await prisma.$transaction(async tx => {
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${id}))`;
        return tx.serverNode.update({ where: { id }, data: dataToUpdate });
      });

      await logAudit(req.admin!.adminId, 'UPDATE_SERVER', 'SERVER', id, { fields: Object.keys(req.body).filter(k => k !== 'agentSecret') });
      await testNode(server);
      const checked = await prisma.serverNode.findUniqueOrThrow({ where: { id: server.id } });
      const { agentSecretHash, ...safeServer } = checked;
      return res.json({ success: true, server: safeServer });
    } catch (_err) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Server not found'
      });
    }
  }
);

router.delete(
  '/servers/:id',
  authenticateAdmin('SUPER_ADMIN'),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    const { id } = req.params;
    try {
      const node = await prisma.serverNode.findUniqueOrThrow({ where: { id }, include: { peerSessions: true } });
      for (const peer of node.peerSessions) if (peer.active) await AgentClient.removePeer(node.agentUrl, node.agentSecretHash, { publicKey: peer.clientPublicKey });
      await prisma.serverNode.delete({ where: { id } });
      await logAudit(req.admin!.adminId, 'DELETE_SERVER', 'SERVER', id);
      return res.json({ success: true, message: 'Server node deleted' });
    } catch (_err) {
      return res.status(500).json({
        success: false,
        error: 'DB_ERROR',
        message: 'Failed to delete server'
      });
    }
  }
);

// Ping individual server health & live peer count
router.post(
  '/servers/:id/ping',
  authenticateAdmin(),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    const { id } = req.params;
    try {
      const server = await prisma.serverNode.findUnique({ where: { id } });
      if (!server) {
        return res.status(404).json({ success: false, error: 'NOT_FOUND', message: 'Server not found' });
      }

      const health = await testNode(server);
      return res.json({ success: true, health });
    } catch (err: any) {
      return res.status(500).json({ success: false, error: 'HEALTH_CHECK_FAILED', message: 'Operation failed' });
    }
  }
);

router.post('/servers/:id/sync-public-key', authenticateAdmin('SUPER_ADMIN'), async (req: AuthenticatedAdminRequest, res: Response) => {
  try {
    const node = await prisma.serverNode.findUnique({ where: { id: req.params.id } });
    if (!node) return res.status(404).json({ success: false, error: 'NOT_FOUND' });
    const health = await testNode(node, true);
    await logAudit(req.admin!.adminId, 'SYNC_NODE_PUBLIC_KEY', 'SERVER', node.id);
    return res.status(health.agentOnline ? 200 : 502).json({ success: health.agentOnline, health });
  } catch { return res.status(502).json({ success: false, error: 'AGENT_UNREACHABLE' }); }
});

// 4. Device Management
router.get('/devices', authenticateAdmin(), async (req, res: Response) => {
  const page = Math.max(parseInt(req.query.page as string, 10) || 1, 1);
  const limit = Math.min(Math.max(parseInt(req.query.limit as string, 10) || 20, 1), 100);
  const search = (req.query.search as string)?.trim() || '';

  try {
    const where = search ? { deviceId: { contains: search, mode: 'insensitive' as any } } : {};
    const [total, devices] = await Promise.all([
      prisma.device.count({ where }),
      prisma.device.findMany({
        where,
        skip: (page - 1) * limit,
        take: limit,
        orderBy: { createdAt: 'desc' },
        select: {
          id: true,
          deviceId: true,
          deviceModel: true,
          osVersion: true,
          appVersion: true,
          tier: true,
          passExpiresAt: true,
          isBlocked: true,
          createdAt: true
        }
      })
    ]);

    return res.json({
      success: true,
      devices,
      pagination: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit) || 1
      }
    });
  } catch (_err) {
    return res.status(500).json({
      success: false,
      error: 'DB_ERROR',
      message: 'Failed to fetch devices'
    });
  }
});

router.put(
  '/devices/:id/tier',
  authenticateAdmin(),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    const { id } = req.params;
    const { tier } = req.body;

    if (!['FREE', 'VIP', 'REWARDED_PASS'].includes(tier)) {
      return res.status(400).json({
        success: false,
        error: 'INVALID_TIER',
        message: 'Invalid subscription tier'
      });
    }

    try {
      const updated = await prisma.device.update({
        where: { id },
        data: {
          tier,
          passExpiresAt: tier === 'VIP' ? new Date(Date.now() + 365 * 86400000) : null
        }
      });

      await logAudit(req.admin!.adminId, 'UPDATE_DEVICE_TIER', 'DEVICE', id, { tier });
      return res.json({ success: true, device: updated });
    } catch (_err) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Device not found'
      });
    }
  }
);

router.put(
  '/devices/:id/block',
  authenticateAdmin(),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    const { id } = req.params;
    const { isBlocked } = req.body;

    try {
      if (typeof isBlocked !== 'boolean') return res.status(400).json({ success: false, error: 'INVALID_INPUT' });
      const updated = await prisma.$transaction(async tx => {
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`device:${id}`}))`;
        return tx.device.update({ where: { id }, data: { isBlocked } });
      });
      if (isBlocked) {
        const peers = await prisma.peerSession.findMany({ where: { deviceId: id, active: true }, include: { server: true } });
        for (const peer of peers) {
          try {
            await AgentClient.removePeer(peer.server.agentUrl, peer.server.agentSecretHash, { publicKey: peer.clientPublicKey });
            await prisma.peerSession.delete({ where: { id: peer.id } });
          } catch { return res.status(502).json({ success: false, error: 'DEVICE_BLOCKED_PEER_REMOVAL_PENDING', message: 'Device blocked; live peer removal needs agent recovery or manual removal' }); }
        }
      }

      await logAudit(
        req.admin!.adminId,
        isBlocked ? 'BLOCK_DEVICE' : 'UNBLOCK_DEVICE',
        'DEVICE',
        id
      );
      return res.json({ success: true, device: updated });
    } catch (_err) {
      return res.status(404).json({
        success: false,
        error: 'NOT_FOUND',
        message: 'Device not found'
      });
    }
  }
);

// 5. Remote Configuration Management
router.get('/config', authenticateAdmin(), async (_req, res: Response) => {
  try {
    let config = await prisma.appConfig.findUnique({ where: { id: 'global_config' } });
    if (!config) {
      config = await prisma.appConfig.create({
        data: {
          id: 'global_config',
          minAppVersionCode: 1,
          latestAppVersionCode: 1,
          forceUpdate: false,
          maintenanceMode: false,
          announcementActive: false,
          announcementTitle: null,
          announcementBody: null,
          bannerAdsEnabled: true,
          interstitialAdsEnabled: true,
          interstitialIntervalSeconds: 120,
          rewardedAdsEnabled: true
        }
      });
    }
    return res.json({
      success: true,
      config,
      data: config,
      lastUpdated: config.updatedAt
    });
  } catch (err: any) {
    return res.status(500).json({
      success: false,
      error: 'DB_ERROR',
      message: 'Failed to fetch configuration'
    });
  }
});

router.put(
  '/config',
  authenticateAdmin(),
  validateBody(UpdateConfigSchema),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    try {
      const sanitized = { ...req.body };
      if (sanitized.announcementTitle === '') sanitized.announcementTitle = null;
      if (sanitized.announcementBody === '') sanitized.announcementBody = null;

      const updated = await prisma.appConfig.upsert({
        where: { id: 'global_config' },
        update: sanitized,
        create: { id: 'global_config', ...sanitized }
      });

      // Invalidate in-memory cache so mobile clients immediately see updates
      invalidateConfigCache();

      await logAudit(req.admin!.adminId, 'UPDATE_CONFIG', 'APP_CONFIG', 'global_config', sanitized);
      return res.json({
        success: true,
        config: updated,
        data: updated,
        lastUpdated: updated.updatedAt
      });
    } catch (err: any) {
      return res.status(500).json({
        success: false,
        error: 'DB_ERROR',
        message: 'Failed to update configuration'
      });
    }
  }
);

// 6. Audit Trail (Super Admin only)
router.get('/audit-logs', authenticateAdmin('SUPER_ADMIN'), async (_req, res: Response) => {
  const logs = await prisma.auditLog.findMany({
    take: 100,
    orderBy: { createdAt: 'desc' },
    include: { admin: { select: { email: true, role: true } } }
  });
  return res.json({ success: true, logs });
});

// 7. CSV Export of Anonymous Aggregated Data
router.get('/export/stats', authenticateAdmin(), async (_req, res: Response) => {
  const stats = await prisma.anonymousStat.findMany({
    orderBy: { date: 'desc' },
    take: 1000
  });

  let csv = 'Date,Country,AppVersion,Tier,ConnectionsCount\n';
  stats.forEach((s) => {
    const formattedDate = s.date.toISOString().split('T')[0];
    csv += `${formattedDate},${s.countryCode},${s.appVersion},${s.tier},${s.connectionsCount}\n`;
  });

  res.setHeader('Content-Type', 'text/csv');
  res.setHeader('Content-Disposition', 'attachment; filename=nsecvpn_anonymous_stats.csv');
  return res.send(csv);
});

export default router;
