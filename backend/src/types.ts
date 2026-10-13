import { z } from 'zod';

// ==========================================
// 1. Zod Input Validation Schemas
// ==========================================

export const DeviceIdSchema = z.string().min(8).max(128).regex(/^[a-zA-Z0-9_-]+$/, 'Invalid deviceId characters');
export const RegisterDeviceSchema = z.object({
  deviceId: DeviceIdSchema,
  registrationSecret: z.string().regex(/^[a-f0-9]{64}$/).optional(),
  registrationProof: z.string().max(2048).optional(),
  deviceModel: z.string().max(64).optional(),
  osVersion: z.string().max(32).optional(),
  appVersion: z.string().max(32).optional(),
  appVersionCode: z.number().int().positive().optional()
});

export const RefreshTokenSchema = z.object({
  refreshToken: z.string().min(20)
});

export const ConnectVpnSchema = z.object({
  locationSelection: z.boolean().optional(),
  excludedNodeIds: z.array(z.string().min(2).max(64)).max(1000).optional(),
  nodeLatencies: z.array(z.object({ nodeId: z.string().min(2).max(64), pingMs: z.number().int().min(0).max(60000) })).max(1000).optional(),
  serverId: z.string().uuid().or(z.string().min(2).max(64)),
  clientPublicKey: z.string().regex(/^[A-Za-z0-9+/]{43}=$/).refine(v => Buffer.from(v, 'base64').toString('base64') === v && Buffer.from(v, 'base64').some(x => x !== 0))
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

export function isPublicIpv4(host: string): boolean {
  const octets = host.split('.').map(Number);
  if (!/^\d+\.\d+\.\d+\.\d+$/.test(host) || octets.some(x => !Number.isInteger(x) || x < 0 || x > 255)) return false;
  const [a, b, c] = octets;
  return !(a === 0 || a === 10 || a === 127 || a >= 224 ||
    (a === 100 && b >= 64 && b <= 127) || (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) || (a === 192 && (b === 168 || (b === 0 && (c === 0 || c === 2)))) ||
    (a === 198 && (b === 18 || b === 19 || (b === 51 && c === 100))) || (a === 203 && b === 0 && c === 113));
}
export function isPublicHost(host: string): boolean {
  if (/^[\d.]+$/.test(host)) return isPublicIpv4(host);
  if (/(^|\.)example\.(com|org|net)$/i.test(host)) return false;
  return host.length <= 253 && host.includes('.') && host.split('.').every(label =>
    /^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/.test(label)) &&
    !/\.(localhost|local|internal|test|invalid|example)$/i.test(host);
}
export const PublicKeySchema = z.string().regex(/^[A-Za-z0-9+/]{43}=$/).refine(value => {
  const bytes = Buffer.from(value, 'base64');
  return bytes.length === 32 && bytes.toString('base64') === value && bytes.some(x => x !== 0);
}, 'Invalid canonical 32-byte WireGuard key');
export const EndpointSchema = z.string().max(260).refine(value => {
  const match = /^([^:]+):(\d+)$/.exec(value);
  return !!match && isPublicHost(match[1]) && Number(match[2]) >= 1 && Number(match[2]) <= 65535 && Number(match[2]) !== 51821;
}, 'Endpoint must be a public IPv4 or DNS host and WireGuard port (not agent port 51821)');
export const SubnetSchema = z.string().refine(value => {
  const match = /^(\d+)\.(\d+)\.(\d+)\.0\/24$/.exec(value);
  return !!match && match.slice(1).every(o => Number(o) <= 255) &&
    (Number(match[1]) === 10 || (Number(match[1]) === 172 && Number(match[2]) >= 16 && Number(match[2]) <= 31) ||
    (Number(match[1]) === 192 && Number(match[2]) === 168));
}, 'A private network-aligned IPv4 /24 is required');
export const DnsSchema = z.array(z.string().ip({ version: 'v4' }).refine(isPublicIpv4, 'A real public DNS resolver is required; private resolvers require a verified resolver deployment')).min(1).max(4);
export const CreateServerSchema = z.object({
  name: z.string().trim().min(2).max(64), countryCode: z.string().trim().toUpperCase().regex(/^[A-Z]{2}$/),
  countryName: z.string().trim().min(2).max(64), city: z.string().trim().min(2).max(64), flagEmoji: z.string().min(1).max(8),
  endpoint: EndpointSchema, publicKey: PublicKeySchema, subnetIpv4: SubnetSchema.default('10.8.0.0/24'),
  dnsServers: DnsSchema.default(['1.1.1.1', '1.0.0.1']), mtu: z.number().int().min(1280).max(1420).default(1420),
  maxPeers: z.number().int().min(1).max(253).default(250),
  agentUrl: z.string().url().refine(value => {
    const u = new URL(value);
    return ['http:', 'https:'].includes(u.protocol) && !u.username && !u.password && !u.search && !u.hash && (u.pathname === '/' || u.pathname === '');
  }, 'Agent URL must be HTTP(S) origin without credentials'),
  agentSecret: z.string().regex(/^env:[A-Z][A-Z0-9_]{2,95}$/, 'Use a backend environment credential reference, not a raw secret'), isPremiumOnly: z.boolean().default(false),
  isRecommended: z.boolean().default(false), active: z.boolean().default(true)
}).strict();
export const UpdateServerSchema = CreateServerSchema.partial();

export const AdUnitSchema = z.string().regex(/^ca-app-pub-[0-9]{16}\/[0-9]{10}$/, "Enter a valid AdMob ad unit ID").refine(value => !value.startsWith("ca-app-pub-3940256099942544/"), "Use your production ad unit, not Google sample IDs").nullable();

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
  rewardedAdsEnabled: z.boolean().optional(),
  nativeAdsEnabled: z.boolean().optional(),
  bannerAdUnitId: AdUnitSchema.optional(),
  nativeAdUnitId: AdUnitSchema.optional(),
  interstitialAdUnitId: AdUnitSchema.optional(),
  rewardedAdUnitId: AdUnitSchema.optional(),
  rewardedPassMinutes: z.number().int().min(1).max(1440).optional(),
  adsEnabled: z.boolean().optional(),
  supportEmail: z.string().trim().email().max(254).nullable().optional()
}).strict();

export const VerifySubscriptionSchema = z.object({
  packageName: z.string().min(3),
  productId: z.string().min(3),
  purchaseToken: z.string().min(10)
});

// ==========================================
// 2. TypeScript Interfaces
// ==========================================

export interface JwtDevicePayload {
  authEpoch?: number;
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
