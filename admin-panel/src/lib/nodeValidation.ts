export function validateNode(value: { endpoint: string; publicKey: string; subnetIpv4: string; dnsServers: string[]; agentUrl: string }): string[] {
  const errors: string[] = [];
  const match = /^([^:]+):(\d+)$/.exec(value.endpoint);
  const isPublic = (host: string) => {
    if (!/^[\d.]+$/.test(host)) return !/(^|\.)example\.(com|org|net)$/i.test(host) && host.includes('.') && host.split('.').every(l => /^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/.test(l)) && !/\.(local|internal|invalid|test|example)$/.test(host);
    const a = host.split('.').map(Number);
    return a.length === 4 && a.every(n => n >= 0 && n <= 255) && !(a[0] === 0 || a[0] === 10 || a[0] === 127 || a[0] >= 224 ||
      (a[0] === 172 && a[1] >= 16 && a[1] <= 31) || (a[0] === 192 && (a[1] === 168 || a[1] === 0)) ||
      (a[0] === 169 && a[1] === 254) || (a[0] === 100 && a[1] >= 64 && a[1] <= 127) ||
      (a[0] === 198 && (a[1] === 18 || a[1] === 19 || (a[1] === 51 && a[2] === 100))) || (a[0] === 203 && a[1] === 0 && a[2] === 113));
  };
  if (!match || !isPublic(match[1]) || Number(match[2]) < 1 || Number(match[2]) > 65535 || Number(match[2]) === 51821) errors.push('Use a public IPv4 or DNS endpoint with the WireGuard UDP port, never agent port 51821.');
  try { const bytes = atob(value.publicKey); if (bytes.length !== 32 || btoa(bytes) !== value.publicKey || !bytes.split('').some(c => c.charCodeAt(0) !== 0)) errors.push('Invalid 32-byte public key.'); }
  catch { errors.push('Invalid Base64 public key.'); }
  const subnet = /^(\d+)\.(\d+)\.(\d+)\.0\/24$/.exec(value.subnetIpv4);
  if (!subnet || subnet.slice(1).some(n => Number(n) > 255) || !(Number(subnet[1]) === 10 || (Number(subnet[1]) === 172 && Number(subnet[2]) >= 16 && Number(subnet[2]) <= 31) || (Number(subnet[1]) === 192 && Number(subnet[2]) === 168))) errors.push('Use a network-aligned private IPv4 /24 subnet.');
  if (!value.dnsServers.length || value.dnsServers.some(ip => !/^\d+\.\d+\.\d+\.\d+$/.test(ip) || ip.split('.').some(n => Number(n) > 255) || !isPublic(ip))) errors.push('Enter valid DNS IPv4 addresses.');
  try {
    const url = new URL(value.agentUrl);
    const host = url.hostname.replace(/^\[|\]$/g, '').toLowerCase();
    const parts = host.split('.').map(Number);
    const privateIp = /^\d+\.\d+\.\d+\.\d+$/.test(host) && parts.every(n => n >= 0 && n <= 255) &&
      (parts[0] === 127 || parts[0] === 10 || (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) || (parts[0] === 192 && parts[1] === 168));
    const secure = url.protocol === 'https:' || (url.protocol === 'http:' && (privateIp || host === 'localhost' || host === '::1'));
    if (!secure || url.username || url.password || url.search || url.hash || url.pathname !== '/')
      errors.push('Use HTTPS or a private/loopback management origin through an encrypted channel.');
  }
  catch { errors.push('Invalid agent URL.'); }
  return errors;
}
