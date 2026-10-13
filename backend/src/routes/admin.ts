import { csvCell } from '../services/csv';
import { removeTrackedPeerKeys } from '../services/peerProvisioning';
import { preserveNonPurchaseGrant } from '../services/purchaseReconciliation';
import { JWT_SECRET } from "../secrets";
import { Router, Response } from 'express';
import { Prisma } from '@prisma/client';
import { z } from 'zod';
import { PageQuery, pagination, databaseError } from '../services/consoleQuery';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { prisma } from '../db';
import { authenticateAdmin, AuthenticatedAdminRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import {
  DeviceIdSchema,
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

async function savedNodeHealth(node: import('@prisma/client').ServerNode) {
  let healthCheckError: string | null = null;
  let checked = node;
  try {
    const health = await testNode(node);
    healthCheckError = health.online ? null : 'NODE_HEALTH_CHECK_FAILED';
    checked = await prisma.serverNode.findUniqueOrThrow({ where: { id: node.id } });
  } catch { healthCheckError = 'NODE_HEALTH_CHECK_UNAVAILABLE'; }
  const { agentSecretHash: _credential, ...server } = checked;
  return { server, healthCheckError };
}

const router = Router();

// Helper to record audit trail
async function logAudit(
  adminId: string,
  action: string,
  resource: string,
  targetId?: string,
  meta?: any
) {
    await prisma.auditLog.create({
      data: {
        adminId,
        action,
        targetResource: resource,
        targetId,
        metadata: meta ? JSON.stringify(meta) : null
      }
    });
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
      orderBy: [{ sortOrder: 'asc' }, { createdAt: 'asc' }]
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
  authenticateAdmin('SUPER_ADMIN'),
  validateBody(CreateServerSchema),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    try {
      const { agentSecret, ...serverData } = req.body;
      const server = await prisma.$transaction(async tx => {
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${'server-order'}))`;
        const last = await tx.serverNode.findFirst({ orderBy: { sortOrder: 'desc' }, select: { sortOrder: true } });
        const result = await tx.serverNode.create({ data: { ...serverData, agentSecretHash: agentSecret, sortOrder: (last?.sortOrder ?? -1) + 1 } });
        await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'CREATE_SERVER', targetResource: 'SERVER', targetId: result.id } });
        return result;
      });
      const checked = await savedNodeHealth(server);
      return res.status(201).json({ success: true, ...checked });
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
  authenticateAdmin('SUPER_ADMIN'),
  validateBody(UpdateServerSchema),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    const { id } = req.params;
    const { agentSecret, ...updateData } = req.body;

    try {
      const dataToUpdate: any = { ...updateData, healthy: false, healthCheckedAt: null };
      if (agentSecret) {
        dataToUpdate.agentSecretHash = agentSecret;
      }

      const server = await prisma.$transaction(async tx => {
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${id}))`;
        const result = await tx.serverNode.update({ where: { id }, data: dataToUpdate });
        await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'UPDATE_SERVER', targetResource: 'SERVER', targetId: id, metadata: JSON.stringify({ fields: Object.keys(req.body).filter(k => k !== 'agentSecret') }) } });
        return result;
      });
      const checked = await savedNodeHealth(server);
      return res.json({ success: true, ...checked });
    } catch (error) {
      const missing = databaseError(error) === 'P2025';
      return res.status(missing ? 404 : 503).json({ success: false, error: missing ? 'NOT_FOUND' : 'NODE_UPDATE_FAILED', message: missing ? 'Server not found' : 'Node update unavailable' });
    }
  }
);

