import { prisma } from '../db';
import { VerifiedReward } from './rewardVerification';
import { preserveNonPurchaseGrant } from './purchaseReconciliation';

export async function prepareReward(deviceId: string) {
  return prisma.$transaction(async tx => {
    const identity = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${identity.deviceId}`}))`;
    const device = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    const config = await tx.appConfig.findUnique({ where: { id: 'global_config' } });
    if (device.isBlocked || config?.adsEnabled === false || config?.rewardedAdsEnabled === false) throw new Error('REWARDED_ADS_DISABLED');
    const now = new Date();
    const existing = await tx.rewardTicket.findFirst({ where: { deviceId, claimedAt: null, expiresAt: { gt: now } } });
    if (existing) return { ticketId: existing.id };
    const recent = await tx.rewardTicket.findFirst({ where: { deviceId, claimedAt: { gt: new Date(now.getTime() - 10 * 60000) } } });
    if (recent) throw new Error('REWARD_COOLDOWN');
    const ticket = await tx.rewardTicket.create({ data: { deviceId, expiresAt: new Date(now.getTime() + 10 * 60000) } });
    return { ticketId: ticket.id };
  });
}

/** A client callback is never proof that a paid ad was watched. This endpoint only reads a verified grant. */
export async function claimReward(deviceId: string, ticketId: string) {
  return prisma.$transaction(async tx => {
    const identity = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${identity.deviceId}`}))`;
    const device = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
    const ticket = await tx.rewardTicket.findUnique({ where: { id: ticketId } });
    if (!ticket || ticket.deviceId !== deviceId) throw new Error('REWARD_TICKET_INVALID');
    if (!ticket.claimedAt) {
      if (ticket.expiresAt <= new Date()) throw new Error('REWARD_TICKET_EXPIRED');
      throw new Error('REWARD_VERIFICATION_PENDING');
    }
    return { tier: device.tier, passExpiresAt: device.passExpiresAt };
  });
}

/** Called exclusively after Google's signature, ad-unit, event-time and identity validation. */
export async function grantVerifiedReward(reward: VerifiedReward) {
  await prisma.$transaction(async tx => {
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${reward.deviceId}`}))`;
    const device = await tx.device.findUniqueOrThrow({ where: { deviceId: reward.deviceId } });
    if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
    const ticket = reward.ticketId ? await tx.rewardTicket.findUnique({ where: { id: reward.ticketId } }) : null;
    if (reward.ticketId && (!ticket || ticket.deviceId !== device.id || reward.timestamp < ticket.createdAt.getTime() - 60000 || reward.timestamp > ticket.expiresAt.getTime()))
      throw new Error('SSV_TICKET_INVALID');
    // The unique transaction id makes callback retries idempotent, including legacy signed callbacks without custom_data.
    await tx.rewardGrant.create({ data: { transactionId: reward.transactionId } });
    if (ticket?.claimedAt) return;
    const config = await tx.appConfig.findUnique({ where: { id: 'global_config' } });
    if (config?.adsEnabled === false || config?.rewardedAdsEnabled === false) throw new Error('REWARDED_ADS_DISABLED');
    if (ticket) {
      const recent = await tx.rewardTicket.findFirst({ where: { deviceId: device.id, claimedAt: { gt: new Date(reward.timestamp - 10 * 60000) } } });
      if (recent) throw new Error('REWARD_COOLDOWN');
    }
    const minutes = config?.rewardedPassMinutes ?? Number(process.env.REWARDED_PASS_MINUTES || '30');
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) throw new Error('REWARD_DURATION_INVALID');
    const until = new Date(reward.timestamp + minutes * 60000);
    if (ticket) await tx.rewardTicket.update({ where: { id: ticket.id }, data: { claimedAt: new Date(), grantExpiresAt: until } });
    if (until > new Date()) await preserveNonPurchaseGrant(tx,device.id,until);
    // Delayed callbacks must not restart a pass or shorten a paid/promo/admin grant.
    if (until <= new Date() || (device.tier === 'VIP' && (!device.passExpiresAt || device.passExpiresAt >= until))) return;
    await tx.device.update({ where: { id: device.id }, data: { tier: 'VIP', passExpiresAt: until } });
  });
}
