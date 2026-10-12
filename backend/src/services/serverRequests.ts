import { z } from 'zod';
import { prisma } from '../db';

const location = z.string().trim().min(2).max(80).refine(v => !/[\u0000-\u001f\u007f<>]/.test(v), 'Enter a plain-text location');
export const ServerRequestInput = z.object({ country: location, city: location,
  note: z.string().trim().max(500).refine(v => !/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(v), 'Invalid note').optional() }).strict();
export const RequestStatus = z.enum(['PENDING', 'APPROVED', 'REJECTED', 'DONE']);
export const REQUEST_COOLDOWN_MS = 10 * 60000;
export async function submitServerRequest(deviceId: string, input: z.infer<typeof ServerRequestInput>) {
  return prisma.$transaction(async tx => {
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`request:${deviceId}`}))`;
    const device = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
    const latest = await tx.serverRequest.findFirst({ where: { deviceId }, orderBy: { createdAt: 'desc' } });
    const now = new Date();
    if (latest && now.getTime() - latest.createdAt.getTime() < REQUEST_COOLDOWN_MS) {
      return { request: null, retryAfterSeconds: Math.ceil((latest.createdAt.getTime() + REQUEST_COOLDOWN_MS - now.getTime()) / 1000) };
    }
    const request = await tx.serverRequest.create({ data: { ...input, note: input.note || null, deviceId, appVersion: device.appVersion, status: 'PENDING' },
      select: { id: true, country: true, city: true, note: true, appVersion: true, status: true, createdAt: true } });
    return { request, retryAfterSeconds: 0 };
  });
}
