import { PlayBillingService } from '../services/billing';
describe('Billing fails closed', () => {
  it('rejects invented tokens without a Google credential even in test mode', async () => {
    const previous = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    delete process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    try { await expect(PlayBillingService.verifySubscription('com.nsec.vpn', 'premium', 'play_token_test')).rejects.toThrow('not configured'); }
    finally { if (previous !== undefined) process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON = previous; }
  });
});
