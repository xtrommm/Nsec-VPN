import crypto from 'crypto';
import { PeerSession, Prisma } from '@prisma/client';
import { prisma } from '../db';
import { IpPoolService } from './ipPool';
import { AgentClient } from './agentClient';
import { testNode } from './nodeHealth';
import { EndpointSchema, PublicKeySchema } from '../types';
import { enforceClientVersion } from './clientVersion';

const transactionOptions = { timeout: 20000, maxWait: 10000 };
export async function removeTrackedPeerKeys(node: { agentUrl: string; agentSecretHash: string }, session: Pick<PeerSession, 'clientPublicKey' | 'pendingPublicKey'>) {
  for (const publicKey of new Set([session.clientPublicKey, session.pendingPublicKey].filter((key): key is string => !!key)))
    await AgentClient.removePeer(node.agentUrl, node.agentSecretHash, { publicKey });
}
async function lockDeviceAndNode(tx: Prisma.TransactionClient, deviceId: string, serverId: string) {
  await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`device:${deviceId}`}))`;
  await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${serverId}))`;
}

export async function provisionPeer(deviceId: string, serverId: string, clientPublicKey: string, tier: string, versionCode?: number) {
  PublicKeySchema.parse(clientPublicKey);
  const config = await prisma.appConfig.findUnique({ where: { id: 'global_config' } });
  if (config?.maintenanceMode) throw new Error('MAINTENANCE_MODE');
  enforceClientVersion(config, versionCode);
  const node = await prisma.serverNode.findUnique({ where: { id: serverId } });
  if (!node) throw new Error('SERVER_NOT_FOUND');
  if (!node.active) throw new Error('NODE_DISABLED');
  if (node.isPremiumOnly && tier === 'FREE') throw new Error('TIER_UPGRADE_REQUIRED');
  EndpointSchema.parse(node.endpoint);
  const health = await testNode(node);
  if (!health.online || !health.publicIpv4) throw new Error(health.agentOnline ? 'NODE_OFFLINE' : 'AGENT_UNREACHABLE');

  const validateCurrent = async (tx: Prisma.TransactionClient) => {
    const policy = await tx.appConfig.findUnique({ where: { id: 'global_config' } });
    if (policy?.maintenanceMode) throw new Error('MAINTENANCE_MODE');
    enforceClientVersion(policy, versionCode);
    const device = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
    const effectiveTier = device.passExpiresAt && device.passExpiresAt <= new Date() ? 'FREE' : device.tier;
    const current = await tx.serverNode.findUniqueOrThrow({ where: { id: serverId } });
    if (current.isPremiumOnly && effectiveTier === 'FREE') throw new Error('TIER_UPGRADE_REQUIRED');
    if (!current.active || current.publicKey !== node.publicKey || current.endpoint !== node.endpoint ||
        current.subnetIpv4 !== node.subnetIpv4 || current.agentUrl !== node.agentUrl || current.agentSecretHash !== node.agentSecretHash)
      throw new Error('NODE_DISABLED');
    return { device, current };
  };
  // This transaction commits BEFORE an external add. An ambiguous timeout or later DB failure cannot free its address.
  const reservation = await prisma.$transaction(async tx => {
    await lockDeviceAndNode(tx, deviceId, serverId);
    const { device, current } = await validateCurrent(tx);
    const owner = await tx.peerSession.findFirst({ where: { serverId, active: true,
      OR: [{ clientPublicKey }, { pendingPublicKey: clientPublicKey }] } });
    if (owner && owner.deviceId !== deviceId) throw new Error('KEY_ALREADY_REGISTERED');
    const old = await tx.peerSession.findFirst({ where: { serverId, deviceId, active: true } });
    if (old?.pendingPublicKey === clientPublicKey) return old;
    if (old?.pendingPublicKey) {
      // Do not overwrite an uncertain key before its absence is acknowledged.
      try { await AgentClient.removePeer(current.agentUrl, current.agentSecretHash, { publicKey: old.pendingPublicKey }); }
      catch { throw new Error('PEER_CLEANUP_PENDING'); }
    }
    const allocatedIp = old?.allocatedIp ?? await IpPoolService.allocateIp(tx, serverId, current.subnetIpv4, current.maxPeers);
    const leaseLimit = Date.now() + 24 * 60 * 60 * 1000;
    const expiresAt = new Date(current.isPremiumOnly && device.passExpiresAt ? Math.min(leaseLimit, device.passExpiresAt.getTime()) : leaseLimit);
    // Each new negotiated lease has a fresh disconnect handle. A late disconnect for the previous lease is harmless.
    const peerId = `peer_${crypto.randomBytes(12).toString('hex')}`;
    const data = { peerId, pendingPublicKey: clientPublicKey, expiresAt };
    return old ? tx.peerSession.update({ where: { id: old.id }, data }) :
      tx.peerSession.create({ data: { ...data, deviceId, serverId, allocatedIp, clientPublicKey, active: true } });
  }, transactionOptions);

  const outcome = await prisma.$transaction(async tx => {
    await lockDeviceAndNode(tx, deviceId, serverId);
    const { device, current } = await validateCurrent(tx);
    const session = await tx.peerSession.findFirst({ where: { id: reservation.id, deviceId, serverId, active: true } });
    if (!session || session.peerId !== reservation.peerId || session.pendingPublicKey !== clientPublicKey)
      throw new Error('PEER_RESERVATION_CHANGED');
    try {
      if (session.clientPublicKey !== clientPublicKey)
        await AgentClient.removePeer(current.agentUrl, current.agentSecretHash, { publicKey: session.clientPublicKey });
      const expiresAt = new Date(current.isPremiumOnly && device.passExpiresAt ? Math.min(session.expiresAt.getTime(), device.passExpiresAt.getTime()) : session.expiresAt.getTime());
      await AgentClient.addPeer(current.agentUrl, current.agentSecretHash, {
        publicKey: clientPublicKey, allocatedIp: session.allocatedIp, expiresAt: expiresAt.toISOString()
      });
      session.expiresAt = expiresAt;
    } catch {
      try {
        await removeTrackedPeerKeys(current, session);
        await tx.peerSession.delete({ where: { id: session.id } });
      } catch { /* Preserve both keys/address until confirmed cleanup. Never roll back the committed reservation. */ }
      return { failure: 'AGENT_UNREACHABLE' as const };
    }
    await tx.peerSession.update({ where: { id: session.id }, data: { clientPublicKey, pendingPublicKey: null, expiresAt: session.expiresAt } });
    return { result: { peerId: session.peerId, tunnelConfig: {
      serverPublicKey: current.publicKey, serverEndpoint: current.endpoint,
      endpointHost: current.endpoint.split(':')[0], endpointPort: Number(current.endpoint.split(':')[1]),
      clientAddress: session.allocatedIp, dnsServers: current.dnsServers, allowedIps: ['0.0.0.0/0'],
      persistentKeepalive: 25, mtu: current.mtu, nodeId: current.id, nodeName: current.name,
      locationName: `${current.city}, ${current.countryName}`, publicIpv4: health.publicIpv4
    } } };
  }, transactionOptions);
  if ('failure' in outcome) throw new Error(outcome.failure);
  return outcome.result;
}

