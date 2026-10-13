import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { prisma } from '../db';
import { JWT_SECRET } from '../secrets';
import { encodeTotpSeed, encryptTotpSeed, readTotpSeed, totpStep, totpVersion } from './totp';

export async function consumeAdminTotp(id: string, code: string) {
  return prisma.$transaction(async tx => {
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`admin-mfa:${id}`}))`;
    const admin = await tx.adminUser.findUniqueOrThrow({ where: { id } });
    if (!admin.totpEnabled) throw new Error('TOTP_STATE_CHANGED');
    const seed = readTotpSeed(admin.totpSecret);
    if (!seed) throw new Error('TOTP_ENCRYPTION_NOT_CONFIGURED');
    const step = totpStep(seed.secret, code);
    if (step === null || step <= (admin.totpLastStep ?? -1)) throw new Error('TOTP_REQUIRED');
    await tx.adminUser.update({ where: { id }, data: { totpLastStep: step } });
    return { ...admin, mfaVersion: totpVersion(admin.totpSecret)! };
  });
}
export async function beginAdminMfa(id: string, password: string) {
  return prisma.$transaction(async tx => {
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`admin-mfa:${id}`}))`;
    const admin = await tx.adminUser.findUniqueOrThrow({ where: { id } });
    if (!await bcrypt.compare(password, admin.passwordHash)) throw new Error('REAUTHENTICATION_REQUIRED');
    if (admin.totpEnabled) throw new Error('TOTP_ALREADY_ENABLED');
    const secret = encodeTotpSeed(crypto.randomBytes(20)), expiresAt = Date.now() + 10 * 60000;
    await tx.adminUser.update({ where: { id }, data: { totpSecret: encryptTotpSeed(secret, expiresAt), totpLastStep: null } });
    await tx.auditLog.create({ data: { adminId: id, action: 'BEGIN_TOTP_ENROLLMENT', targetResource: 'AUTH' } });
    return { secret, expiresAt: new Date(expiresAt).toISOString(),
      authenticatorUri: `otpauth://totp/${encodeURIComponent(`Nsec VPN:${admin.email}`)}?secret=${secret}&issuer=Nsec%20VPN&algorithm=SHA1&digits=6&period=30` };
  }, { timeout: 10000 });
}
export async function enableAdminMfa(id: string, password: string, code: string) {
  return prisma.$transaction(async tx => {
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`admin-mfa:${id}`}))`;
    const admin = await tx.adminUser.findUniqueOrThrow({ where: { id } });
    if (!await bcrypt.compare(password, admin.passwordHash)) throw new Error('REAUTHENTICATION_REQUIRED');
    if (admin.totpEnabled) throw new Error('TOTP_ALREADY_ENABLED');
    const seed = readTotpSeed(admin.totpSecret);
    if (!seed || seed.expiresAt === undefined || seed.expiresAt <= Date.now()) throw new Error('TOTP_ENROLLMENT_EXPIRED');
    const step = totpStep(seed.secret, code);
    if (step === null) throw new Error('TOTP_INVALID');
    await tx.adminUser.update({ where: { id }, data: { totpEnabled: true, totpLastStep: step } });
    await tx.auditLog.create({ data: { adminId: id, action: 'ENABLE_TOTP', targetResource: 'AUTH' } });
    return { token: jwt.sign({ adminId: id, email: admin.email, role: admin.role,
      mfaVersion: totpVersion(admin.totpSecret) }, JWT_SECRET, { expiresIn: '12h' }) };
  }, { timeout: 10000 });
}
