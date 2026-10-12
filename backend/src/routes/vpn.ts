import { Router, Response } from 'express';
import { prisma } from '../db';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { ConnectVpnSchema, DisconnectVpnSchema } from '../types';
import { AgentClient } from '../services/agentClient';
import { connectLimiter } from '../middleware/rateLimit';
import { provisionPeer } from '../services/peerProvisioning';

const router = Router();
router.post('/connect', authenticateDevice, connectLimiter, validateBody(ConnectVpnSchema), async (req: AuthenticatedDeviceRequest, res: Response) => {
  try {
    const data = await provisionPeer(req.device!.id, req.body.serverId, req.body.clientPublicKey, req.device!.tier);
    return res.json({ success: true, data });
  } catch (error) {
    const code = error instanceof Error ? error.message : '';
    const codes: Record<string, number> = { MAINTENANCE_MODE: 503, SERVER_NOT_FOUND: 404, NODE_DISABLED: 409, NODE_OFFLINE: 503,
      DEVICE_BLOCKED: 403, KEY_ALREADY_REGISTERED: 409, SERVER_FULL: 503, AGENT_UNREACHABLE: 502, TIER_UPGRADE_REQUIRED: 403 };
    return res.status(codes[code] ?? 500).json({ success: false, error: codes[code] ? code : 'INTERNAL_ERROR',
      message: codes[code] ? code.replace(/_/g, ' ') : 'Failed to negotiate VPN session' });
  }
});
router.post('/disconnect', authenticateDevice, validateBody(DisconnectVpnSchema), async (req: AuthenticatedDeviceRequest, res: Response) => {
  try {
    const s = await prisma.peerSession.findFirst({ where: { peerId: req.body.peerId, deviceId: req.device!.id }, include: { server: true } });
    if (s) {
      await AgentClient.removePeer(s.server.agentUrl, s.server.agentSecretHash, { publicKey: s.clientPublicKey });
      await prisma.peerSession.delete({ where: { id: s.id } });
    }
    return res.json({ success: true, message: 'Disconnected' });
  } catch { return res.status(502).json({ success: false, error: 'AGENT_UNREACHABLE', message: 'Peer removal pending; lease retained' }); }
});
export default router;