router.delete(
  '/servers/:id',
  authenticateAdmin('SUPER_ADMIN'),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    const { id } = req.params;
    try {
      // Disable before external cleanup, using the same lock as allocation.
      const node = await prisma.$transaction(async tx => {
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${id}))`;
        const result = await tx.serverNode.update({ where: { id }, data: { active: false }, include: { peerSessions: true } });
        await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'DISABLE_SERVER_FOR_DELETION', targetResource: 'SERVER', targetId: id } });
        return result;
      });
      try { for (const peer of node.peerSessions) if (peer.active) await removeTrackedPeerKeys(node, peer); }
      catch { return res.status(502).json({ success: false, error: 'NODE_PEER_REMOVAL_PENDING', message: 'Node disabled. Recover its agent and retry deletion to revoke its peers.' }); }
      await prisma.$transaction(async tx => {
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${'server-order'}))`;
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${id}))`;
        await tx.serverNode.delete({ where: { id } });
        await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'DELETE_SERVER', targetResource: 'SERVER', targetId: id } });
      });
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
    const health = await testNode(node, true, req.admin!.adminId);
    return res.status(health.agentOnline ? 200 : 502).json({ success: health.agentOnline, health });
  } catch { return res.status(502).json({ success: false, error: 'AGENT_UNREACHABLE' }); }
});

const deviceSelect = { id: true, deviceId: true, deviceModel: true, osVersion: true, appVersion: true,
  tier: true, passExpiresAt: true, isBlocked: true, createdAt: true, updatedAt: true } as const;
router.get('/me', authenticateAdmin(), (req: AuthenticatedAdminRequest, res) => res.json({ success: true, admin: { email: req.admin!.email, role: req.admin!.role } }));
router.put('/servers-order', authenticateAdmin('SUPER_ADMIN'), validateBody(z.object({ ids: z.array(z.string().uuid()).min(1).max(1000).refine(ids => new Set(ids).size === ids.length) }).strict()), async (req: AuthenticatedAdminRequest, res) => {
  try {
    await prisma.$transaction(async tx => {
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${'server-order'}))`;
      const nodes = await tx.serverNode.findMany({ select: { id: true } });
      const ids: string[] = req.body.ids;
      if (nodes.length !== ids.length || nodes.some(n => !ids.includes(n.id))) throw new Error('SERVER_ORDER_STALE');
      for (const [sortOrder, id] of ids.entries()) await tx.serverNode.update({ where: { id }, data: { sortOrder } });
      await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'REORDER_SERVERS', targetResource: 'SERVER', metadata: JSON.stringify({ count: ids.length }) } });
    });
    return res.json({ success: true });
  } catch (e) { return res.status(e instanceof Error && e.message === 'SERVER_ORDER_STALE' ? 409 : 503).json({ success: false, error: e instanceof Error && e.message === 'SERVER_ORDER_STALE' ? 'SERVER_ORDER_STALE' : 'SERVER_ORDER_FAILED' }); }
});

