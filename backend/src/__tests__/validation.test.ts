import {
  RegisterDeviceSchema,
  ConnectVpnSchema,
  DisconnectVpnSchema,
  AdminLoginSchema,
  CreateServerSchema
} from '../types';

describe('Validation Schemas (Zod)', () => {
  describe('RegisterDeviceSchema', () => {
    it('accepts valid device registration payloads', () => {
      const valid = {
        deviceId: 'device_abc123_xyz-99',
        deviceModel: 'Pixel 8 Pro',
        osVersion: 'Android 15',
        appVersion: '1.0.0',
        appVersionCode: 1
      };
      expect(() => RegisterDeviceSchema.parse(valid)).not.toThrow();
    });

    it('rejects invalid deviceId containing special characters', () => {
      const invalid = {
        deviceId: 'invalid$device!id'
      };
      expect(() => RegisterDeviceSchema.parse(invalid)).toThrow();
    });
  });

  describe('ConnectVpnSchema', () => {
    it('accepts valid WireGuard public keys (44-char base64)', () => {
      const valid = {
        serverId: '123e4567-e89b-12d3-a456-426614174000',
        clientPublicKey: '8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M='
      };
      expect(() => ConnectVpnSchema.parse(valid)).not.toThrow();
    });

    it('rejects invalid length or malformed public keys', () => {
      const invalid = {
        serverId: 'server1',
        clientPublicKey: 'invalid_key'
      };
      expect(() => ConnectVpnSchema.parse(invalid)).toThrow();
    });
  });

  describe('DisconnectVpnSchema', () => {
    it('validates peerId parameter', () => {
      const valid = {
        peerId: 'peer_1234567890abcdef'
      };
      expect(() => DisconnectVpnSchema.parse(valid)).not.toThrow();
    });
  });

  describe('AdminLoginSchema', () => {
    it('requires valid email format and password', () => {
      expect(() =>
        AdminLoginSchema.parse({ email: 'admin@nsecvpn.com', password: 'ValidPassword123' })
      ).not.toThrow();

      expect(() =>
        AdminLoginSchema.parse({ email: 'not-an-email', password: 'short' })
      ).toThrow();
    });
  });

  describe('CreateServerSchema', () => {
    it('validates server endpoint format host:port', () => {
      const valid = {
        name: 'US East',
        countryCode: 'US',
        countryName: 'United States',
        city: 'New York',
        flagEmoji: '🇺🇸',
        endpoint: 'us.nsecvpn.com:51820',
        publicKey: '8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M=',
        subnetIpv4: '10.8.0.0/24',
        agentUrl: 'http://198.51.100.24:51821',
        agentSecret: 'env:NSEC_TEST_AGENT_KEY',
        isPremiumOnly: false,
        isRecommended: true,
        active: true
      };
      expect(() => CreateServerSchema.parse(valid)).not.toThrow();
    });

    it('rejects malformed endpoint missing port', () => {
      const invalid = {
        name: 'US East',
        countryCode: 'US',
        countryName: 'United States',
        city: 'New York',
        flagEmoji: '🇺🇸',
        endpoint: 'us.nsecvpn.com', // missing port
        publicKey: '8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M=',
        agentUrl: 'http://198.51.100.24:51821',
        agentSecret: 'env:NSEC_TEST_AGENT_KEY'
      };
      expect(() => CreateServerSchema.parse(invalid)).toThrow();
    });
  });
});
