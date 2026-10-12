import { prisma } from '../db';
import { provisionPeer } from './peerProvisioning';

export type NodeLatency = { nodeId: string; pingMs: number };

/** Advisory latency only affects ranking. The transaction remains the capacity/auth authority. */
export async function provisionLocationPeer(deviceId: string, serverId: string, publicKey: string,
  latencies: NodeLatency[] = [], excludedNodeIds: string[] = []) {
  const device = await prisma.device.findUniqueOrThrow({ where: { id: deviceId } });
  if (device.isBlocked) throw new Error('DEVICE_BLOCKED');
  const tier = device.passExpiresAt && device.passExpiresAt < new Date() ? 'FREE' : device.tier;
  const anchor = await prisma.serverNode.findUnique({ where: { id: serverId } });
  if (!anchor) throw new Error('SERVER_NOT_FOUND');
  if (anchor.isPremiumOnly && tier === 'FREE') throw new Error('TIER_UPGRADE_REQUIRED');
  const countryNodes = await prisma.serverNode.findMany({ where: {
    id: { notIn: excludedNodeIds },
    active: true, healthy: true, healthCheckedAt: { gt: new Date(Date.now() - 120000) },
    countryCode: { equals: anchor.countryCode, mode: 'insensitive' },
    ...(tier === 'FREE' ? { isPremiumOnly: false } : {})
  } });
  const nodes = countryNodes.filter(n => n.city.trim().toLowerCase() === anchor.city.trim().toLowerCase());
  if (!nodes.length) throw new Error('NODE_OFFLINE');
  const where = { serverId: { in: nodes.map(n => n.id) }, active: true };
  const [reservations, ownReservations] = await Promise.all([
    prisma.peerSession.groupBy({ by: ['serverId'], where, _count: { _all: true } }),
    prisma.peerSession.findMany({ where: { ...where, deviceId }, select: { serverId: true } })
  ]);
  const counts = new Map(reservations.map(lease => [lease.serverId, lease._count._all]));
  const owned = new Set(ownReservations.map(lease => lease.serverId));
  const available = nodes.filter(n => owned.has(n.id) || (counts.get(n.id) ?? 0) < Math.min(n.maxPeers, 253));
  if (!available.length) throw new Error(excludedNodeIds.length ? 'NODE_OFFLINE' : 'ALL_SERVERS_FULL');
  const pings = new Map(latencies.map(l => [l.nodeId, l.pingMs]));
  const score = (n: typeof nodes[number]) => (pings.get(n.id) ?? 999) +
    Math.ceil((counts.get(n.id) ?? 0) * 100 / Math.max(1, Math.min(n.maxPeers, 253))) * 2;
  available.sort((a, b) => score(a) - score(b) || a.id.localeCompare(b.id));
  let full = 0;
  let networkFailures = 0;
  for (const node of available) {
    try { return await provisionPeer(deviceId, node.id, publicKey, tier); }
    catch (error) {
      const code = error instanceof Error ? error.message : '';
      if (code === 'SERVER_FULL') { full++; continue; }
      if (['NODE_OFFLINE', 'NODE_DISABLED', 'AGENT_UNREACHABLE'].includes(code)) {
        // Capacity misses are cheap and uncapped; network failures are bounded.
        networkFailures++;
        if (networkFailures < 3) continue;
      }
      throw error;
    }
  }
  throw new Error(full === available.length ? 'ALL_SERVERS_FULL' : 'NODE_OFFLINE');
}
