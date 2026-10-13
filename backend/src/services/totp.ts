import crypto from 'crypto';
const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
export function encodeTotpSeed(bytes: Buffer): string {
  let bits = '', output = '';
  for (const byte of bytes) bits += byte.toString(2).padStart(8, '0');
  for (let i = 0; i < bits.length; i += 5) output += alphabet[parseInt(bits.slice(i, i + 5).padEnd(5, '0'), 2)];
  return output;
}
function encryptionKey(): Buffer {
  const value = process.env.ADMIN_TOTP_ENCRYPTION_KEY;
  if (!value || !/^[a-f0-9]{64}$/i.test(value)) throw new Error('TOTP_ENCRYPTION_NOT_CONFIGURED');
  return Buffer.from(value, 'hex');
}
export function encryptTotpSeed(secret: string, expiresAt: number): string {
  const iv = crypto.randomBytes(12), cipher = crypto.createCipheriv('aes-256-gcm', encryptionKey(), iv);
  cipher.setAAD(Buffer.from('nsec-admin-totp-v1'));
  const body = Buffer.concat([cipher.update(JSON.stringify({ secret, expiresAt }), 'utf8'), cipher.final()]);
  return 'enc:v1:' + Buffer.concat([iv, cipher.getAuthTag(), body]).toString('base64');
}
export function readTotpSeed(reference: string | null): { secret: string; expiresAt?: number } | null {
  if (!reference) return null;
  if (/^env:[A-Z][A-Z0-9_]{2,95}$/.test(reference)) {
    const secret = process.env[reference.slice(4)];
    return secret && secret.length >= 16 ? { secret } : null;
  }
  if (!reference.startsWith('enc:v1:')) return null;
  const bytes = Buffer.from(reference.slice(7), 'base64');
  if (bytes.length < 29) return null;
  const decipher = crypto.createDecipheriv('aes-256-gcm', encryptionKey(), bytes.subarray(0, 12));
  decipher.setAAD(Buffer.from('nsec-admin-totp-v1')); decipher.setAuthTag(bytes.subarray(12, 28));
  const value: unknown = JSON.parse(Buffer.concat([decipher.update(bytes.subarray(28)), decipher.final()]).toString('utf8'));
  if (!value || typeof value !== 'object' || !('secret' in value) || typeof value.secret !== 'string' ||
      !('expiresAt' in value) || typeof value.expiresAt !== 'number' || !Number.isFinite(value.expiresAt)) return null;
  return { secret: value.secret, expiresAt: value.expiresAt };
}
export const totpVersion = (reference: string | null) => { const seed = readTotpSeed(reference); return seed ? crypto.createHash('sha256').update(seed.secret.toUpperCase()).digest('hex') : null; };
export function verifyConfiguredTotp(reference: string | null, code: string, time = Date.now()): boolean {
  try { const seed = readTotpSeed(reference); return !!seed && verifyTotp(seed.secret, code, time); }
  catch { return false; }
}
export function totpStep(secret: string, code: string, time = Date.now()): number | null {
  if (!/^\d{6}$/.test(code) || !/^[A-Z2-7]+=*$/.test(secret.toUpperCase()) || !Number.isFinite(time)) return null;
  const bits = secret.toUpperCase().replace(/=/g, '').split('').map(x => alphabet.indexOf(x).toString(2).padStart(5, '0')).join('');
  const bytes = Buffer.from(bits.match(/.{8}/g)?.map(x => parseInt(x, 2)) || []);
  if (bytes.length < 10) return null;
  for (const delta of [0, -1, 1]) {
    const step = Math.floor(time / 30000) + delta;
    if (!Number.isSafeInteger(step) || step < 0 || step > 2147483647) continue;
    const counter = Buffer.alloc(8); counter.writeBigUInt64BE(BigInt(step));
    const hmac = crypto.createHmac('sha1', bytes).update(counter).digest();
    const offset = hmac[hmac.length - 1] & 15;
    const expected = ((hmac.readUInt32BE(offset) & 0x7fffffff) % 1000000).toString().padStart(6, '0');
    if (crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(code))) return step;
  }
  return null;
}
export function verifyTotp(secret: string, code: string, time = Date.now()): boolean { return totpStep(secret, code, time) !== null; }
