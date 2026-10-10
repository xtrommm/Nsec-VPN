import { Router, Response } from 'express';
import crypto from 'crypto';
import { prisma } from '../db';
import { authenticateDevice, AuthenticatedDeviceRequest } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { ConnectVpnSchema, DisconnectVpnSchema } from '../types';
import { IpPoolService } from '../services/ipPool';
import { AgentClient } from '../services/agentClient';
import { StatsService } from '../services/stats';
import { connectLimiter } from '../middleware/rateLimit';

const router = Router();
const PEER_LEASE_TTL_MS = 24 * 60 * 60 * 1000; // 24-hour lease

/**
 * Recalculates and persists currentLoad percentage for a server node.
 */
async function syncServerLoad(serverId: string): Promise<number> {
  try {
    const activePeers = await prisma.peerSession.count({
      where: { serverId, active: true }
    });
    // Standard WireGuard /24 node capacity is 250 peers
    const currentLoad = Math.min(Math.round((activePeers / 250) * 100), 100);
    await prisma.serverNode.update({
      where: { id: serverId },
      data: { currentLoad }
    });
    return currentLoad;
  } catch (_e) {
    return 0;
  }
}

router.post(
  '/connect',
  connectLimiter,
  authenticateDevice,
  validateBody(ConnectVpnSchema),
  async (req: AuthenticatedDeviceRequest, res: Response) => {
    const { serverId, clientPublicKey } = req.body;
    const device = req.device!;

    try {
      const requestedServer = await prisma.serverNode.findFirst({
        where: { id: serverId, active: true }
      });

      if (!requestedServer) {
        return res.status(404).json({
          success: false,
          error: 'SERVER_NOT_FOUND',
          message: 'The requested VPN location is inactive or does not exist'
        });
      }

      // Tier check: Free users cannot access Premium VIP servers
      if (requestedServer.isPremiumOnly && device.tier === 'FREE') {
        return res.status(403).json({
          success: false,
          error: 'TIER_UPGRADE_REQUIRED',
          message: 'This 10Gbps high-speed location requires a VIP subscription'
        });
      }

      // Cluster Load Balancing:
      // If there are multiple servers in this location (e.g. 10 Singapore servers),
      // dynamically route to the sister node with the lowest load in that pool.
      const clusterNodes = await prisma.serverNode.findMany({
        where: {
          countryCode: requestedServer.countryCode,
          city: requestedServer.city,
          active: true,
          isPremiumOnly: requestedServer.isPremiumOnly
        },
        orderBy: { currentLoad: 'asc' }
      });

      const server = clusterNodes[0] || requestedServer;

      // Idempotency: check if active session with same public key already exists for this device
      const existingSession = await prisma.peerSession.findFirst({
        where: {
          deviceId: device.id,
          serverId: server.id,
          clientPublicKey,
          active: true,
          expiresAt: { gt: new Date() }
        }
      });

      if (existingSession) {
        return res.json({
          success: true,
          data: {
            peerId: existingSession.peerId,
            tunnelConfig: {
              serverPublicKey: server.publicKey,
              serverEndpoint: server.endpoint,
              clientAddress: existingSession.allocatedIp,
              dnsServers: ['1.1.1.1', '1.0.0.1'],
              allowedIps: ['0.0.0.0/0', '::/0'],
              persistentKeepalive: 25,
              mtu: 1420
            }
          }
        });
      }

      // Deactivate older active sessions from this device on this server
      await prisma.peerSession.updateMany({
        where: { deviceId: device.id, serverId: server.id, active: true },
        data: { active: false }
      });

      // 1. Atomically allocate a unique IP address in the server subnet
      const allocatedIp = await IpPoolService.allocateIp(server.id, server.subnetIpv4);
      const peerId = `peer_${crypto.randomBytes(12).toString('hex')}`;
      const expiresAt = new Date(Date.now() + PEER_LEASE_TTL_MS);

      // 2. Register the peer with the database
      const session = await prisma.peerSession.create({
        data: {
          peerId,
          deviceId: device.id,
          serverId: server.id,
          allocatedIp,
          clientPublicKey,
          active: true,
          expiresAt
        }
      });

      // 3. Register the peer on the live WireGuard gateway server agent
      if (server.agentUrl && server.agentUrl !== 'http://mock') {
        try {
          await AgentClient.addPeer(server.agentUrl, server.agentSecretHash, {
            publicKey: clientPublicKey,
            allocatedIp
          });
        } catch (agentErr: any) {
          // ROLLBACK DB SESSION IF AGENT REGISTRATION FAILS
          await prisma.peerSession.update({
            where: { id: session.id },
            data: { active: false }
          });
          await syncServerLoad(server.id);

          return res.status(502).json({
            success: false,
            error: 'GATEWAY_UNREACHABLE',
            message: 'Target VPN gateway failed to configure handshake. Please try another location.'
          });
        }
      }

      // 4. Update real-time server load
      await syncServerLoad(server.id);

      // 5. Record strictly anonymous telemetry (zero logs)
      StatsService.recordConnection(server.countryCode, '1.0.0', device.tier);

      // 6. Return WireGuard client configuration to Android app
      return res.json({
        success: true,
        data: {
          peerId: session.peerId,
          tunnelConfig: {
            serverPublicKey: server.publicKey,
            serverEndpoint: server.endpoint,
            clientAddress: allocatedIp,
            dnsServers: ['1.1.1.1', '1.0.0.1'],
            allowedIps: ['0.0.0.0/0', '::/0'],
            persistentKeepalive: 25,
            mtu: 1420
          }
        }
      });
    } catch (err: any) {
      if (err.message && err.message.startsWith('SERVER_CAPACITY_REACHED')) {
        return res.status(503).json({
          success: false,
          error: 'SERVER_FULL',
          message: 'This location is at maximum capacity. Connecting to optimal alternative.'
        });
      }
      return res.status(500).json({
        success: false,
        error: 'INTERNAL_ERROR',
        message: 'Failed to negotiate VPN session'
      });
    }
  }
);

router.post(
  '/disconnect',
  authenticateDevice,
  validateBody(DisconnectVpnSchema),
  async (req: AuthenticatedDeviceRequest, res: Response) => {
    const { peerId, clientPublicKey } = req.body;
    const device = req.device!;

    try {
      // Security fix: Enforce that deviceId matches the owning session to prevent IDOR attacks
      const session = await prisma.peerSession.findFirst({
        where: {
          peerId,
          deviceId: device.id,
          active: true
        },
        include: { server: true }
      });

      if (session) {
        await prisma.peerSession.update({
          where: { id: session.id },
          data: { active: false }
        });

        // Notify gateway agent to remove peer from interface
        if (session.server.agentUrl && session.server.agentUrl !== 'http://mock') {
          AgentClient.removePeer(session.server.agentUrl, session.server.agentSecretHash, {
            publicKey: clientPublicKey || session.clientPublicKey
          }).catch(() => {});
        }

        // Update server load
        await syncServerLoad(session.serverId);
      }

      return res.json({ success: true, message: 'Disconnected' });
    } catch (_err) {
      return res.status(500).json({ success: false, error: 'INTERNAL_ERROR', message: 'Failed to close session' });
    }
  }
);

export default router;
