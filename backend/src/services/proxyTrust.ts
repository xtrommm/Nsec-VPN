import { isIP } from 'net';

/** Trust only operator-specified proxy addresses, never an arbitrary forwarding header or hop count. */
export function trustedProxyCidrs(value = ''): string[] {
  if (!value.trim()) return [];
  const entries = [...new Set(value.split(',').map(entry => entry.trim()))];
  if (entries.length > 64 || entries.some(entry => {
    const parts = entry.split('/');
    const family = isIP(parts[0]);
    if (!family || parts.length !== 2 || !/^\d+$/.test(parts[1])) return true;
    const prefix = Number(parts[1]);
    return prefix < 1 || prefix > (family === 4 ? 32 : 128);
  })) throw new Error('TRUSTED_PROXY_CIDRS requires explicit IPv4/IPv6 CIDRs with nonzero prefixes');
  return entries;
}
