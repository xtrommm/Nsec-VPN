import { ServerNode } from '@prisma/client';
import { lookup } from 'dns/promises';
import { prisma } from '../db';
import { EndpointSchema, SubnetSchema, isPublicIpv4 } from '../types';
import { AgentClient } from './agentClient';

export async function testNode(node: ServerNode, syncKey = false) {
  const health = await AgentClient.getNodeHealth(node.agentUrl, node.agentSecretHash);
  const endpoint = EndpointSchema.safeParse(node.endpoint);
  const subnet = SubnetSchema.safeParse(node.subnetIpv4);
  let resolves = false;
  if (endpoint.success) {
    try {
      const addresses = await lookup(node.endpoint.split(':')[0], { all: true, family: 4 });
      resolves = addresses.length > 0 && addresses.every(a => isPublicIpv4(a.address)) &&
        addresses.some(a => a.address === health.publicIpv4);
    } catch { resolves = false; }
  }
  const keyMatches = health.publicKey === node.publicKey;
  const portMatches = Number(node.endpoint.split(':')[1]) === health.listenPort;
  const healthy = health.online && resolves && subnet.success && portMatches && (keyMatches || syncKey);
  await prisma.serverNode.update({ where: { id: node.id }, data: {
    healthy, healthCheckedAt: new Date(),
    ...(health.online ? { currentLoad: Math.min(100, Math.round(health.peerCount / node.maxPeers * 100)) } : {}),
    ...(health.online ? { publicIpv4: health.publicIpv4 } : {}),
    ...(syncKey && health.online ? { publicKey: health.publicKey } : {})
  } });
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
    // Bounded concurrency, no automatic adoption of a changed identity/key.
    for (let i = 0; i < nodes.length; i += 4) await Promise.all(nodes.slice(i, i + 4).map(n => testNode(n).catch(() => undefined)));
  } finally { refreshing = false; }
}
