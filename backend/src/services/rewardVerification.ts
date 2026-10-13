import crypto from 'crypto';
import { z } from 'zod';
import { prisma } from '../db';
let cachedKeys: Map<string, crypto.KeyObject> = new Map();
let cachedAt = 0;
async function publicKeys() {
  if (cachedKeys.size && Date.now() - cachedAt < 3600000) return cachedKeys;
  try {
  const response = await fetch('https://www.gstatic.com/admob/reward/verifier-keys.json', { signal: AbortSignal.timeout(4000) });
  if (!response.ok) throw new Error('SSV_KEYS_UNAVAILABLE');
  const data = z.object({ keys: z.array(z.object({ keyId: z.number(), base64: z.string() })) }).parse(await response.json());
  cachedKeys = new Map(data.keys.map(k => [String(k.keyId), crypto.createPublicKey({ key: Buffer.from(k.base64, 'base64'), format: 'der', type: 'spki' })]));
  cachedAt = Date.now(); return cachedKeys;
  } catch { throw new Error('SSV_KEYS_UNAVAILABLE'); }

}
export function verifyRewardQuery(query: string, key: crypto.KeyObject): URLSearchParams {
  const match = /^(.*)&signature=([^&]+)&key_id=(\d+)$/.exec(query);
  if (!match || !crypto.verify('sha256', Buffer.from(match[1]), key, Buffer.from(decodeURIComponent(match[2]), 'base64url'))) throw new Error('SSV_SIGNATURE_INVALID');
  const params = new URLSearchParams(match[1]);
  const names = new Set<string>();
  for (const [name] of params) { if (names.has(name)) throw new Error('SSV_DUPLICATE_PARAMETER'); names.add(name); }
  return params;
}
export type VerifiedReward = { deviceId: string; transactionId: string; timestamp: number; ticketId: string | null };
export async function verifyReward(query: string): Promise<VerifiedReward> {
  const keyId = /&key_id=(\d+)$/.exec(query)?.[1];
  let key = (await publicKeys()).get(keyId || '');
  if (!key) { cachedAt = 0; key = (await publicKeys()).get(keyId || ''); }
  if (!key) throw new Error('SSV_KEY_UNKNOWN');
  const params = verifyRewardQuery(query, key);
  const config = await prisma.appConfig.findUnique({ where: { id: 'global_config' } });
  const configured = config?.rewardedAdUnitId || process.env.ADMOB_REWARDED_UNIT_ID;
  if (!configured) throw new Error('SSV_NOT_CONFIGURED');
  if (![configured, configured.split('/').pop()].includes(params.get('ad_unit') || '')) throw new Error('SSV_AD_UNIT_INVALID');
  const deviceId = z.string().min(8).max(128).parse(params.get('user_id'));
  const transactionId = z.string().min(8).max(256).parse(params.get('transaction_id'));
  const timestamp = Number(params.get('timestamp'));
  if (!Number.isFinite(timestamp) || Math.abs(Date.now() - timestamp) > 86400000) throw new Error('SSV_EXPIRED');
  return { deviceId, transactionId, timestamp, ticketId: params.get('custom_data') };
}
