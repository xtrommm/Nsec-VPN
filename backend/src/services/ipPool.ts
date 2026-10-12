import { Prisma } from '@prisma/client';
import { SubnetSchema } from '../types';

export class IpPoolService {
  static async allocateIp(tx: Prisma.TransactionClient, serverId: string, subnet: string, capacity: number): Promise<string> {
    SubnetSchema.parse(subnet);
    const sessions = await tx.peerSession.findMany({ where: { serverId, active: true }, select: { allocatedIp: true } });
    if (sessions.length >= capacity) throw new Error('SERVER_FULL');
    const used = new Set(sessions.map(s => s.allocatedIp));
    const prefix = subnet.split('/')[0].split('.').slice(0, 3).join('.');
    for (let host = 2; host <= 254; host++) {
      const ip = `${prefix}.${host}/32`;
      if (!used.has(ip)) return ip;
    }
    throw new Error('SERVER_FULL');
  }
}