// 4. Device Management
router.get('/devices', authenticateAdmin(), async (req, res) => {
  const parsed = PageQuery.safeParse(req.query);
  if (!parsed.success) return res.status(400).json({ success: false, error: 'INVALID_QUERY' });
  const { page, limit, search, sort } = parsed.data;
  const exact = req.query.exact === 'true';
  const where: Prisma.DeviceWhereInput = search ? exact ? { deviceId: search } : { OR: [{ deviceId: { contains: search, mode: 'insensitive' } }, { deviceModel: { contains: search, mode: 'insensitive' } }] } : {};
  try {
    const [total, devices] = await prisma.$transaction([prisma.device.count({ where }), prisma.device.findMany({ where, select: deviceSelect,
      skip: (page - 1) * limit, take: limit, orderBy: [sort === 'name' ? { deviceId: 'asc' } : { createdAt: sort === 'oldest' ? 'asc' : 'desc' }, { id: 'asc' }] })]);
    return res.json({ success: true, devices, pagination: pagination(total, page, limit) });
  } catch { return res.status(503).json({ success: false, error: 'DEVICES_UNAVAILABLE' }); }
});
router.get('/devices/:installationId/details', authenticateAdmin(), async (req, res) => {
  if (!DeviceIdSchema.safeParse(req.params.installationId).success) return res.status(400).json({ success: false, error: 'INVALID_DEVICE_ID' });
  try {
    const device = await prisma.device.findUnique({ where: { deviceId: req.params.installationId }, select: deviceSelect });
    if (!device) return res.status(404).json({ success: false, error: 'DEVICE_NOT_FOUND' });
    const [promos, requests] = await Promise.all([
      prisma.promoRedemption.findMany({ where: { deviceId: device.id }, orderBy: { redeemedAt: 'desc' }, take: 50,
        select: { id: true, redeemedAt: true, grantExpiresAt: true, durationMinutes: true, promo: { select: { id: true, label: true, deletedAt: true } } } }),
      prisma.serverRequest.findMany({ where: { deviceId: device.id }, orderBy: { createdAt: 'desc' }, take: 50,
        select: { id: true, country: true, city: true, status: true, note: true, createdAt: true } })
    ]);
    return res.json({ success: true, device, promos: promos.map(p => ({ ...p, active: !!p.grantExpiresAt && p.grantExpiresAt > new Date() })), requests });
  } catch { return res.status(503).json({ success: false, error: 'DEVICE_DETAILS_UNAVAILABLE' }); }
});
router.put('/devices/:id/tier', authenticateAdmin('SUPER_ADMIN'), validateBody(z.object({ tier: z.enum(['FREE', 'VIP']) }).strict()), async (req: AuthenticatedAdminRequest, res) => {
  try {
    const device = await prisma.$transaction(async tx => {
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`device:${req.params.id}`}))`;
      const current = await tx.device.findUniqueOrThrow({ where: { id: req.params.id } });
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${current.deviceId}`}))`;
      const updated = await tx.device.update({ where: { id: current.id }, data: { tier: req.body.tier, passExpiresAt: req.body.tier === 'VIP' ? new Date(Date.now() + 365 * 86400000) : null }, select: deviceSelect });
      if(req.body.tier==='VIP') await preserveNonPurchaseGrant(tx,current.id,updated.passExpiresAt);
      else await tx.purchaseClaim.updateMany({where:{deviceId:current.id},data:{active:false,adminRevoked:true,
        protectedVipUntil:null,protectedVipIndefinite:false}});
      if(req.body.tier==='FREE') await tx.peerSession.updateMany({where:{deviceId:current.id,server:{isPremiumOnly:true}},data:{expiresAt:new Date()}});
      await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'UPDATE_DEVICE_TIER', targetResource: 'DEVICE', targetId: updated.id, metadata: JSON.stringify({ tier: updated.tier }) } });
      return updated;
    });
    return res.json({ success: true, device });
  } catch (e) { return res.status(databaseError(e) === 'P2025' ? 404 : 503).json({ success: false, error: databaseError(e) === 'P2025' ? 'DEVICE_NOT_FOUND' : 'DEVICE_UPDATE_FAILED' }); }
});
router.put('/devices/:id/block', authenticateAdmin(), validateBody(z.object({ isBlocked: z.boolean() }).strict()), async (req: AuthenticatedAdminRequest, res) => {
  try {
    const device = await prisma.$transaction(async tx => {
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`device:${req.params.id}`}))`;
      const current = await tx.device.findUniqueOrThrow({ where: { id: req.params.id } });
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${current.deviceId}`}))`;
      const updated = await tx.device.update({ where: { id: current.id }, data: { isBlocked: req.body.isBlocked }, select: deviceSelect });
      await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: updated.isBlocked ? 'BLOCK_DEVICE' : 'UNBLOCK_DEVICE', targetResource: 'DEVICE', targetId: updated.id } });
      return updated;
    });
    if (device.isBlocked) {
      const peers = await prisma.peerSession.findMany({ where: { deviceId: device.id, active: true }, include: { server: true } });
      for (const peer of peers) {
        try {
          await AgentClient.removePeer(peer.server.agentUrl, peer.server.agentSecretHash, { publicKey: peer.clientPublicKey });
          await prisma.peerSession.delete({ where: { id: peer.id } });
        } catch { return res.status(502).json({ success: false, error: 'DEVICE_BLOCKED_PEER_REMOVAL_PENDING', message: 'Device blocked; live peer removal needs agent recovery or manual removal.' }); }
      }
    }
    return res.json({ success: true, device });
  } catch (e) { return res.status(databaseError(e) === 'P2025' ? 404 : 503).json({ success: false, error: databaseError(e) === 'P2025' ? 'DEVICE_NOT_FOUND' : 'DEVICE_UPDATE_FAILED' }); }
});

