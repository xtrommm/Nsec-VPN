import { EndpointSchema, PublicKeySchema, SubnetSchema, isPublicIpv4 } from '../types';
import { AgentClient } from '../services/agentClient';
import crypto from 'crypto';
import { verifyTotp, verifyConfiguredTotp } from '../services/totp';
process.env.NSEC_TEST_AGENT_KEY = 'x'.repeat(32);
const key = Buffer.alloc(32, 7).toString('base64');
describe('Node input regression cases', () => {
  test.each(['172.30.148.192:51820', '10.8.0.1:51820', '127.0.0.1:51820', '192.168.1.1:51820',
    '203.0.113.2:51820', '198.51.100.2:51820', '192.0.2.2:51820', '47.85.109.170:51821',
    '47.85.109.170:0', '47.85.109.170:65536', '999.1.1.1:51820', 'localhost:51820'])('rejects %s', value => {
    expect(EndpointSchema.safeParse(value).success).toBe(false);
  });
  it('accepts the real node endpoint', () => expect(EndpointSchema.parse('47.85.109.170:51820')).toBeTruthy());
  it('rejects malformed, whitespace and null public keys', () => {
    expect(PublicKeySchema.parse(key)).toBe(key);
    for (const bad of [key + '\n', Buffer.alloc(32).toString('base64'), 'invalid']) expect(PublicKeySchema.safeParse(bad).success).toBe(false);
  });
  it('does not invent a subnet for malformed input', () => {
    for (const bad of ['invalid', '10.8.0.0/16', '10.8.0.1/24', '10.999.0.0/24']) expect(SubnetSchema.safeParse(bad).success).toBe(false);
  });
  it('rejects special-purpose public addresses', () => expect(isPublicIpv4('100.64.0.1')).toBe(false));
});
describe('Agent response confirmation', () => {
  const original = global.fetch;
  afterEach(() => { global.fetch = original; });
  test.each([{ success: false }, { success: true }, { success: true, persisted: false }])('requires confirmed persistence: %j', async payload => {
    global.fetch = jest.fn(async (_url, options) => {
      const headers = options?.headers as Record<string,string>;
      const body = JSON.stringify(payload);
      const signed = crypto.createHmac('sha256', 'x'.repeat(32)).update(`response.${headers['X-Agent-Timestamp']}.POST./api/v1/agent/peers.${body}`).digest('hex');
      return new Response(body, { headers: { 'X-Agent-Response-Signature': signed } });
    }) as typeof fetch;
    await expect(AgentClient.addPeer('http://node', 'env:NSEC_TEST_AGENT_KEY', { publicKey: key, allocatedIp: '10.8.0.2/32', expiresAt: new Date().toISOString() })).rejects.toThrow();
  });
  it('accepts only signed durable confirmation and rejects a tampered response', async () => {
    let tamper = false;
    global.fetch = jest.fn(async (_url, options) => {
      const headers = options?.headers as Record<string,string>;
      const body = JSON.stringify({ success: true, persisted: true });
      const signature = crypto.createHmac('sha256', 'x'.repeat(32)).update(`response.${headers['X-Agent-Timestamp']}.POST./api/v1/agent/peers.${body}`).digest('hex');
      return new Response(tamper ? body.replace('true','false') : body, { headers: { 'X-Agent-Response-Signature': signature } });
    }) as typeof fetch;
    const input = { publicKey: key, allocatedIp: '10.8.0.2/32', expiresAt: new Date().toISOString() };
    await expect(AgentClient.addPeer('http://127.0.0.1:51821','env:NSEC_TEST_AGENT_KEY',input)).resolves.toBeUndefined();
    tamper = true;
    await expect(AgentClient.addPeer('http://127.0.0.1:51821','env:NSEC_TEST_AGENT_KEY',input)).rejects.toThrow('AGENT_RESPONSE_UNAUTHENTICATED');
  });
  it('does not treat an offline HTTP-200 health response as online', async () => {
    global.fetch = jest.fn(async (_url, options) => {
      const headers = options?.headers as Record<string,string>;
      const body = JSON.stringify({ online: false, peerCount: 0, loadPercent: 0 });
      const signed = crypto.createHmac('sha256', 'x'.repeat(32)).update(`response.${headers['X-Agent-Timestamp']}.GET./api/v1/agent/health.${body}`).digest('hex');
      return new Response(body, { headers: { 'X-Agent-Response-Signature': signed } });
    }) as typeof fetch;
    expect((await AgentClient.getNodeHealth('http://node', 'env:NSEC_TEST_AGENT_KEY')).online).toBe(false);
  });
});
describe('Authenticator enforcement', () => {
  it('checks RFC6238 vector reduced to six digits', () => {
    expect(verifyTotp('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', '287082', 59000)).toBe(true);
    expect(verifyTotp('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', '000000', 59000)).toBe(false);
  });
});

it('does not accept raw database agent credentials or fabricate an environment credential', async () => {
  await expect(AgentClient.addPeer('http://127.0.0.1:51821','x'.repeat(32),{publicKey:key,allocatedIp:'10.8.0.2/32',expiresAt:new Date().toISOString()})).rejects.toThrow('AGENT_CREDENTIAL_REFERENCE_REQUIRED');
  await expect(AgentClient.addPeer('http://127.0.0.1:51821','env:NSEC_ABSENT_TEST_KEY',{publicKey:key,allocatedIp:'10.8.0.2/32',expiresAt:new Date().toISOString()})).rejects.toThrow('AGENT_CREDENTIAL_CONFIGURATION_MISSING');
});

it('TOTP secrets are resolved from environment references only', () => {
  process.env.NSEC_TEST_TOTP_SECRET = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
  expect(verifyConfiguredTotp('env:NSEC_TEST_TOTP_SECRET', '287082', 59000)).toBe(true);
  expect(verifyConfiguredTotp(process.env.NSEC_TEST_TOTP_SECRET, '287082', 59000)).toBe(false);
  expect(verifyConfiguredTotp('env:NSEC_ABSENT_TEST_TOTP', '287082', 59000)).toBe(false);
  delete process.env.NSEC_TEST_TOTP_SECRET;
});
