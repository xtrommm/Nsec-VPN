import { invalidateServerCatalog } from './serverCatalog';
import { ServerNode } from '@prisma/client';
import { lookup } from 'dns/promises';
import { prisma } from '../db';
import { EndpointSchema, SubnetSchema, isPublicIpv4 } from '../types';
import { AgentClient } from './agentClient';
import { peerCapacityPercent } from './nodeLoad';

const probeFields = (node: ServerNode) => ({
  id: node.id, endpoint: node.endpoint, publicKey: node.publicKey, agentUrl: node.agentUrl,
  agentSecretHash: node.agentSecretHash, subnetIpv4: node.subnetIpv4,
  maxPeers: node.maxPeers, mtu: node.mtu, active: node.active, dnsServers: node.dnsServers
});

export async function testNode(node: ServerNode, syncKey = false, adminId?: string) {
  const health = await AgentClient.getNodeHealth(node.agentUrl, node.agentSecretHash);
  const endpoint = EndpointSchema.safeParse(node.endpoint);
  const subnet = SubnetSchema.safeParse(node.subnetIpv4);
  let resolves = false;
  if (endpoint.success) {
    try {
      let timer: NodeJS.Timeout | undefined;
      const addresses = await Promise.race([
        lookup(node.endpoint.split(':')[0], { all: true, family: 4 }),
        new Promise<never>((_resolve, reject) => { timer = setTimeout(() => reject(new Error('NODE_DNS_TIMEOUT')), 2000); })
      ]).finally(() => { if (timer) clearTimeout(timer); });
      resolves = addresses.length > 0 && addresses.every(a => isPublicIpv4(a.address)) &&
        addresses.some(a => a.address === health.publicIpv4);
    } catch { resolves = false; }
  }
  const keyMatches = health.publicKey === node.publicKey;
  const portMatches = Number(node.endpoint.split(':')[1]) === health.listenPort;
  const healthy = health.online && resolves && subnet.success && portMatches && (keyMatches || syncKey);
  const data = {
    healthy, healthCheckedAt: new Date(),
    ...(health.online ? { currentLoad: peerCapacityPercent(health.peerCount, node.maxPeers) } : {}),
    ...(health.online ? { publicIpv4: health.publicIpv4 } : {}),
    ...(syncKey && health.online ? { publicKey: health.publicKey } : {})
  };
  if (syncKey && adminId) {
    await prisma.$transaction(async tx => {
      await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${node.id}))`;
      const current = await tx.serverNode.findUniqueOrThrow({ where: { id: node.id } });
      if (JSON.stringify(probeFields(current)) !== JSON.stringify(probeFields(node))) throw new Error('NODE_CHANGED');
      await tx.serverNode.update({ where: { id: node.id }, data });
      await tx.auditLog.create({ data: { adminId, action: 'SYNC_NODE_PUBLIC_KEY', targetResource: 'SERVER', targetId: node.id } });
    });
  } else {
    // A stale health probe must never mark a changed endpoint or key healthy.
    const result = await prisma.serverNode.updateMany({ where: { ...probeFields(node), dnsServers: { equals: node.dnsServers } }, data });
    if (!result.count) return { ...health, online: false, agentOnline: health.online, keyMatches, portMatches, resolves, error: 'NODE_CHANGED' };
  }
  invalidateServerCatalog();
  return { ...health, online: healthy, agentOnline: health.online, keyMatches, portMatches, resolves,
    endpoint: node.endpoint, dnsServers: node.dnsServers, mtu: node.mtu, nodeName: node.name,
    locationName: `${node.city}, ${node.countryName}`, error: healthy ? undefined : 'NODE_VALIDATION_FAILED' };
}
let refreshing = false;
export async function refreshNodeHealth() {
  if (refreshing) return;
  refreshing = true;
  try {
    const nodes = await prisma.serverNode.findMany({ where: { active: true } });
    // A slow node cannot block every fast node in its old four-node chunk.
    let next = 0, failures = 0;
    await Promise.all(Array.from({ length: Math.min(8, nodes.length) }, async () => {
      while (next < nodes.length) {
        const node = nodes[next++];
        try { await testNode(node); } catch { failures++; }
      }
    }));
    if (failures) throw new Error('NODE_HEALTH_REFRESH_PARTIAL_FAILURE');
  } finally { refreshing = false; }
}
