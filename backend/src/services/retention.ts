import { Prisma } from '@prisma/client';
import { prisma } from '../db';

export function retentionDays(value = process.env.RETENTION_DAYS): number {
  const days = value === undefined || value === '' ? 90 : Number(value);
  if (!Number.isInteger(days) || days < 1 || days > 3650) throw new Error('RETENTION_DAYS_INVALID');
  return days;
}

/** Preserve replay/abuse ledgers, recovery credentials, blocked identities and all peer reservations. */
export function inactiveDeviceFilter(cutoff: Date, now = new Date()): Prisma.DeviceWhereInput {
  return { tier: 'FREE', isBlocked: false, updatedAt: { lt: cutoff }, recoveryPinHash: null,
    passExpiresAt: null, tokens: { none: { OR: [{ createdAt: { gte: cutoff } }, { expiresAt: { gt: now } }] } },
    peerSessions: { none: {} }, promoRedemptions: { none: {} },
    rewardTickets: { none: { OR: [{ expiresAt: { gte: cutoff } }, { claimedAt: { not: null } }] } },
    serverRequests: { none: { OR: [{ status: { in: ['PENDING', 'APPROVED'] } }, { updatedAt: { gte: cutoff } }] } } };
}

let running = false;
export async function runRetention(now = new Date()) {
  if (running) return;
  running = true;
  try {
    const cutoff = new Date(now.getTime() - retentionDays() * 86400000);
    const where = inactiveDeviceFilter(cutoff, now);
    const candidates = await prisma.device.findMany({ where, select: { id: true, deviceId: true }, take: 100 });
    let devices = 0;
    for (const candidate of candidates) {
      devices += await prisma.$transaction(async tx => {
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`device:${candidate.id}`}))`;
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${candidate.deviceId}`}))`;
        if (await tx.purchaseClaim.findFirst({ where: { deviceId: candidate.id }, select: { tokenHash: true } })) return 0;
        return (await tx.device.deleteMany({ where: { AND: [{ id: candidate.id }, where] } })).count;
      });
    }
    const [tokens, tickets, requests, audit] = await prisma.$transaction([
      prisma.refreshToken.deleteMany({ where: { expiresAt: { lt: cutoff } } }),
      prisma.rewardTicket.deleteMany({ where: { expiresAt: { lt: cutoff }, claimedAt: null } }),
      prisma.serverRequest.deleteMany({ where: { status: { in: ['REJECTED', 'DONE'] }, updatedAt: { lt: cutoff } } }),
      prisma.auditLog.deleteMany({ where: { createdAt: { lt: cutoff } } })
    ]);
    return { devices, tokens: tokens.count, tickets: tickets.count, requests: requests.count, audit: audit.count };
  } finally { running = false; }
}
