
export type SubscriptionState =
  | 'ACTIVE'
  | 'IN_GRACE_PERIOD'
  | 'ON_HOLD'
  | 'PAUSED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'REFUNDED';

export interface SubscriptionStatus {
  isValid: boolean;
  tier: 'FREE' | 'VIP';
  state: SubscriptionState;
  expiryTimeMs?: number;
  autoRenewing?: boolean;
}

export class PlayBillingService {

  private static readonly EXPECTED_PACKAGE = process.env.PLAY_PACKAGE_NAME || 'com.nsec.vpn';

  /**
   * Verifies an in-app subscription purchase with Google Play Developer API.
   * Handles ACTIVE, IN_GRACE_PERIOD, ON_HOLD, PAUSED, CANCELLED, EXPIRED, and REFUNDED states.
   */
  static async verifySubscription(
    packageName: string,
    productId: string,
    purchaseToken: string
  ): Promise<SubscriptionStatus> {
    // Validate package name to prevent arbitrary package inquiries
    if (packageName !== this.EXPECTED_PACKAGE) {
      throw new Error(`Unauthorized package name: ${packageName}`);
    }

    const serviceAccountKey = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;

    if (!serviceAccountKey) {
      throw new Error('GOOGLE_PLAY_SERVICE_ACCOUNT_JSON environment variable is not configured');
    }

    let parsedCredentials: any;
    try {
      parsedCredentials = JSON.parse(serviceAccountKey);
    } catch (_err) {
      throw new Error('Invalid JSON format in GOOGLE_PLAY_SERVICE_ACCOUNT_JSON');
    }

    const { google } = await import('googleapis');
    const auth = new google.auth.GoogleAuth({ credentials: parsedCredentials, scopes: ['https://www.googleapis.com/auth/androidpublisher'] });
    const publisher = google.androidpublisher({ version: 'v3', auth, timeout: 8000 });
    try {
      const response = await publisher.purchases.subscriptionsv2.get({ packageName, token: purchaseToken });
      const data = response.data;
      const item = data.lineItems?.find(line => line.productId === productId);
      if (!item?.expiryTime) return { isValid: false, tier: 'FREE', state: 'EXPIRED' };
      const expiryTimeMs = Date.parse(item.expiryTime);
      const state = data.subscriptionState;
      const allowed = ['SUBSCRIPTION_STATE_ACTIVE', 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD', 'SUBSCRIPTION_STATE_CANCELED'].includes(state || '');
      const valid = allowed && Number.isFinite(expiryTimeMs) && expiryTimeMs > Date.now();
      if (valid && data.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_PENDING') {
        await publisher.purchases.subscriptions.acknowledge({ packageName, subscriptionId: productId, token: purchaseToken });
      }
      const mapped: Record<string, SubscriptionState> = {
        SUBSCRIPTION_STATE_ACTIVE: 'ACTIVE', SUBSCRIPTION_STATE_IN_GRACE_PERIOD: 'IN_GRACE_PERIOD',
        SUBSCRIPTION_STATE_CANCELED: 'CANCELLED', SUBSCRIPTION_STATE_ON_HOLD: 'ON_HOLD',
        SUBSCRIPTION_STATE_PAUSED: 'PAUSED', SUBSCRIPTION_STATE_EXPIRED: 'EXPIRED'
      };
      return { isValid: valid, tier: valid ? 'VIP' : 'FREE', state: mapped[state || ''] || 'EXPIRED', expiryTimeMs,
        autoRenewing: item.autoRenewingPlan?.autoRenewEnabled === true };
    } catch (error) {
      if ((error as { code?: number }).code === 404) return { isValid: false, tier: 'FREE', state: 'REFUNDED' };
      throw new Error('Google Play verification unavailable');
    }
  }
}
