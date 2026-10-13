jest.mock('../secrets', () => ({ JWT_SECRET: require('crypto').randomBytes(32).toString('hex') }));
jest.mock('../db', () => ({ prisma: { device: { findUnique: jest.fn() }, adminUser: { findUnique: jest.fn() } } }));
import jwt from 'jsonwebtoken';
import { JWT_SECRET } from '../secrets';
import { prisma } from '../db';
import { authenticateAdmin, authenticateDevice } from '../middleware/auth';
import auth from '../routes/auth';
const req = (payload: object): any => ({headers:{authorization:'Bearer '+jwt.sign(payload,JWT_SECRET)}});
const res = () => { const r: any = {code:200};r.status=(code:number)=>{r.code=code;return r;};r.json=(body:unknown)=>{r.body=body;return r;};return r; };
beforeEach(() => jest.clearAllMocks());
it('enforces the current database role instead of a stale signed SUPER_ADMIN role', async () => {
  (prisma.adminUser.findUnique as jest.Mock).mockResolvedValue({role:'ADMIN'});
  const response = res(), next = jest.fn();
  await authenticateAdmin('SUPER_ADMIN')(req({adminId:'admin',role:'SUPER_ADMIN'}),response,next);
  expect(response.code).toBe(403);expect(next).not.toHaveBeenCalled();
});
it('a device JWT cannot authorize administrator operations', async () => {
  const response = res(), next = jest.fn();
  await authenticateAdmin('SUPER_ADMIN')(req({deviceId:'device',tier:'VIP'}),response,next);
  expect(response.code).toBe(401);expect(next).not.toHaveBeenCalled();
});
it('blocked and recovered devices cannot use stale authorization', async () => {
  const device = {id:'internal',deviceId:'installation',tier:'FREE',passExpiresAt:null,authEpoch:1,isBlocked:false};
  (prisma.device.findUnique as jest.Mock).mockResolvedValue(device);
  const response = res(), next = jest.fn();
  await authenticateDevice(req({deviceId:'installation',authEpoch:0}),response,next);
  expect(response.body.error).toBe('TOKEN_REVOKED');expect(next).not.toHaveBeenCalled();
  (prisma.device.findUnique as jest.Mock).mockResolvedValue({...device,isBlocked:true});
  await authenticateDevice(req({deviceId:'installation',authEpoch:1}),response,next);
  expect(response.code).toBe(403);expect(next).not.toHaveBeenCalled();
});
it('database outages fail closed without mislabeling a valid session or exposing internals', async () => {
  const failure = new Error('synthetic database failure');
  (prisma.device.findUnique as jest.Mock).mockRejectedValue(failure);
  (prisma.adminUser.findUnique as jest.Mock).mockRejectedValue(failure);
  const response = res(), next = jest.fn();
  await authenticateDevice(req({deviceId:'installation',authEpoch:0}),response,next);
  expect(response.code).toBe(503);expect(response.body.error).toBe('AUTH_UNAVAILABLE');
  await authenticateAdmin()(req({adminId:'admin'}),response,next);
  expect(response.code).toBe(503);expect(response.body.error).toBe('ADMIN_AUTH_UNAVAILABLE');
  expect(next).not.toHaveBeenCalled();expect(JSON.stringify(response.body)).not.toContain(failure.message);
});
it('profile outages are handled rather than escaping an Express 4 async handler', async () => {
  (prisma.device.findUnique as jest.Mock).mockRejectedValue(new Error('synthetic database failure'));
  const handler = (auth as any).stack.find((layer:any)=>layer.route?.path==='/me').route.stack.at(-1).handle;
  const response = res();
  await handler({device:{id:'internal'}},response);
  expect(response.code).toBe(503);expect(response.body.error).toBe('PROFILE_UNAVAILABLE');
});

it('rejects admin-shaped or malformed JWT payloads at device routes', async () => {
  const response = res(), next = jest.fn();
  await authenticateDevice(req({ adminId: 'admin' }), response, next);
  expect(response.code).toBe(401); expect(next).not.toHaveBeenCalled();
});
it('an enabled authenticator invalidates previously issued non-MFA admin tokens', async () => {
  (prisma.adminUser.findUnique as jest.Mock).mockResolvedValue({ role: 'ADMIN', totpEnabled: true, totpSecret: 'env:AUDIT_TOTP_FIXTURE' });
  process.env.AUDIT_TOTP_FIXTURE = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
  const response = res(), next = jest.fn();
  await authenticateAdmin()(req({ adminId: 'admin' }), response, next);
  expect(response.code).toBe(401); expect(response.body.error).toBe('MFA_SESSION_REQUIRED');
  const version = require('../services/totp').totpVersion('env:AUDIT_TOTP_FIXTURE');
  await authenticateAdmin()(req({ adminId: 'admin', mfaVersion: version }), response, next);
  expect(next).toHaveBeenCalledTimes(1); delete process.env.AUDIT_TOTP_FIXTURE;
});
