import { Router, Request, Response } from 'express';
import { prisma } from '../db';

const router = Router();

// In-memory cache for high-throughput config queries
let cachedConfig: any = null;
let cachedConfigExpiry = 0;
let cacheGeneration = 0;
let inFlight: { generation: number; promise: Promise<unknown> } | null = null;
const CACHE_TTL_MS = 5 * 1000; // 5 seconds

export function invalidateConfigCache() {
  cacheGeneration++;
  cachedConfig = null;
  cachedConfigExpiry = 0;
  inFlight = null;
}

router.get('/config', async (_req: Request, res: Response) => {
  res.setHeader('Cache-Control', 'no-store');

  const now = Date.now();
  if (cachedConfig && cachedConfigExpiry > now) {
    return res.json({
      success: true,
      data: cachedConfig
    });
  }

  try {
    const generation = cacheGeneration;
    let request = inFlight;
    if (!request || request.generation !== generation) {
      const promise = (async () => {
    const config = await prisma.appConfig.upsert({ where: { id: 'global_config' }, create: { id: 'global_config' }, update: {} });

    const formattedData = {
      updatedAt: config.updatedAt,
      support: { email: config.supportEmail },
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
        enabled: config.adsEnabled,
        bannerEnabled: config.adsEnabled && config.bannerAdsEnabled,
        nativeEnabled: config.adsEnabled && config.nativeAdsEnabled,
        bannerAdUnitId: config.bannerAdUnitId,
        nativeAdUnitId: config.nativeAdUnitId,
        interstitialAdUnitId: config.interstitialAdUnitId,
        rewardedAdUnitId: config.rewardedAdUnitId,
        rewardedPassMinutes: config.rewardedPassMinutes,
        interstitialEnabled: config.adsEnabled && config.interstitialAdsEnabled,
        interstitialIntervalSeconds: config.interstitialIntervalSeconds,
        rewardedEnabled: config.adsEnabled && config.rewardedAdsEnabled
      }
    };

    if (generation === cacheGeneration) {
      cachedConfig = formattedData;
      cachedConfigExpiry = Date.now() + CACHE_TTL_MS;
    }
    return formattedData;

      })();
      request = { generation, promise };
      inFlight = request;
      void promise.finally(() => { if (inFlight === request) inFlight = null; }).catch(() => undefined);
    }
    const formattedData = await request.promise;

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
  if (!['privacy', 'terms'].includes(type)) return res.status(404).json({ success: false, error: 'LEGAL_DOCUMENT_NOT_FOUND' });

  if (type === 'privacy') {
    return res.json({
      success: true,
      data: {
        type: 'privacy',
        version: '1.2.0',
        contentMarkdown: `# Nsec VPN Privacy Information

### Operational records
The service persists a random installation identifier, device authentication records, account entitlements, purchase-token hashes and encrypted Google Play receipts used for subscription/refund verification, rewarded-ad verification records, and expiring WireGuard public-key/address leases. The node persists peer configuration and lease metadata to restore authorized peers after reboot. The installation identifier is not a hardware identifier.

### Tunnel keys and traffic
Client private keys remain on the device and are protected using Android Keystore encryption. Server and client public keys and assigned tunnel addresses are operational records, not RAM-only records. WireGuard keeps runtime endpoint, handshake and transfer-counter state in memory. This implementation does not persist originating client IPs, browsing activity, DNS queries or traffic content. Hosting and third-party provider settings require a separate operator review.

### Advertising and billing
Google Play processes payments. Google AdMob serves advertising and verifies rewarded-ad events. These providers have their own privacy policies. The VPN app is excluded from the tunnel so its advertising traffic uses the ordinary network connection.

### Optional device recovery
Users may configure a private recovery PIN while authenticated. The server stores a salted PIN hash and failed-attempt/lock metadata, never the raw PIN. The existing installation identifier plus the PIN can restore the same entitlement record after clearing local data, without extending its expiry. Recovery rotates device authentication and expires old peer leases for cleanup. Device ID alone cannot authorize recovery.

### Retention and deletion
Expired peers are removed by lease cleanup; failed node cleanup is retried. Clearing app data removes local data and does not delete backend records. Eligible inactive free device records, completed/rejected server requests and administrator audit records are cleaned after the operator-selected retention period (90 days by default). Expired credentials and unclaimed reward tickets are cleaned after that period following expiry. Active VIP, recovery credentials, replay/abuse protections and uncertain peer reservations are retained as needed for their purpose. Encrypted receipts are removed after expiry and the retention period, while minimal receipt hashes/ownership records remain to prevent reuse. The operator must publish its configured periods, deletion contact and identity before release.`
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
