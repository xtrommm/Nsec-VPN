import { prisma } from '../db';

export class IpPoolService {
  /**
   * Parses an IPv4 CIDR string (e.g. "10.8.0.0/24") into a base prefix ("10.8.0")
   * and range boundaries.
   */
  private static parseSubnet(cidr: string): { prefix: string; startOctet: number; endOctet: number } {
    const parts = cidr.trim().split('/');
    const ip = parts[0];
    const mask = parts[1] ? parseInt(parts[1], 10) : 24;

    const octets = ip.split('.').map(o => parseInt(o, 10));
    if (octets.length !== 4 || octets.some(isNaN) || mask !== 24) {
      // Default fallback to 10.8.0.0/24 for standard WireGuard topologies
      return { prefix: '10.8.0', startOctet: 2, endOctet: 254 };
    }

    const prefix = `${octets[0]}.${octets[1]}.${octets[2]}`;
    // Host .1 is typically assigned to the server interface (wg0)
    return { prefix, startOctet: 2, endOctet: 254 };
  }

  /**
   * Atomically allocates the next available IP address in the server subnet.
   * Performs proactive expiration cleanup and retry backoff on concurrency contention.
   */
  static async allocateIp(serverId: string, customSubnet?: string): Promise<string> {
    // 1. Proactively clean up expired sessions on this server to free up address space
    await this.cleanExpiredSessionsForServer(serverId);

    // 2. Fetch server subnet configuration if not provided
    let subnetStr = customSubnet;
    if (!subnetStr) {
      const server = await prisma.serverNode.findUnique({
        where: { id: serverId },
        select: { subnetIpv4: true }
      });
      subnetStr = server?.subnetIpv4 || '10.8.0.0/24';
    }

    const { prefix, startOctet, endOctet } = this.parseSubnet(subnetStr);

    // 3. Allocate in a retry loop to ensure collision-free assignment under high load
    const maxRetries = 5;
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        const candidateIp = await prisma.$transaction(
          async (tx) => {
            // Find all active leased IPs for this server
            const activeSessions = await tx.peerSession.findMany({
              where: { serverId, active: true },
              select: { allocatedIp: true }
            });

            const usedOctets = new Set<number>();
            for (const s of activeSessions) {
              const match = s.allocatedIp.match(new RegExp(`^${prefix.replace(/\./g, '\\.')}\\.(\\d+)/32$`));
              if (match) {
                usedOctets.add(parseInt(match[1], 10));
              }
            }

            // Find first available host octet
            for (let octet = startOctet; octet <= endOctet; octet++) {
              if (!usedOctets.has(octet)) {
                return `${prefix}.${octet}/32`;
              }
            }

            throw new Error('SERVER_CAPACITY_REACHED: All available IP leases in the subnet are currently in use.');
          },
          {
            isolationLevel: 'Serializable',
            timeout: 5000
          }
        );

        return candidateIp;
      } catch (err: any) {
        if (err.message && err.message.startsWith('SERVER_CAPACITY_REACHED')) {
          throw err;
        }
        // In case of transient concurrency conflict (Prisma P2034 / serialization failure), retry
        if (attempt === maxRetries - 1) {
          throw err;
        }
        await new Promise((r) => setTimeout(r, 20 + Math.random() * 50));
      }
    }

    throw new Error('SERVER_CAPACITY_REACHED: Failed to allocate a unique IP address after multiple attempts.');
  }

  /**
   * Cleans up expired peer sessions for a specific server node.
   */
  static async cleanExpiredSessionsForServer(serverId: string): Promise<number> {
    const now = new Date();
    const result = await prisma.peerSession.updateMany({
      where: {
        serverId,
        active: true,
        expiresAt: { lt: now }
      },
      data: { active: false }
    });
    return result.count;
  }

  /**
   * Cleans up all expired peer leases across the entire system.
   */
  static async cleanExpiredSessions(): Promise<number> {
    const now = new Date();
    const result = await prisma.peerSession.updateMany({
      where: {
        active: true,
        expiresAt: { lt: now }
      },
      data: { active: false }
    });
    return result.count;
  }
}
