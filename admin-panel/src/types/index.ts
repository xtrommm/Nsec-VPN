export interface ServerNode {
  id: string;
  name: string;
  countryCode: string;
  countryName: string;
  city: string;
  flagEmoji: string;
  endpoint: string;
  publicKey: string;
  agentUrl: string;
  agentSecret?: string;
  subnetIpv4?: string;
  dnsServers?: string[];
  mtu?: number;
  maxPeers?: number;
  publicIpv4?: string | null;
  healthy?: boolean;
  healthCheckedAt?: string | null;
  sortOrder?: number;
  currentLoad: number;
  isPremiumOnly: boolean;
  isRecommended: boolean;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface DeviceUser {
  id: string;
  deviceId: string;
  deviceModel: string | null;
  osVersion: string | null;
  appVersion: string | null;
  tier: 'FREE' | 'VIP' | 'REWARDED_PASS';
  passExpiresAt: string | null;
  isBlocked: boolean;
  createdAt: string;
}

export interface StatsData {
  activeTunnels: number;
  totalDevices: number;
  vipSubscribers: number;
  totalServers: number;
  avgServerLoad: number;
  topCountries?: { countryCode: string; count: number }[];
}

export interface AppConfigData {
  id?: string;
  minAppVersionCode: number;
  latestAppVersionCode: number;
  forceUpdate: boolean;
  maintenanceMode: boolean;
  announcementActive: boolean;
  announcementTitle: string | null;
  announcementBody: string | null;
  bannerAdsEnabled: boolean;
  interstitialAdsEnabled: boolean;
  interstitialIntervalSeconds: number;
  rewardedAdsEnabled: boolean;
  nativeAdsEnabled?: boolean;
  bannerAdUnitId?: string | null;
  nativeAdUnitId?: string | null;
  interstitialAdUnitId?: string | null;
  rewardedAdUnitId?: string | null;
  rewardedPassMinutes?: number;
  adsEnabled?: boolean;
  supportEmail?: string | null;
  updatedAt?: string;
}

export interface AuditLogEntry {
  id: string;
  adminId: string;
  action: string;
  targetResource: string;
  targetId: string | null;
  metadata: string | null;
  createdAt: string;
  admin?: { email: string; role: string };
}

export interface ToastMessage {
  id: string;
  type: 'success' | 'error' | 'info' | 'warning';
  title?: string;
  message: string;
}
