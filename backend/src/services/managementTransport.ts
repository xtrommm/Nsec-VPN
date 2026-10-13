import { isIP } from 'net';

export function managementOriginAllowed(value: string): boolean {
  try {
    const u = new URL(value);
    if (u.username || u.password || u.search || u.hash || u.pathname !== '/') return false;
    if (u.protocol === 'https:') return true;
    if (u.protocol !== 'http:') return false;
    const host = u.hostname.replace(/^\[|\]$/g, '').toLowerCase();
    if (host === 'localhost' || host === '::1') return true;
    if (isIP(host) !== 4) return false;
    const parts = host.split('.').map(Number);
    return parts[0] === 127 || parts[0] === 10 ||
      (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) ||
      (parts[0] === 192 && parts[1] === 168);
  } catch { return false; }
}
