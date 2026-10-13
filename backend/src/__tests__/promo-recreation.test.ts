jest.mock('../secrets', () => ({ JWT_SECRET: require('crypto').randomBytes(32).toString('hex') }));
jest.mock('../db', () => ({ prisma: { $transaction: jest.fn() } }));
import crypto from 'crypto';
import { prisma } from '../db';
import { adminPromos } from '../routes/promos';
import { archivedPromoHash, promoHash } from '../services/promotions';
const route = (method: string) => (adminPromos as any).stack.find((l: any) => l.route?.path === (method === 'delete' ? '/:id' : '/') && l.route.methods[method]).route.stack.at(-1).handle;
const res = () => { const r: any = { code: 200 }; r.status = (code: number) => { r.code = code; return r; }; r.json = (body: unknown) => { r.body = body; return r; }; return r; };
it('reuses deleted text as a new campaign without deleting original history; live duplicates still fail', async () => {
  process.env.PROMO_CODE_ENCRYPTION_KEY = crypto.randomBytes(32).toString('hex');
  const rows: any[] = [];
  const tx: any = { $executeRaw: jest.fn(), auditLog: { create: jest.fn() }, promoCode: {
    findUnique: jest.fn(({ where }) => rows.find(r => r.codeHash === where.codeHash)),
    findUniqueOrThrow: jest.fn(({ where }) => rows.find(r => r.id === where.id)),
    create: jest.fn(({ data }) => { const row = { ...data, id: 'campaign-' + rows.length, redemptionCount: 0, deletedAt: null }; rows.push(row); return row; }),
    update: jest.fn(({ where, data }) => { const row = rows.find(r => r.id === where.id); Object.assign(row, data); return row; }) } };
  (prisma.$transaction as jest.Mock).mockImplementation(fn => fn(tx));
  const request: any = { admin: { adminId: 'fixture-admin' }, body: { code: 'FIXTURE-CODE', label: 'Fixture', passDays: 1, maxRedemptions: 1 } };
  const first = res(); await route('post')(request, first); expect(first.code).toBe(201);
  const duplicate = res(); await route('post')(request, duplicate); expect(duplicate.code).toBe(409);
  await route('delete')({ admin: request.admin, params: { id: rows[0].id } }, res());
  expect(rows[0].codeHash).toBe(archivedPromoHash(rows[0].id)); expect(rows[0].deletedAt).toBeTruthy();
  const second = res(); await route('post')(request, second); expect(second.code).toBe(201);
  expect(rows).toHaveLength(2); expect(rows[1].codeHash).toBe(promoHash('FIXTURE-CODE'));
  rows[1].deletedAt = new Date(); // A legacy tombstone with the original hash is also migrated on create.
  const third = res(); await route('post')(request, third); expect(third.code).toBe(201); expect(rows).toHaveLength(3);
  delete process.env.PROMO_CODE_ENCRYPTION_KEY;
});
