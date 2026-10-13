import { prisma } from '../db';
import { readSnapshot } from './readSnapshot';

export class StatsService {
  /**
   * Records an anonymous connection count per date, country, and app version.
   * Absolutely NO IP addresses, user identifiers, or traffic payload are stored or logged.
   */
  static async recordConnection(countryCode: string, appVersion: string, tier: string): Promise<void> {
    const now = new Date();
    const today = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));

    try {
      await prisma.$transaction(async tx => {
        const key = `stat:${today.toISOString()}:${countryCode.toUpperCase()}:${appVersion}:${tier}`;
        await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${key}))`;
      const existing = await tx.anonymousStat.findFirst({
        where: {
          date: today,
          countryCode: countryCode.toUpperCase(),
          appVersion,
          tier
        }
      });

      if (existing) {
        await tx.anonymousStat.update({
          where: { id: existing.id },
          data: { connectionsCount: { increment: 1 } }
        });
      } else {
        await tx.anonymousStat.create({
          data: {
            date: today,
            countryCode: countryCode.toUpperCase(),
            appVersion,
            tier,
            connectionsCount: 1
          }
        });
      }
      });
    } catch (_err) {
      // Fire-and-forget; never block the connection flow on telemetry write errors
    }
  }

  /**
   * Retrieves aggregated dashboard KPI metrics for the Admin Panel.
   */
  static getDashboardMetrics() { return dashboardSnapshot.read(); }

  static async loadDashboardMetrics() {
    const [totalDevices, vipCount, activeTunnels, servers, topCountries] = await Promise.all([
      prisma.device.count(),
      prisma.device.count({ where: { tier: 'VIP', OR: [{ passExpiresAt: null }, { passExpiresAt: { gt: new Date() } }] } }),
      prisma.peerSession.count({ where: { active: true, expiresAt: { gt: new Date() } } }),
      prisma.serverNode.findMany({
        select: {
          id: true,
          name: true,
          countryCode: true,
          currentLoad: true,
          active: true,
          isPremiumOnly: true
        }
      }),
      prisma.anonymousStat.groupBy({
        by: ['countryCode'],
        _sum: { connectionsCount: true },
        orderBy: { _sum: { connectionsCount: 'desc' } },
        take: 5
      })
    ]);

    const activeServers = servers.filter(s => s.active);
    const avgLoad =
      activeServers.length > 0
        ? Math.round(activeServers.reduce((acc, s) => acc + s.currentLoad, 0) / activeServers.length)
        : 0;

    return {
      activeTunnels,
      totalDevices,
      vipSubscribers: vipCount,
      totalServers: servers.length,
      activeServers: activeServers.length,
      avgServerLoad: avgLoad,
      servers,
      topCountries: topCountries.map(tc => ({
        countryCode: tc.countryCode,
        count: tc._sum.connectionsCount || 0
      }))
    };
  }
}

const dashboardSnapshot = readSnapshot(2000, () => StatsService.loadDashboardMetrics());
