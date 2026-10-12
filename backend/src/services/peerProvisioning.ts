import crypto from 'crypto';
import { prisma } from '../db';
import { IpPoolService } from './ipPool';
import { AgentClient } from './agentClient';
import { testNode } from './nodeHealth';
import { EndpointSchema, PublicKeySchema } from '../types';

export async function provisionPeer(deviceId: string, serverId: string, clientPublicKey: string, tier: string) {
  PublicKeySchema.parse(clientPublicKey);
  const config = await prisma.appConfig.findUnique({ where: { id: 'global_config' } });
  if (config?.maintenanceMode) throw new Error('MAINTENANCE_MODE');
  const node = await prisma.serverNode.findUnique({ where: { id: serverId } });
  if (!node) throw new Error('SERVER_NOT_FOUND');
  if (!node.active) throw new Error('NODE_DISABLED');
  if (node.isPremiumOnly && tier === 'FREE') throw new Error('TIER_UPGRADE_REQUIRED');
  EndpointSchema.parse(node.endpoint);
  const health = await testNode(node);
  if (!health.online || !health.publicIpv4) throw new Error(health.agentOnline ? 'NODE_OFFLINE' : 'AGENT_UNREACHABLE');

  let agentConfirmed = false;
  try { return await prisma.$transaction(async tx => {
    // Cross-process lock: selection AND reservation AND agent confirmation share this transaction.
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`device:${deviceId}`}))`;
    const device = await tx.device.findUniqueOrThrow({ where: { id: deviceId } });
    if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
    const effectiveTier = device.passExpiresAt && device.passExpiresAt < new Date() ? 'FREE' : device.tier;
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${serverId}))`;
    const current = await tx.serverNode.findUniqueOrThrow({ where: { id: serverId } });
    if (current.isPremiumOnly && effectiveTier === 'FREE') throw new Error('TIER_UPGRADE_REQUIRED');
    if (!current.active || current.publicKey !== node.publicKey || current.endpoint !== node.endpoint) throw new Error('NODE_DISABLED');
    const keyOwner = await tx.peerSession.findFirst({ where: { serverId, clientPublicKey, active: true } });
    if (keyOwner && keyOwner.deviceId !== deviceId) throw new Error('KEY_ALREADY_REGISTERED');
    const sessions = await tx.peerSession.findMany({ where: { serverId, deviceId, active: true } });
    const old = sessions[0];
    const allocatedIp = old?.allocatedIp ?? await IpPoolService.allocateIp(tx, serverId, node.subnetIpv4, node.maxPeers);
    const leaseLimit = Date.now() + 24 * 60 * 60 * 1000;
    const expiresAt = new Date(current.isPremiumOnly && device.passExpiresAt ?
      Math.min(leaseLimit, device.passExpiresAt.getTime()) : leaseLimit);
    // Remove old keys before reassigning their address, never accept a caller-supplied disconnect key.
    for (const session of sessions) {
      if (session.clientPublicKey !== clientPublicKey) await AgentClient.removePeer(node.agentUrl, node.agentSecretHash, { publicKey: session.clientPublicKey });
    }
    try {
      await AgentClient.addPeer(node.agentUrl, node.agentSecretHash, { publicKey: clientPublicKey, allocatedIp, expiresAt: expiresAt.toISOString() });
    } catch {
      // Timeout is ambiguous. Compensate; agent lease expiry is the final safety net if unreachable.
      try { await AgentClient.removePeer(node.agentUrl, node.agentSecretHash, { publicKey: clientPublicKey }); } catch { /* Agent lease expires independently. */ }
      throw new Error('AGENT_UNREACHABLE');
    }
    agentConfirmed = true;
    const session = old ? await tx.peerSession.update({ where: { id: old.id }, data: { clientPublicKey, expiresAt } }) :
      await tx.peerSession.create({ data: { peerId: `peer_${crypto.randomBytes(12).toString('hex')}`, deviceId, serverId,
        allocatedIp, clientPublicKey, expiresAt, active: true } });
    return { peerId: session.peerId, tunnelConfig: {
      serverPublicKey: node.publicKey, serverEndpoint: node.endpoint,
      endpointHost: node.endpoint.split(':')[0], endpointPort: Number(node.endpoint.split(':')[1]),
      clientAddress: allocatedIp, dnsServers: node.dnsServers, allowedIps: ['0.0.0.0/0'],
      persistentKeepalive: 25, mtu: node.mtu, nodeId: node.id, nodeName: node.name,
      locationName: `${node.city}, ${node.countryName}`, publicIpv4: health.publicIpv4
    } };
  }, { timeout: 20000, maxWait: 10000 });
  } catch (error) {
    if (agentConfirmed) {
      try { await AgentClient.removePeer(node.agentUrl, node.agentSecretHash, { publicKey: clientPublicKey }); }
      catch { /* Durable agent expiry is the final cleanup path. */ }
    }
    throw error;
  }
}

let expiring = false;
export async function expirePeers() {
  if (expiring) return;
  expiring = true;
  try {
    const sessions = await prisma.peerSession.findMany({ where: { active: true, OR: [{ expiresAt: { lt: new Date() } }, { device: { isBlocked: true } }, { server: { active: false } }] }, take: 100 });
    for (const candidate of sessions) {
      try {
        await prisma.$transaction(async tx => {
          await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${candidate.serverId}))`;
          // Selection can be stale: re-read under the same lock as provisioning.
          const s = await tx.peerSession.findFirst({ where: { id: candidate.id, active: true }, include: { server: true, device: true } });
          if (!s || (s.expiresAt >= new Date() && !s.device.isBlocked && s.server.active)) return;
          await AgentClient.removePeer(s.server.agentUrl, s.server.agentSecretHash, { publicKey: s.clientPublicKey });
          await tx.peerSession.delete({ where: { id: s.id } });
        }, { timeout: 20000 });
      } catch { /* Retain the reservation and retry; never delete a renewed lease. */ }
    }
  } finally { expiring = false; }
}
