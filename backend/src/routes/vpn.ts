import { Router, Response } from 'express';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { ConnectVpnSchema, DisconnectVpnSchema } from '../types';
import { connectLimiter } from '../middleware/rateLimit';
import { provisionPeer, disconnectPeer } from '../services/peerProvisioning';
import { provisionLocationPeer } from '../services/locationSelection';
import { clientVersion } from '../services/clientVersion';

const router = Router();
router.post('/connect', authenticateDevice, connectLimiter, validateBody(ConnectVpnSchema), async (req: AuthenticatedDeviceRequest, res: Response) => {
  try {
    const versionCode = clientVersion(req.get('X-App-Version-Code'));
    const data = req.body.locationSelection
      ? await provisionLocationPeer(req.device!.id, req.body.serverId, req.body.clientPublicKey, req.body.nodeLatencies, req.body.excludedNodeIds, versionCode)
      : await provisionPeer(req.device!.id, req.body.serverId, req.body.clientPublicKey, req.device!.tier, versionCode);
    return res.json({ success: true, data });
  } catch (error) {
    const code = error instanceof Error ? error.message : '';
    const codes: Record<string, number> = { APP_UPDATE_REQUIRED: 426, MAINTENANCE_MODE: 503, SERVER_NOT_FOUND: 404, NODE_DISABLED: 409, NODE_OFFLINE: 503,
      DEVICE_BLOCKED: 403, KEY_ALREADY_REGISTERED: 409, PEER_RESERVATION_CHANGED: 409, PEER_CLEANUP_PENDING: 503, SERVER_FULL: 503, ALL_SERVERS_FULL: 503, AGENT_UNREACHABLE: 502, TIER_UPGRADE_REQUIRED: 403 };
    return res.status(codes[code] ?? 500).json({ success: false, error: codes[code] ? code : 'INTERNAL_ERROR',
      message: codes[code] ? code.replace(/_/g, ' ') : 'Failed to negotiate VPN session' });
  }
});
router.post('/disconnect', authenticateDevice, validateBody(DisconnectVpnSchema), async (req: AuthenticatedDeviceRequest, res: Response) => {
  try {
    await disconnectPeer(req.device!.id, req.body.peerId);
    return res.json({ success: true, message: 'Disconnected' });
  } catch { return res.status(502).json({ success: false, error: 'AGENT_UNREACHABLE', message: 'Peer removal pending; lease retained' }); }
});
export default router;
