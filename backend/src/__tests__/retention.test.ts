jest.mock('../db', () => ({prisma:{device:{findMany:jest.fn()},purchaseClaim:{updateMany:jest.fn()},$transaction:jest.fn(),
  refreshToken:{deleteMany:jest.fn()},rewardTicket:{deleteMany:jest.fn()},serverRequest:{deleteMany:jest.fn()},auditLog:{deleteMany:jest.fn()}}}));
import { prisma } from '../db';
import { inactiveDeviceFilter, retentionDays, runRetention } from '../services/retention';
it('uses the owner-selected 90 days and rejects invalid destructive settings', () => {
  expect(retentionDays('90')).toBe(90);
  for (const value of ['0','-1','1.5','NaN','3651']) expect(()=>retentionDays(value)).toThrow('RETENTION_DAYS_INVALID');
});
it('never selects blocked, recoverable, promo-bearing or peer-reserved identities', () => {
  const filter=inactiveDeviceFilter(new Date(0));
  expect(filter).toMatchObject({tier:'FREE',isBlocked:false,recoveryPinHash:null,peerSessions:{none:{}},promoRedemptions:{none:{}}});
});
it('rechecks candidates under locks and never deletes a purchase-bound device', async () => {
  const tx={$executeRaw:jest.fn(),purchaseClaim:{findFirst:jest.fn().mockResolvedValue({tokenHash:'bound'})},device:{deleteMany:jest.fn()}};
  (prisma.device.findMany as jest.Mock).mockResolvedValue([{id:'d',deviceId:'installation'}]);
  (prisma.$transaction as jest.Mock).mockImplementation(async work=>typeof work==='function'?work(tx):Promise.all(work));
  for(const service of [prisma.refreshToken,prisma.rewardTicket,prisma.serverRequest,prisma.auditLog])
    (service.deleteMany as jest.Mock).mockResolvedValue({count:0});
  await runRetention();
  expect(tx.device.deleteMany).not.toHaveBeenCalled();
  expect(tx.$executeRaw).toHaveBeenCalledTimes(2);
});
