import crypto from 'crypto';

export interface AddPeerPayload {
  publicKey: string;
  allocatedIp: string;
}

export interface RemovePeerPayload {
  publicKey: string;
}

export interface NodeHealthResponse {
  online: boolean;
  peerCount: number;
  loadPercent: number;
}

export class AgentClient {
  /**
   * Calls the remote node agent to register a peer on wg0.
   */
  static async addPeer(
    agentUrl: string,
    secret: string,
    payload: AddPeerPayload,
    timeoutMs = 4000
  ): Promise<{ success: boolean; message?: string }> {
    const timestamp = Date.now().toString();
    const bodyStr = JSON.stringify(payload);
    const signature = crypto
      .createHmac('sha256', secret)
      .update(`${timestamp}.${bodyStr}`)
      .digest('hex');

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const response = await fetch(`${agentUrl.replace(/\/$/, '')}/api/v1/agent/peers`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Agent-Timestamp': timestamp,
          'X-Agent-Signature': signature
        },
        body: bodyStr,
        signal: controller.signal
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Agent returned HTTP ${response.status}: ${errorText}`);
      }

      return (await response.json()) as { success: boolean };
    } catch (err: any) {
      if (err.name === 'AbortError') {
        throw new Error('Agent connection timed out after 4 seconds');
      }
      throw err;
    } finally {
      clearTimeout(timeout);
    }
  }

  /**
   * Calls the remote node agent to remove a peer from wg0.
   */
  static async removePeer(
    agentUrl: string,
    secret: string,
    payload: RemovePeerPayload,
    timeoutMs = 4000
  ): Promise<{ success: boolean }> {
    const timestamp = Date.now().toString();
    const bodyStr = JSON.stringify(payload);
    const signature = crypto
      .createHmac('sha256', secret)
      .update(`${timestamp}.${bodyStr}`)
      .digest('hex');

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const response = await fetch(`${agentUrl.replace(/\/$/, '')}/api/v1/agent/peers`, {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json',
          'X-Agent-Timestamp': timestamp,
          'X-Agent-Signature': signature
        },
        body: bodyStr,
        signal: controller.signal
      });

      if (!response.ok) {
        return { success: false };
      }
      return (await response.json()) as { success: boolean };
    } catch (_err) {
      return { success: false };
    } finally {
      clearTimeout(timeout);
    }
  }

  /**
   * Pings the node agent for status and current connected peer count.
   */
  static async getNodeHealth(
    agentUrl: string,
    secret: string
  ): Promise<NodeHealthResponse> {
    const timestamp = Date.now().toString();
    const signature = crypto.createHmac('sha256', secret).update(timestamp).digest('hex');

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2500);

    try {
      const res = await fetch(`${agentUrl.replace(/\/$/, '')}/api/v1/agent/health`, {
        headers: {
          'X-Agent-Timestamp': timestamp,
          'X-Agent-Signature': signature
        },
        signal: controller.signal
      });
      if (res.ok) {
        const data = (await res.json()) as any;
        return { online: true, peerCount: data.peerCount || 0, loadPercent: data.loadPercent || 0 };
      }
    } catch (_e) {
      // Node agent unreachable or timeout
    } finally {
      clearTimeout(timeout);
    }
    return { online: false, peerCount: 0, loadPercent: 0 };
  }
}
