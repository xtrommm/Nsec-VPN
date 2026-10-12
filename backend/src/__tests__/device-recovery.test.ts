jest.mock('../db', () => ({ prisma: { $transaction: jest.fn() } }));
jest.mock('../secrets', () => ({ JWT_SECRET: require('crypto').randomBytes(32).toString('hex') }));
jest.mock('bcryptjs', () => ({ hash: jest.fn(async () => 'test-hash'), compare: jest.fn() }));
import bcrypt from 'bcryptjs';
import { prisma } from '../db';
import { recoverDevice, setRecoveryPin, currentAuthEpoch, RecoverDeviceSchema } from '../services/deviceRecovery';
const expiry = new Date(Date.now() + 86400000);
const device = { id: 'internal-id', deviceId: 'installation-id', recoveryPinHash: 'old-hash',
  recoveryFailures: 0, recoveryBlockedUntil: null, authEpoch: 0, tier: 'VIP', passExpiresAt: expiry, isBlocked: false };
const tx = { $executeRaw: jest.fn(), device: { findUnique: jest.fn(), findUniqueOrThrow: jest.fn(), update: jest.fn() },
  refreshToken: { updateMany: jest.fn(), create: jest.fn() }, peerSession: { updateMany: jest.fn() } };
beforeEach(() => {
  jest.clearAllMocks();
  tx.device.findUnique.mockResolvedValue(device); tx.device.findUniqueOrThrow.mockResolvedValue(device);
  tx.device.update.mockResolvedValue({ ...device, authEpoch: 1 });
  (prisma.$transaction as jest.Mock).mockImplementation(async fn => fn(tx));
  (bcrypt.compare as jest.Mock).mockResolvedValue(true);
});
it('device ID alone is not recovery proof', () => {
  expect(RecoverDeviceSchema.safeParse({ deviceId: device.deviceId, registrationSecret: 'a'.repeat(64) }).success).toBe(false);
});
it('preserves the same VIP record and expiry, rotates credentials, revokes sessions and keeps peer reservations', async () => {
  const result = await recoverDevice(device.deviceId, '48372615', 'a'.repeat(64));
  expect(result.device).toEqual({ id: device.id, tier: 'VIP', passExpiresAt: expiry.toISOString() });
  const update = tx.device.update.mock.calls[0][0];
  expect(update.data.authEpoch).toEqual({ increment: 1 });
  expect(update.data).not.toHaveProperty('tier'); expect(update.data).not.toHaveProperty('passExpiresAt');
  expect(update.data.registrationSecretHash).not.toBe('a'.repeat(64));
  expect(tx.refreshToken.updateMany).toHaveBeenCalledWith({ where: { deviceId: device.id }, data: { revoked: true } });
  expect(tx.peerSession.updateMany).toHaveBeenCalledWith(expect.objectContaining({ where: { deviceId: device.id, active: true } }));
  expect(result).not.toHaveProperty('recoveryPinHash');
});
it('does not renew expired VIP during recovery', async () => {
  tx.device.findUnique.mockResolvedValue({ ...device, passExpiresAt: new Date(1) });
  tx.device.update.mockResolvedValue({ ...device, authEpoch: 1, tier: 'FREE', passExpiresAt: null });
  const result = await recoverDevice(device.deviceId, '48372615', 'a'.repeat(64));
  expect(result.device.tier).toBe('FREE'); expect(result.device.passExpiresAt).toBeNull();
});
it('wrong PIN commits the failed-attempt counter without issuing tokens or rotating proof', async () => {
  (bcrypt.compare as jest.Mock).mockResolvedValue(false);
  await expect(recoverDevice(device.deviceId, '48372615', 'a'.repeat(64))).rejects.toThrow('INVALID_RECOVERY_PROOF');
  expect(tx.device.update).toHaveBeenCalledWith(expect.objectContaining({ data: { recoveryFailures: 1, recoveryBlockedUntil: null } }));
  expect(tx.refreshToken.create).not.toHaveBeenCalled();
});
it('fifth failure persists a 15-minute lock across app clear-data and API restarts', async () => {
  tx.device.findUnique.mockResolvedValue({ ...device, recoveryFailures: 4 });
  (bcrypt.compare as jest.Mock).mockResolvedValue(false);
  await expect(recoverDevice(device.deviceId, '48372615', 'a'.repeat(64))).rejects.toThrow('INVALID_RECOVERY_PROOF');
  const lock = tx.device.update.mock.calls[0][0].data.recoveryBlockedUntil;
  expect(lock.getTime() - Date.now()).toBeGreaterThan(14 * 60000);
});
it('blocked or unconfigured identities never yield tokens', async () => {
  tx.device.findUnique.mockResolvedValue({ ...device, recoveryPinHash: null });
  await expect(recoverDevice(device.deviceId, '48372615', 'a'.repeat(64))).rejects.toThrow('INVALID_RECOVERY_PROOF');
  expect(tx.refreshToken.create).not.toHaveBeenCalled();
});
it('changing an existing PIN requires its current private proof', async () => {
  await expect(setRecoveryPin(device.id, '48372615')).rejects.toThrow('INVALID_RECOVERY_PROOF');
  expect(tx.device.update.mock.calls[0][0].data).not.toHaveProperty('recoveryPinHash');
});
it('legacy tokens work until recovery increments the epoch, then both old JWTs and refresh proofs are rejected', () => {
  expect(currentAuthEpoch(undefined, 0)).toBe(true);
  expect(currentAuthEpoch(0, 1)).toBe(false);
  expect(currentAuthEpoch(undefined, 1)).toBe(false);
  expect(currentAuthEpoch(1, 1)).toBe(true);
  expect(currentAuthEpoch(-1, -1)).toBe(false);
});
