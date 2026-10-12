import crypto from 'crypto';
export function verifyConfiguredTotp(reference: string | null, code: string, time = Date.now()): boolean {
  if (!reference || !/^env:[A-Z][A-Z0-9_]{2,95}$/.test(reference)) return false;
  const secret = process.env[reference.slice(4)];
  return !!secret && secret.length >= 16 && verifyTotp(secret, code, time);
}
export function verifyTotp(secret: string, code: string, time = Date.now()): boolean {
  if (!/^\d{6}$/.test(code) || !/^[A-Z2-7]+=*$/.test(secret.toUpperCase())) return false;
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bits = secret.toUpperCase().replace(/=/g, '').split('').map(x => alphabet.indexOf(x).toString(2).padStart(5, '0')).join('');
  const bytes = Buffer.from(bits.match(/.{8}/g)?.map(x => parseInt(x, 2)) || []);
  for (let delta = -1; delta <= 1; delta++) {
    const counter = Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(Math.floor(time / 30000) + delta));
    const hmac = crypto.createHmac('sha1', bytes).update(counter).digest();
    const offset = hmac[hmac.length - 1] & 15;
    const expected = ((hmac.readUInt32BE(offset) & 0x7fffffff) % 1000000).toString().padStart(6, '0');
    if (crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(code))) return true;
  }
  return false;
}