export async function disconnectPeer(deviceId: string, peerId: string) {
  return prisma.$transaction(async tx => {
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`device:${deviceId}`}))`;
    const candidate = await tx.peerSession.findFirst({ where: { peerId, deviceId } });
    if (!candidate) return;
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${candidate.serverId}))`;
    const session = await tx.peerSession.findFirst({ where: { id: candidate.id, peerId, deviceId }, include: { server: true } });
    if (!session) return;
    await removeTrackedPeerKeys(session.server, session);
    await tx.peerSession.delete({ where: { id: session.id } });
  }, transactionOptions);
}

let expiring = false;
let cleanupCursor: string | undefined;
export async function expirePeers() {
  if (expiring) return;
  expiring = true;
  try {
    const pendingBefore = new Date(Date.now() - 120000);
    const where: Prisma.PeerSessionWhereInput = { active: true, OR: [
      { expiresAt: { lte: new Date() } }, { device: { isBlocked: true } }, { server: { active: false } },
      { pendingPublicKey: { not: null }, updatedAt: { lt: pendingBefore } }
    ] };
    let sessions = await prisma.peerSession.findMany({ where: { ...where, ...(cleanupCursor ? { id: { gt: cleanupCursor } } : {}) },
      orderBy: { id: 'asc' }, take: 100 });
    if (!sessions.length && cleanupCursor) {
      cleanupCursor = undefined;
      sessions = await prisma.peerSession.findMany({ where, orderBy: { id: 'asc' }, take: 100 });
    }
    if (sessions.length) cleanupCursor = sessions[sessions.length - 1].id;
    for (const candidate of sessions) {
      try {
        await prisma.$transaction(async tx => {
          await lockDeviceAndNode(tx, candidate.deviceId, candidate.serverId);
          const session = await tx.peerSession.findFirst({ where: { id: candidate.id, active: true }, include: { server: true, device: true } });
          if (!session) return;
          const abandoned = session.pendingPublicKey && session.updatedAt < pendingBefore;
          if (!abandoned && session.expiresAt > new Date() && !session.device.isBlocked && session.server.active) return;
          await removeTrackedPeerKeys(session.server, session);
          await tx.peerSession.delete({ where: { id: session.id } });
        }, transactionOptions);
      } catch { /* Retain reservations when cleanup is uncertain; retry next pass. */ }
    }
  } finally { expiring = false; }
}
