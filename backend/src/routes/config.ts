import { Router, Request, Response } from 'express';
import { prisma } from '../db';

const router = Router();

// In-memory cache for high-throughput config queries
let cachedConfig: any = null;
let cachedConfigExpiry = 0;
const CACHE_TTL_MS = 60 * 1000; // 60 seconds

export function invalidateConfigCache() {
  cachedConfig = null;
  cachedConfigExpiry = 0;
}

router.get('/config', async (_req: Request, res: Response) => {
  res.setHeader('Cache-Control', 'public, max-age=60');

  const now = Date.now();
  if (cachedConfig && cachedConfigExpiry > now) {
    return res.json({
      success: true,
      data: cachedConfig
    });
  }

  try {
    let config = await prisma.appConfig.findUnique({
      where: { id: 'global_config' }
    });

    if (!config) {
      config = await prisma.appConfig.create({
        data: {
          id: 'global_config',
          minAppVersionCode: 1,
          latestAppVersionCode: 1,
          forceUpdate: false,
          maintenanceMode: false,
          announcementActive: false,
          announcementTitle: null,
          announcementBody: null,
          bannerAdsEnabled: true,
          interstitialAdsEnabled: true,
          interstitialIntervalSeconds: 120,
          rewardedAdsEnabled: true
        }
      });
    }

    const formattedData = {
      app: {
        minAppVersionCode: config.minAppVersionCode,
        latestAppVersionCode: config.latestAppVersionCode,
        forceUpdate: config.forceUpdate,
        maintenanceMode: config.maintenanceMode
      },
      announcement: {
        active: config.announcementActive,
        title: config.announcementTitle,
        body: config.announcementBody,
        dismissible: true
      },
      ads: {
        bannerEnabled: config.bannerAdsEnabled,
        nativeEnabled: config.nativeAdsEnabled,
        bannerAdUnitId: config.bannerAdUnitId,
        nativeAdUnitId: config.nativeAdUnitId,
        interstitialAdUnitId: config.interstitialAdUnitId,
        rewardedAdUnitId: config.rewardedAdUnitId,
        rewardedPassMinutes: config.rewardedPassMinutes,
        interstitialEnabled: config.interstitialAdsEnabled,
        interstitialIntervalSeconds: config.interstitialIntervalSeconds,
        rewardedEnabled: config.rewardedAdsEnabled
      }
    };

    cachedConfig = formattedData;
    cachedConfigExpiry = now + CACHE_TTL_MS;

    return res.json({
      success: true,
      data: formattedData
    });
  } catch (_err) {
    return res.status(500).json({
      success: false,
      error: 'DB_ERROR',
      message: 'Failed to retrieve configuration'
    });
  }
});

router.get('/legal/:type', (req: Request, res: Response) => {
  res.setHeader('Cache-Control', 'public, max-age=300'); // 5 minutes cache
  const type = req.params.type;

  if (type === 'privacy') {
    return res.json({
      success: true,
      data: {
        type: 'privacy',
        version: '1.1.0',
        contentMarkdown: `# Nsec VPN Privacy Information

### Operational records
The service persists a random installation identifier, device authentication records, account entitlements, purchase-token hashes, rewarded-ad verification records, and expiring WireGuard public-key/address leases. The node persists peer configuration and lease metadata to restore authorized peers after reboot. The installation identifier is not a hardware identifier.

### Tunnel keys and traffic
Client private keys remain on the device and are protected using Android Keystore encryption. Server and client public keys and assigned tunnel addresses are operational records, not RAM-only records. WireGuard keeps runtime endpoint, handshake and transfer-counter state in memory. This implementation does not persist originating client IPs, browsing activity, DNS queries or traffic content. Hosting and third-party provider settings require a separate operator review.

### Advertising and billing
Google Play processes payments. Google AdMob serves advertising and verifies rewarded-ad events. These providers have their own privacy policies. The VPN app is excluded from the tunnel so its advertising traffic uses the ordinary network connection.

### Retention and deletion
Expired peers are removed by lease cleanup; failed node cleanup is retried. Clearing app data removes local data and does not delete backend records. The operator must publish verified retention periods, a deletion contact and its identity before release.`
      }
    });
  }

  return res.json({
    success: true,
    data: {
      type: 'terms',
      version: '1.1.0',
      contentMarkdown: `# Nsec VPN Terms of Service

### 1. Acceptable Use
By utilizing the Nsec VPN service, you agree not to use our network infrastructure for:
- Denial of Service (DoS/DDoS) attacks or network interference.
- Distribution of malicious software, worms, or trojans.
- Unauthorized intrusion or port scanning against third-party systems.

### 2. Service Availability & Bandwidth
Nsec VPN provides high-speed encrypted tunnels on a best-effort basis. Eligible paid or rewarded entitlements permit access to nodes marked premium. Availability and throughput depend on the selected node and network conditions; no fixed bandwidth is guaranteed.`
    }
  });
});

export default router;