// 5. Remote Configuration Management
router.get('/config', authenticateAdmin(), async (_req, res: Response) => {
  try {
    const config = await prisma.appConfig.upsert({ where: { id: 'global_config' }, create: { id: 'global_config' }, update: {} });
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
  authenticateAdmin('SUPER_ADMIN'),
  validateBody(UpdateConfigSchema),
  async (req: AuthenticatedAdminRequest, res: Response) => {
    try {
      const sanitized = { ...req.body };
      if (sanitized.announcementTitle === '') sanitized.announcementTitle = null;
      if (sanitized.announcementBody === '') sanitized.announcementBody = null;

      const updated = await prisma.$transaction(async tx => {
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${'config:global'}))`;
        const existing = await tx.appConfig.findUnique({ where: { id: 'global_config' } });
        const merged = { ...existing, ...sanitized };
        if ((merged.latestAppVersionCode ?? 1) < (merged.minAppVersionCode ?? 1)) throw new Error('VERSION_RANGE_INVALID');
        if (merged.announcementActive && (!merged.announcementTitle?.trim() || !merged.announcementBody?.trim())) throw new Error('ANNOUNCEMENT_CONTENT_REQUIRED');
        const result = await tx.appConfig.upsert({ where: { id: 'global_config' }, update: sanitized, create: { id: 'global_config', ...sanitized } });
        await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'UPDATE_CONFIG', targetResource: 'APP_CONFIG', targetId: 'global_config', metadata: JSON.stringify({ fields: Object.keys(sanitized) }) } });
        return result;
      });
      invalidateConfigCache();

      return res.json({
        success: true,
        config: updated,
        data: updated,
        lastUpdated: updated.updatedAt
      });
    } catch (err: unknown) {
      const invalid = err instanceof Error && ['VERSION_RANGE_INVALID','ANNOUNCEMENT_CONTENT_REQUIRED'].includes(err.message);
      return res.status(invalid ? 400 : 503).json({ success: false, error: invalid ? (err as Error).message : 'CONFIG_SAVE_FAILED' });
    }
  }
);

// 6. Audit Trail (Super Admin only)
router.get('/audit-logs', authenticateAdmin('SUPER_ADMIN'), async (req, res) => {
  const q = PageQuery.safeParse(req.query);
  if (!q.success) return res.status(400).json({ success: false, error: 'INVALID_QUERY' });
  try {
    const { page, limit, search, sort } = q.data;
    const where: Prisma.AuditLogWhereInput = search ? { OR: [{ action: { contains: search, mode: 'insensitive' } }, { targetResource: { contains: search, mode: 'insensitive' } }, { admin: { email: { contains: search, mode: 'insensitive' } } }] } : {};
    const [total, logs] = await prisma.$transaction([prisma.auditLog.count({ where }), prisma.auditLog.findMany({ where, take: limit, skip: (page - 1) * limit,
      orderBy: [{ createdAt: sort === 'oldest' ? 'asc' : 'desc' }, { id: 'asc' }], select: { id: true, action: true, targetResource: true, targetId: true, createdAt: true, admin: { select: { email: true, role: true } } } })]);
    return res.json({ success: true, logs, pagination: pagination(total, page, limit) });
  } catch { return res.status(503).json({ success: false, error: 'AUDIT_UNAVAILABLE' }); }
});

// 7. CSV Export of Anonymous Aggregated Data
router.get('/export/stats', authenticateAdmin(), async (_req, res) => {
  try {
    const stats = await prisma.anonymousStat.findMany({ orderBy: { date: 'desc' }, take: 1000 });
    const csv = 'Date,Country,AppVersion,Tier,ConnectionsCount\n' + stats.map(s => [s.date.toISOString().split('T')[0], s.countryCode, s.appVersion, s.tier, String(s.connectionsCount)].map(csvCell).join(',')).join('\n');
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename=nsecvpn_anonymous_stats.csv');
    return res.send(csv);
  } catch { return res.status(503).json({ success: false, error: 'EXPORT_UNAVAILABLE' }); }
});

export default router;
