import { PlayBillingService } from '../services/billing';

describe('PlayBillingService Verification', () => {
  it('identifies mock active VIP tokens in test mode', async () => {
    const status = await PlayBillingService.verifySubscription(
      'com.nsec.vpn',
      'nsecvpn_premium_monthly',
      'test_mock_active_token'
    );

    expect(status.isValid).toBe(true);
    expect(status.tier).toBe('VIP');
    expect(status.state).toBe('ACTIVE');
    expect(status.expiryTimeMs).toBeGreaterThan(Date.now());
  });

  it('identifies mock expired tokens in test mode', async () => {
    const status = await PlayBillingService.verifySubscription(
      'com.nsec.vpn',
      'nsecvpn_premium_monthly',
      'test_expired_token'
    );

    expect(status.isValid).toBe(false);
    expect(status.tier).toBe('FREE');
    expect(status.state).toBe('EXPIRED');
    expect(status.expiryTimeMs).toBeLessThan(Date.now());
  });
});
