import crypto from 'crypto';
import { verifyRewardQuery } from '../services/rewardVerification';
it('verifies signed raw query without reordering or accepting tampered data', () => {
  const pair = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const query = 'ad_unit=123&timestamp=1000&transaction_id=test-transaction&user_id=test-device';
  const signature = crypto.sign('sha256', Buffer.from(query), pair.privateKey).toString('base64url');
  expect(verifyRewardQuery(`${query}&signature=${signature}&key_id=1`, pair.publicKey).get('user_id')).toBe('test-device');
  expect(() => verifyRewardQuery(`${query.replace('test-device','another-device')}&signature=${signature}&key_id=1`, pair.publicKey)).toThrow();
});
