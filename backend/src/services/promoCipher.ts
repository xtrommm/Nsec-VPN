import crypto from 'crypto';

function key(): Buffer {
  const value = process.env.PROMO_CODE_ENCRYPTION_KEY;
  if (!value || !/^[a-f0-9]{64}$/i.test(value)) throw new Error('PROMO_ENCRYPTION_NOT_CONFIGURED');
  return Buffer.from(value, 'hex');
}

export function encryptPromo(code: string): string {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key(), iv);
  cipher.setAAD(Buffer.from('nsec-promo-v1'));
  const data = Buffer.concat([cipher.update(code, 'utf8'), cipher.final()]);
  return ['v1', iv.toString('base64'), cipher.getAuthTag().toString('base64'), data.toString('base64')].join('.');
}

export function decryptPromo(value: string): string {
  const [version, iv, tag, data, extra] = value.split('.');
  if (version !== 'v1' || !iv || !tag || !data || extra) throw new Error('PROMO_CODE_UNAVAILABLE');
  try {
    const cipher = crypto.createDecipheriv('aes-256-gcm', key(), Buffer.from(iv, 'base64'));
    cipher.setAAD(Buffer.from('nsec-promo-v1'));
    cipher.setAuthTag(Buffer.from(tag, 'base64'));
    return Buffer.concat([cipher.update(Buffer.from(data, 'base64')), cipher.final()]).toString('utf8');
  } catch { throw new Error('PROMO_CODE_UNAVAILABLE'); }
}
