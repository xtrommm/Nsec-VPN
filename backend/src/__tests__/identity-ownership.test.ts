jest.mock('../secrets', () => ({ JWT_SECRET: require('crypto').randomBytes(32).toString('hex') }));
jest.mock('../db', () => ({ prisma: { $transaction: jest.fn(), device: { update: jest.fn() }, refreshToken: { create: jest.fn() }, peerSession: { findFirst: jest.fn(), delete: jest.fn() } } }));
jest.mock('../services/agentClient', () => ({ AgentClient: { removePeer: jest.fn() } }));
import crypto from 'crypto';
import { prisma } from '../db';
import auth from '../routes/auth';
import vpn from '../routes/vpn';
import { AgentClient } from '../services/agentClient';
const routeHandler = (router: any, path: string) => router.stack.find((layer: any) => layer.route && (layer.route.path === path || Array.isArray(layer.route.path) && layer.route.path.includes(path))).route.stack.at(-1).handle;
function response() {
  const res: any = { code: 200, payload: null };
  res.status = (code: number) => { res.code = code; return res; };
  res.json = (payload: unknown) => { res.payload = payload; return res; };
  return res;
}
beforeEach(() => jest.clearAllMocks());
it('knowing an existing UUID cannot replace its credential or obtain tokens', async () => {
  const oldHash = crypto.createHash('sha256').update(crypto.randomBytes(32).toString('hex')).digest('hex');
  const tx = { $executeRaw: jest.fn(), device: { findUnique: jest.fn().mockResolvedValue({ registrationSecretHash: oldHash }), upsert: jest.fn() } };
  (prisma.$transaction as jest.Mock).mockImplementation(async work => work(tx));
  const res = response();
  await routeHandler(auth,'/register-device-v2')({ body: { deviceId: 'test-device', registrationSecret: crypto.randomBytes(32).toString('hex') } },res);
  expect(res.code).toBe(409); expect(res.payload.error).toBe('DEVICE_PROOF_REQUIRED');
  expect(tx.device.upsert).not.toHaveBeenCalled(); expect(prisma.refreshToken.create).not.toHaveBeenCalled();
});
it('legacy registration without a device-held secret fails explicitly with version switch', async () => {
  const res = response();
  await routeHandler(auth,'/register-device')({ body: { deviceId: 'test-device' } },res);
  expect(res.code).toBe(426);expect(res.payload.error).toBe('APP_UPDATE_REQUIRED');expect(prisma.$transaction).not.toHaveBeenCalled();
});
it('disconnect uses own stored public key, never supplied replacement key', async () => {
  const storedKey = Buffer.alloc(32,8).toString('base64');
  (prisma.peerSession.findFirst as jest.Mock).mockResolvedValue({ id:'session-test', clientPublicKey:storedKey, server:{agentUrl:'http://test.invalid',agentSecretHash:crypto.randomBytes(32).toString('hex')} });
  const res=response();
  await routeHandler(vpn,'/disconnect')({device:{id:'owner-device'},body:{peerId:'peer-test',clientPublicKey:Buffer.alloc(32,9).toString('base64')}},res);
  expect(prisma.peerSession.findFirst).toHaveBeenCalledWith(expect.objectContaining({where:{peerId:'peer-test',deviceId:'owner-device'}}));
  expect(AgentClient.removePeer).toHaveBeenCalledWith(expect.any(String),expect.any(String),{publicKey:storedKey});
  expect(res.payload.success).toBe(true);
});
