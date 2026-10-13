jest.mock('../secrets', () => ({ JWT_SECRET: require('crypto').randomBytes(32).toString('hex') }));
jest.mock('../db', () => ({ prisma: { $transaction: jest.fn(), appConfig: { upsert: jest.fn() },
  adminUser: { findUniqueOrThrow: jest.fn() }, peerSession: { findMany: jest.fn() } } }));
import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import { prisma } from '../db';
import { totpStep, encryptTotpSeed, readTotpSeed, verifyTotp } from '../services/totp';
import { beginAdminMfa, consumeAdminTotp, enableAdminMfa } from '../services/adminMfa';
import { rotateRefreshToken } from '../services/refreshTokens';
import { bootstrapAdmin } from '../services/bootstrapAdmin';
import { StatsService } from '../services/stats';
import config, { invalidateConfigCache } from '../routes/config';
import { VerifySubscriptionSchema } from '../types';
const response = () => { const r: any = { statusCode: 200 }; r.status = (v: number) => { r.statusCode = v; return r; }; r.setHeader = jest.fn(); r.json = (body: unknown) => { r.body = body; return r; }; return r; };
const handler = (router: any, path: string) => router.stack.find((l: any) => l.route?.path === path).route.stack.at(-1).handle;
const seed = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
beforeEach(() => { jest.clearAllMocks(); invalidateConfigCache(); process.env.ADMIN_TOTP_ENCRYPTION_KEY = crypto.randomBytes(32).toString('hex'); });
afterEach(() => { jest.useRealTimers(); jest.restoreAllMocks(); delete process.env.ADMIN_TOTP_ENCRYPTION_KEY; });
it('encrypts enrollment seeds, rejects tampering, and safely handles negative clock steps', () => {
  const cipher = encryptTotpSeed(seed, 100000);
  expect(cipher).not.toContain(seed); expect(readTotpSeed(cipher)?.secret).toBe(seed);
  const bytes = Buffer.from(cipher.slice(7), 'base64'); bytes[bytes.length - 1] ^= 1;
  expect(() => readTotpSeed('enc:v1:' + bytes.toString('base64'))).toThrow();
  expect(totpStep(seed, '287082', 59000)).toBe(1);
  expect(() => verifyTotp(seed, '000000', 0)).not.toThrow();
});
it('consumes one authenticator time step once under its admin lock', async () => {
  jest.spyOn(Date, 'now').mockReturnValue(59000);
  const admin: any = { id: 'admin', totpEnabled: true, totpLastStep: null, totpSecret: encryptTotpSeed(seed, 100000) };
  const tx: any = { $executeRaw: jest.fn(), adminUser: { findUniqueOrThrow: jest.fn().mockResolvedValue(admin), update: jest.fn().mockImplementation(({ data }) => { Object.assign(admin, data); return admin; }) } };
  (prisma.$transaction as jest.Mock).mockImplementation(fn => fn(tx));
  await consumeAdminTotp('admin', '287082');
  await expect(consumeAdminTotp('admin', '287082')).rejects.toThrow('TOTP_REQUIRED');
  expect(tx.adminUser.update).toHaveBeenCalledTimes(1);
  expect(tx.$executeRaw.mock.invocationCallOrder[0]).toBeLessThan(tx.adminUser.update.mock.invocationCallOrder[0]);
});
it('requires password reauthentication, expires enrollment and confirms only the real seed', async () => {
  jest.spyOn(Date, 'now').mockReturnValue(59000);
  const admin: any = { id: 'admin', email: 'fixture@example.test', role: 'ADMIN', totpEnabled: false,
    passwordHash: await bcrypt.hash('Fixture-Password-123!', 4), totpSecret: encryptTotpSeed(seed, 100000) };
  const tx: any = { $executeRaw: jest.fn(), auditLog: { create: jest.fn() }, adminUser: {
    findUniqueOrThrow: jest.fn().mockResolvedValue(admin), update: jest.fn().mockImplementation(({ data }) => { Object.assign(admin, data); return admin; }) } };
  (prisma.$transaction as jest.Mock).mockImplementation(fn => fn(tx));
  await expect(beginAdminMfa('admin', 'incorrect')).rejects.toThrow('REAUTHENTICATION_REQUIRED');
  await expect(enableAdminMfa('admin', 'Fixture-Password-123!', '000000')).rejects.toThrow('TOTP_INVALID');
  admin.totpSecret = encryptTotpSeed(seed, 0);
  await expect(enableAdminMfa('admin', 'Fixture-Password-123!', '287082')).rejects.toThrow('TOTP_ENROLLMENT_EXPIRED');
  admin.totpSecret = encryptTotpSeed(seed, 100000);
  expect((await enableAdminMfa('admin', 'Fixture-Password-123!', '287082')).token).toBeTruthy();
  expect(admin.totpEnabled).toBe(true); expect(admin.totpLastStep).toBe(1);
});
it('proven refresh reuse revokes both refresh and access credentials and queues lease cleanup', async () => {
  const token: any = { id: 'token', authEpoch: 3, revoked: true, deviceId: 'internal', device: { id: 'internal', deviceId: 'fixture-device', authEpoch: 3 } };
  const tx: any = { $executeRaw: jest.fn(), refreshToken: { findUnique: jest.fn().mockResolvedValue(token), updateMany: jest.fn() },
    device: { update: jest.fn() }, peerSession: { updateMany: jest.fn() } };
  (prisma.$transaction as jest.Mock).mockImplementation(fn => fn(tx));
  expect(await rotateRefreshToken('fixture-replayed-token')).toEqual({ error: 'TOKEN_COMPROMISED', status: 401 });
  expect(tx.device.update).toHaveBeenCalledWith({ where: { id: 'internal' }, data: { authEpoch: { increment: 1 } } });
  expect(tx.refreshToken.updateMany).toHaveBeenCalled(); expect(tx.peerSession.updateMany).toHaveBeenCalled();
});
it('expired refresh at the exact deadline is rejected without rotating', async () => {
  const time = Date.now(); jest.useFakeTimers({ now: time });
  const tx: any = { $executeRaw: jest.fn(), refreshToken: { findUnique: jest.fn().mockResolvedValue({ revoked: false,
    authEpoch: 0, expiresAt: new Date(time), device: { deviceId: 'fixture-device', authEpoch: 0 } }), create: jest.fn() } };
  (prisma.$transaction as jest.Mock).mockImplementation(fn => fn(tx));
  expect((await rotateRefreshToken('fixture-expired-token')).error).toBe('REFRESH_TOKEN_EXPIRED');
  expect(tx.refreshToken.create).not.toHaveBeenCalled();
});
it('bootstrap has a lock and fails without first-admin credentials rather than opening an unusable service', async () => {
  const tx: any = { $executeRaw: jest.fn(), adminUser: { count: jest.fn().mockResolvedValue(0), create: jest.fn() } };
  (prisma.$transaction as jest.Mock).mockImplementation(fn => fn(tx));
  const oldEmail = process.env.ADMIN_EMAIL; delete process.env.ADMIN_EMAIL;
  await expect(bootstrapAdmin()).rejects.toThrow(); expect(tx.adminUser.create).not.toHaveBeenCalled();
  if (oldEmail !== undefined) process.env.ADMIN_EMAIL = oldEmail;
  tx.adminUser.count.mockResolvedValue(1); expect(await bootstrapAdmin()).toBe(false);
});
it('bounds receipt verification inputs before contacting Google', () => {
  for (const input of [{ packageName: 'x'.repeat(256), productId: 'vip', purchaseToken: 'valid-token-123' },
    { packageName: 'com.nsec.vpn', productId: 'x'.repeat(129), purchaseToken: 'valid-token-123' },
    { packageName: 'com.nsec.vpn', productId: 'vip', purchaseToken: 'x'.repeat(16385) }]) expect(VerifySubscriptionSchema.safeParse(input).success).toBe(false);
});
it('unknown legal document returns 404 rather than fabricated terms', () => {
  const r = response(); handler(config, '/legal/:type')({ params: { type: 'unknown' } }, r);
  expect(r.statusCode).toBe(404); expect(r.body.error).toBe('LEGAL_DOCUMENT_NOT_FOUND');
});
it('old in-flight config cannot repopulate cache after invalidation', async () => {
  let release!: (v: unknown) => void;
  (prisma.appConfig.upsert as jest.Mock).mockImplementationOnce(() => new Promise(resolve => { release = resolve; }))
    .mockResolvedValue({ updatedAt: 'new-version' });
  const old = response(), fresh = response(), cached = response();
  const request = handler(config, '/config'); const oldWork = request({}, old);
  invalidateConfigCache(); await request({}, fresh);
  release({ updatedAt: 'old-version' }); await oldWork; await request({}, cached);
  expect(cached.body.data.updatedAt).toBe('new-version'); expect(prisma.appConfig.upsert).toHaveBeenCalledTimes(2);
});
it('coalesces config cache misses without changing response shape', async () => {
  let release!: (v: unknown) => void;
  (prisma.appConfig.upsert as jest.Mock).mockImplementation(() => new Promise(resolve => { release = resolve; }));
  const request = handler(config, '/config'), one = response(), two = response();
  const a = request({}, one), b = request({}, two); release({ updatedAt: 'same-version' }); await Promise.all([a, b]);
  expect(prisma.appConfig.upsert).toHaveBeenCalledTimes(1); expect(one.body).toEqual(two.body);
});
it('uses UTC calendar dates and serializes aggregate writes', async () => {
  const tx: any = { $executeRaw: jest.fn(), anonymousStat: { findFirst: jest.fn().mockResolvedValue(null), create: jest.fn() } };
  (prisma.$transaction as jest.Mock).mockImplementation(fn => fn(tx));
  await StatsService.recordConnection('SG', 'fixture', 'FREE');
  const date = tx.anonymousStat.create.mock.calls[0][0].data.date as Date;
  expect(date.getUTCHours()).toBe(0); expect(date.getUTCMinutes()).toBe(0); expect(tx.$executeRaw).toHaveBeenCalled();
});
it('successful refresh rotation reads the token again only after the identity lock', async () => {
  const device:any={id:'internal',deviceId:'fixture-device',authEpoch:0,tier:'FREE',passExpiresAt:null,isBlocked:false};
  const token:any={id:'token',deviceId:device.id,device,authEpoch:0,revoked:false,expiresAt:new Date(Date.now()+60000)};
  const tx:any={$executeRaw:jest.fn(),refreshToken:{findUnique:jest.fn().mockResolvedValue(token),update:jest.fn(),create:jest.fn()},
    device:{findUniqueOrThrow:jest.fn().mockResolvedValue(device)}};
  (prisma.$transaction as jest.Mock).mockImplementation(fn=>fn(tx));
  expect((await rotateRefreshToken('fixture-current-token')).data?.accessToken).toBeTruthy();
  expect(tx.$executeRaw.mock.invocationCallOrder[0]).toBeLessThan(tx.refreshToken.findUnique.mock.invocationCallOrder[1]);
  expect(tx.refreshToken.update.mock.invocationCallOrder[0]).toBeLessThan(tx.refreshToken.create.mock.invocationCallOrder[0]);
});
