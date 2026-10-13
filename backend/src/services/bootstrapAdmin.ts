import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { prisma } from '../db';

export async function bootstrapAdmin() {
  return prisma.$transaction(async tx => {
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${'admin-bootstrap'}))`;
    if (await tx.adminUser.count()) return false;
    const email = z.string().email().parse(process.env.ADMIN_EMAIL);
    const password = z.string().min(12).max(128).parse(process.env.ADMIN_PASSWORD);
    await tx.adminUser.create({ data: { email, passwordHash: await bcrypt.hash(password, 12), role: 'SUPER_ADMIN' } });
    return true;
  }, { timeout: 10000 });
}
