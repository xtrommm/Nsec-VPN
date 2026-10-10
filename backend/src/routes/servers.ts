import { Router, Response } from 'express';
import { prisma } from '../db';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';

const router = Router();

router.get('/', authenticateDevice, async (req: AuthenticatedDeviceRequest, res: Response) => {
  const device = req.device!;
  const includePremiumParam = req.query.includePremium;
  const hidePremiumForFree = process.env.HIDE_PREMIUM_FROM_FREE === 'true';
  const groupByLocation = req.query.grouped === 'true';

  // If environment flag is set or client explicitly asks to exclude premium, filter them
  const shouldHidePremium =
    includePremiumParam === 'false' ||
    (hidePremiumForFree && device.tier === 'FREE' && includePremiumParam !== 'true');

  try {
    const whereClause: any = { active: true };
    if (shouldHidePremium) {
      whereClause.isPremiumOnly = false;
    }

    const servers = await prisma.serverNode.findMany({
      where: whereClause,
      select: {
        id: true,
        name: true,
        countryCode: true,
        countryName: true,
        city: true,
        flagEmoji: true,
        currentLoad: true,
        isPremiumOnly: true,
        isRecommended: true
      },
      orderBy: [{ isRecommended: 'desc' }, { currentLoad: 'asc' }]
    });

    // If client requests servers grouped by geographic location
    if (groupByLocation) {
      const clusterMap = new Map<string, typeof servers>();
      for (const s of servers) {
        const key = `${s.countryCode.toUpperCase()}_${s.city.toLowerCase()}_${s.isPremiumOnly}`;
        if (!clusterMap.has(key)) clusterMap.set(key, []);
        clusterMap.get(key)!.push(s);
      }

      const groupedServers = Array.from(clusterMap.values()).map(pool => {
        // Least loaded node represents the pool
        const best = pool.sort((a, b) => a.currentLoad - b.currentLoad)[0];
        const avgLoad = Math.round(pool.reduce((sum, item) => sum + item.currentLoad, 0) / pool.length);
        return {
          id: best.id,
          name: best.city,
          countryCode: best.countryCode,
          countryName: best.countryName,
          city: best.city,
          flagEmoji: best.flagEmoji,
          currentLoad: avgLoad,
          isPremiumOnly: best.isPremiumOnly,
          isRecommended: pool.some(item => item.isRecommended),
          totalNodes: pool.length
        };
      });

      return res.json({
        success: true,
        data: {
          servers: groupedServers
        }
      });
    }

    // Default mode: Count cluster nodes per location so clients can display node count
    const locationCounts = new Map<string, number>();
    for (const s of servers) {
      const key = `${s.countryCode.toUpperCase()}_${s.city.toLowerCase()}_${s.isPremiumOnly}`;
      locationCounts.set(key, (locationCounts.get(key) || 0) + 1);
    }

    const serversWithCount = servers.map(s => ({
      ...s,
      totalNodes: locationCounts.get(`${s.countryCode.toUpperCase()}_${s.city.toLowerCase()}_${s.isPremiumOnly}`) || 1
    }));

    return res.json({
      success: true,
      data: {
        servers: serversWithCount
      }
    });
  } catch (err) {
    return res.status(500).json({ success: false, error: 'DB_ERROR', message: 'Failed to fetch servers' });
  }
});

export default router;
