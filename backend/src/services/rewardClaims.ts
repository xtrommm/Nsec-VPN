import { prisma } from '../db';

export async function prepareReward(deviceId: string) {
  return prisma.$transaction(async tx => {
    const device = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${device.deviceId}`}))`;
    const config = await tx.appConfig.findUnique({ where: { id: 'global_config' } });
    if (device.isBlocked || config?.rewardedAdsEnabled === false) throw new Error('REWARDED_ADS_DISABLED');
    const now = new Date();
    const existing = await tx.rewardTicket.findFirst({ where: { deviceId, claimedAt: null, expiresAt: { gt: now } } });
    if (existing) return { ticketId: existing.id };
    const ticket = await tx.rewardTicket.create({ data: { deviceId, expiresAt: new Date(Date.now() + 10 * 60000) } });
    return { ticketId: ticket.id };
  });
}

export async function claimReward(deviceId: string, ticketId: string) {
  return prisma.$transaction(async tx => {
    const identity = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${identity.deviceId}`}))`;
    const device = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
    const ticket = await tx.rewardTicket.findUnique({ where: { id: ticketId } });
    if (!ticket || ticket.deviceId !== deviceId) throw new Error('REWARD_TICKET_INVALID');
    if (ticket.claimedAt) return { tier: device.tier, passExpiresAt: device.passExpiresAt };
    if (ticket.expiresAt <= new Date()) throw new Error('REWARD_TICKET_EXPIRED');
    const config = await tx.appConfig.findUnique({ where: { id: 'global_config' } });
    if (config?.rewardedAdsEnabled === false) throw new Error('REWARDED_ADS_DISABLED');
    // One grant per ten minutes; one-use claims cannot stack or extend an existing longer pass.
    const recent = await tx.rewardTicket.findFirst({ where: { deviceId, claimedAt: { gt: new Date(Date.now() - 10 * 60000) } } });
    if (recent) throw new Error('REWARD_COOLDOWN');
    const minutes = config?.rewardedPassMinutes ?? 30;
    const until = new Date(Date.now() + minutes * 60000);
    await tx.rewardTicket.update({ where: { id: ticketId }, data: { claimedAt: new Date(), grantExpiresAt: until } });
    if (device.tier === 'VIP' && (!device.passExpiresAt || device.passExpiresAt > until))
      return { tier: device.tier, passExpiresAt: device.passExpiresAt };
    const updated = await tx.device.update({ where: { id: deviceId }, data: { tier: 'VIP', passExpiresAt: until } });
    return { tier: updated.tier, passExpiresAt: updated.passExpiresAt };
  });
}
