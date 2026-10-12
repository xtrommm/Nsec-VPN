import crypto from 'crypto';
import { z } from 'zod';
import { PublicKeySchema, isPublicIpv4 } from '../types';

export interface AddPeerPayload { publicKey: string; allocatedIp: string; expiresAt: string }
export interface RemovePeerPayload { publicKey: string }
export interface NodeHealthResponse {
  online: boolean; peerCount: number; loadPercent: number; publicKey?: string;
  publicIpv4?: string; listenPort?: number; handshakeCount?: number;
}
const HealthSchema = z.object({
  online: z.literal(true), peerCount: z.number().int().nonnegative(), loadPercent: z.number().min(0).max(100),
  publicKey: PublicKeySchema, publicIpv4: z.string().refine(isPublicIpv4),
  listenPort: z.number().int().min(1).max(65535), handshakeCount: z.number().int().nonnegative()
});
export class AgentClient {
  private static async request(url: string, credentialReference: string, method: string, path: string, payload?: unknown): Promise<unknown> {
    if (!/^env:[A-Z][A-Z0-9_]{2,95}$/.test(credentialReference)) throw new Error('AGENT_CREDENTIAL_REFERENCE_REQUIRED');
    const secret = process.env[credentialReference.slice(4)];
    if (!secret || secret.length < 32) throw new Error('AGENT_CREDENTIAL_CONFIGURATION_MISSING');
    const timestamp = Date.now().toString();
    const body = payload === undefined ? '' : JSON.stringify(payload);
    // v2 binds the signature to method and path; the updated agent accepts v1 during rollout.
    const signature = crypto.createHmac('sha256', secret).update(`${timestamp}.${method}.${path}.${body}`).digest('hex');
    const response = await fetch(`${url.replace(/\/$/, '')}${path}`, {
      method, body: payload === undefined ? undefined : body,
      headers: { 'Content-Type': 'application/json', 'X-Agent-Timestamp': timestamp,
        'X-Agent-Signature': signature, 'X-Agent-Version': '2' },
      signal: AbortSignal.timeout(4000), redirect: 'error'
    });
    if (!response.ok) throw new Error('AGENT_UNREACHABLE');
    const bodyText = await response.text();
    const signatureHeader = response.headers.get('X-Agent-Response-Signature') || '';
    const expected = crypto.createHmac('sha256', secret).update(`response.${timestamp}.${method}.${path}.${bodyText}`).digest('hex');
    if (!/^[a-f0-9]{64}$/.test(signatureHeader) || !crypto.timingSafeEqual(Buffer.from(signatureHeader), Buffer.from(expected))) throw new Error('AGENT_RESPONSE_UNAUTHENTICATED');
    return JSON.parse(bodyText);
  }
  static async addPeer(url: string, secret: string, payload: AddPeerPayload): Promise<void> {
    const result = await this.request(url, secret, 'POST', '/api/v1/agent/peers', payload);
    z.object({ success: z.literal(true), persisted: z.literal(true) }).parse(result);
  }
  static async removePeer(url: string, secret: string, payload: RemovePeerPayload): Promise<void> {
    const result = await this.request(url, secret, 'DELETE', '/api/v1/agent/peers', payload);
    z.object({ success: z.literal(true), persisted: z.literal(true) }).parse(result);
  }
  static async getNodeHealth(url: string, secret: string): Promise<NodeHealthResponse> {
    try { return HealthSchema.parse(await this.request(url, secret, 'GET', '/api/v1/agent/health')); }
    catch { return { online: false, peerCount: 0, loadPercent: 0 }; }
  }
}
