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
        forceUpdate: config.forceUpdate
      },
      announcement: {
        active: config.announcementActive,
        title: config.announcementTitle,
        body: config.announcementBody,
        dismissible: true
      },
      ads: {
        bannerEnabled: config.bannerAdsEnabled,
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
        version: '1.0.0',
        contentMarkdown: `# Nsec VPN Zero-Logs Privacy Policy

**Effective Date:** October 2026
**Website:** https://nsecvpn.com
**Operator:** Nsec Security Systems

### 1. Absolute Zero-Logs Commitment
Nsec VPN does not track, store, or log any browsing activity, DNS queries, traffic content, origin IP addresses, or destination metadata. We have engineered our WireGuard® infrastructure from the kernel layer up to operate with volatile ephemeral state.

### 2. Anonymous Hardware Authentication
Nsec VPN does not collect emails, passwords, names, or phone numbers. Client authorization relies strictly on an anonymous cryptographic UUID generated inside the Android hardware Keystore.

### 3. WireGuard Cryptographic Key Exchange
WireGuard peer Curve25519 public keys and dynamic private IP addresses (e.g. 10.8.0.x) exist only in volatile server kernel RAM for the duration of an active connection and are permanently removed upon disconnection.

### 4. Third-Party Services
Subscription processing is handled directly by Google Play In-App Billing. Nsec VPN servers never see or store your credit card or financial details. Optional advertising is served through Google AdMob.`
      }
    });
  }

  return res.json({
    success: true,
    data: {
      type: 'terms',
      version: '1.0.0',
      contentMarkdown: `# Nsec VPN Terms of Service

**Effective Date:** October 2026
**Website:** https://nsecvpn.com

### 1. Acceptable Use
By utilizing the Nsec VPN service, you agree not to use our network infrastructure for:
- Denial of Service (DoS/DDoS) attacks or network interference.
- Distribution of malicious software, worms, or trojans.
- Unauthorized intrusion or port scanning against third-party systems.

### 2. Service Availability & Bandwidth
Nsec VPN provides high-speed encrypted tunnels on a best-effort basis. VIP subscriptions unlock unrestricted 10Gbps high-capacity nodes. Free tier users receive access to standard community nodes.`
    }
  });
});

export default router;
