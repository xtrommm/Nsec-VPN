import { DeviceIdSchema } from '../types';
import { Router } from 'express';
import { Prisma } from '@prisma/client';
import { z } from 'zod';
import { prisma } from '../db';
import { authenticateAdmin, authenticateDevice, AuthenticatedAdminRequest, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { PageQuery, pagination, databaseError } from '../services/consoleQuery';
import { RequestStatus, ServerRequestInput, submitServerRequest } from '../services/serverRequests';

export const adminRequests = Router();
export const deviceRequests = Router();
adminRequests.use(authenticateAdmin());
adminRequests.use((_req, res, next) => { res.setHeader('Cache-Control', 'no-store'); next(); });
const querySchema = PageQuery.extend({ status: RequestStatus.optional(), deviceId: DeviceIdSchema.optional() });
adminRequests.get('/', async (req, res) => {
  const q = querySchema.safeParse(req.query);
  if (!q.success) return res.status(400).json({ success: false, error: 'INVALID_QUERY' });
  const { page, limit, search, status, deviceId } = q.data;
  const where: Prisma.ServerRequestWhereInput = { ...(status ? { status } : {}), ...(deviceId ? { device: { deviceId } } : {}),
    ...(search ? { OR: [{ country: { contains: search, mode: 'insensitive' } }, { city: { contains: search, mode: 'insensitive' } }, { device: { deviceId: { contains: search, mode: 'insensitive' } } }] } : {}) };
  try {
    const [total, requests] = await prisma.$transaction([prisma.serverRequest.count({ where }), prisma.serverRequest.findMany({ where, skip: (page - 1) * limit, take: limit,
      orderBy: [{ createdAt: q.data.sort === 'oldest' ? 'asc' : 'desc' }, { id: 'asc' }],
      select: { id: true, country: true, city: true, note: true, appVersion: true, status: true, createdAt: true, updatedAt: true, device: { select: { deviceId: true } } } })]);
    return res.json({ success: true, requests, pagination: pagination(total, page, limit) });
  } catch { return res.status(503).json({ success: false, error: 'REQUESTS_UNAVAILABLE' }); }
});
adminRequests.put('/:id', validateBody(z.object({ status: RequestStatus }).strict()), async (req: AuthenticatedAdminRequest, res) => {
  try {
    const request = await prisma.$transaction(async tx => {
      const updated = await tx.serverRequest.update({ where: { id: req.params.id }, data: { status: req.body.status } });
      await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'UPDATE_SERVER_REQUEST', targetResource: 'SERVER_REQUEST', targetId: updated.id, metadata: JSON.stringify({ status: updated.status }) } });
      return updated;
    });
    return res.json({ success: true, request });
  } catch (e) { return res.status(databaseError(e) === 'P2025' ? 404 : 503).json({ success: false, error: databaseError(e) === 'P2025' ? 'REQUEST_NOT_FOUND' : 'REQUEST_UPDATE_FAILED' }); }
});
adminRequests.delete('/:id', authenticateAdmin('SUPER_ADMIN'), async (req: AuthenticatedAdminRequest, res) => {
  try {
    await prisma.$transaction(async tx => {
      await tx.serverRequest.delete({ where: { id: req.params.id } });
      await tx.auditLog.create({ data: { adminId: req.admin!.adminId, action: 'DELETE_SERVER_REQUEST', targetResource: 'SERVER_REQUEST', targetId: req.params.id } });
    });
    return res.json({ success: true });
  } catch (e) { return res.status(databaseError(e) === 'P2025' ? 404 : 503).json({ success: false, error: databaseError(e) === 'P2025' ? 'REQUEST_NOT_FOUND' : 'REQUEST_DELETE_FAILED' }); }
});
deviceRequests.post('/', authenticateDevice, validateBody(ServerRequestInput), async (req: AuthenticatedDeviceRequest, res) => {
  try {
    const result = await submitServerRequest(req.device!.id, ServerRequestInput.parse(req.body));
    if (!result.request) { res.setHeader('Retry-After', result.retryAfterSeconds); return res.status(429).json({ success: false, error: 'REQUEST_COOLDOWN', retryAfterSeconds: result.retryAfterSeconds }); }
    return res.status(201).json({ success: true, data: result.request });
  } catch (e) { return res.status(e instanceof Error && e.message === 'DEVICE_BLOCKED' ? 403 : 503).json({ success: false, error: e instanceof Error && e.message === 'DEVICE_BLOCKED' ? 'DEVICE_BLOCKED' : 'REQUEST_SUBMISSION_FAILED' }); }
});
