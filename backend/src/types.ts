import { z } from 'zod';

// ==========================================
// 1. Zod Input Validation Schemas
// ==========================================

export const RegisterDeviceSchema = z.object({
  deviceId: z.string().min(8).max(128).regex(/^[a-zA-Z0-9_-]+$/, 'Invalid deviceId characters'),
  deviceModel: z.string().max(64).optional(),
  osVersion: z.string().max(32).optional(),
  appVersion: z.string().max(32).optional(),
  appVersionCode: z.number().int().positive().optional()
});

export const RefreshTokenSchema = z.object({
  refreshToken: z.string().min(20)
});

export const ConnectVpnSchema = z.object({
  serverId: z.string().uuid().or(z.string().min(2).max(64)),
  clientPublicKey: z.string().length(44, 'WireGuard public keys must be 44-character base64 strings')
    .regex(/^[A-Za-z0-9+/]{43}=$/, 'Invalid Curve25519 public key format')
});

export const DisconnectVpnSchema = z.object({
  peerId: z.string().min(4).max(64),
  clientPublicKey: z.string().max(64).optional()
});

export const AdminLoginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8).max(128),
  totpCode: z.string().length(6).optional()
});

function isPrivateOrDocumentationIp(host: string): boolean {
  const hostLower = host.toLowerCase().trim();
  if (hostLower === 'localhost' || hostLower === '127.0.0.1' || hostLower === '::1' || hostLower === '0.0.0.0') {
    return true;
  }
  if (hostLower.startsWith('10.') || hostLower.startsWith('127.') || hostLower.startsWith('192.168.')) {
    return true;
  }
  if (hostLower.startsWith('172.')) {
    const parts = hostLower.split('.');
    if (parts.length >= 2) {
      const secondOctet = parseInt(parts[1], 10);
      if (secondOctet >= 16 && secondOctet <= 31) return true;
    }
  }
  if (hostLower.startsWith('192.0.2.') || hostLower.startsWith('198.51.100.') || hostLower.startsWith('203.0.113.')) {
    return true;
  }
  return false;
}

export const CreateServerSchema = z.object({
  name: z.string().min(2).max(64),
  countryCode: z.string().length(2).toUpperCase(),
  countryName: z.string().min(2).max(64),
  city: z.string().min(2).max(64),
  flagEmoji: z.string().min(1).max(8),
  endpoint: z.string().min(4).max(128).regex(/^([a-zA-Z0-9.-]+):(\d+)$/, 'Endpoint must be host:port')
    .refine((val) => {
      const host = val.substring(0, val.lastIndexOf(':'));
      return !isPrivateOrDocumentationIp(host);
    }, 'Private, loopback, or documentation endpoint IPs are strictly prohibited'),
  publicKey: z.string().length(44).regex(/^[A-Za-z0-9+/]{43}=$/, 'Invalid WireGuard public key'),
  subnetIpv4: z.string().default('10.8.0.0/24'),
  agentUrl: z.string().url('Agent URL must be valid HTTP/HTTPS endpoint'),
  agentSecret: z.string().min(16),
  isPremiumOnly: z.boolean().default(false),
  isRecommended: z.boolean().default(false),
  active: z.boolean().default(true)
});

export const UpdateServerSchema = CreateServerSchema.partial();

export const UpdateConfigSchema = z.object({
  minAppVersionCode: z.number().int().positive().optional(),
  latestAppVersionCode: z.number().int().positive().optional(),
  forceUpdate: z.boolean().optional(),
  maintenanceMode: z.boolean().optional(),
  announcementActive: z.boolean().optional(),
  announcementTitle: z.string().max(128).nullable().optional(),
  announcementBody: z.string().max(512).nullable().optional(),
  bannerAdsEnabled: z.boolean().optional(),
  interstitialAdsEnabled: z.boolean().optional(),
  interstitialIntervalSeconds: z.number().int().min(15).max(1800).optional(),
  rewardedAdsEnabled: z.boolean().optional()
});

export const VerifySubscriptionSchema = z.object({
  packageName: z.string().min(3),
  productId: z.string().min(3),
  purchaseToken: z.string().min(10)
});

// ==========================================
// 2. TypeScript Interfaces
// ==========================================

export interface JwtDevicePayload {
  deviceId: string;
  tier: 'FREE' | 'VIP' | 'REWARDED_PASS';
  iat?: number;
  exp?: number;
}

export interface JwtAdminPayload {
  adminId: string;
  email: string;
  role: 'SUPER_ADMIN' | 'ADMIN';
  iat?: number;
  exp?: number;
}
