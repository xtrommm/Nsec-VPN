jest.mock('../db', () => ({ prisma: { serverNode: { findMany: jest.fn() } } }));
jest.mock('../middleware/auth', () => ({ authenticateDevice: jest.fn() }));
import router from '../routes/servers';
import { invalidateServerCatalog } from '../services/serverCatalog';
beforeEach(()=>{invalidateServerCatalog();jest.spyOn(Date,'now').mockReturnValue(Date.parse('2026-10-12T18:00:00Z'));});
afterEach(()=>jest.restoreAllMocks());
import { prisma } from '../db';
const handler = (router as any).stack.find((layer: any) => layer.route?.path === '/').route.stack.at(-1).handle;
it('returns the real measurement timestamp in a backward-compatible server response', async () => {
  const stamp = new Date('2026-10-12T18:00:00Z');
  (prisma.serverNode.findMany as jest.Mock).mockResolvedValue([{ id: 'node-test', name: 'Test', city: 'Frankfurt',
    countryCode: 'DE', countryName: 'Germany', currentLoad: 42, isPremiumOnly: false, healthCheckedAt: stamp }]);
  const res = { json: jest.fn(), status: jest.fn() };
  await handler({ device: { tier: 'FREE' }, query: {} }, res);
  const payload = JSON.parse(JSON.stringify(res.json.mock.calls[0][0]));
  expect(payload.data.servers[0].currentLoad).toBe(42);
  expect(payload.data.servers[0].loadUpdatedAt).toBe(stamp.toISOString());
  expect(payload.data.servers[0]).not.toHaveProperty('healthCheckedAt');
  expect(prisma.serverNode.findMany).toHaveBeenCalledWith(expect.objectContaining({select: expect.objectContaining({healthCheckedAt: true})}));
});
it('grouped metadata uses the oldest measurement rather than pretending the whole group is newer', async () => {
  const earlier = new Date('2026-10-12T17:59:40Z');
  (prisma.serverNode.findMany as jest.Mock).mockResolvedValue([
    {id:'test-a',name:'A',city:'Frankfurt',countryCode:'DE',isPremiumOnly:false,currentLoad:20,healthCheckedAt:earlier},
    {id:'test-b',name:'B',city:'Frankfurt',countryCode:'DE',isPremiumOnly:false,currentLoad:80,healthCheckedAt:new Date('2026-10-12T18:00:00Z')}
  ]);
  const res = { json: jest.fn(), status: jest.fn() };
  await handler({device:{tier:'FREE'},query:{grouped:'true'}},res);
  expect(res.json.mock.calls[0][0].data.servers[0]).toMatchObject({currentLoad:50,loadUpdatedAt:earlier.toISOString()});
});
it('never shares device premium filtering through the catalog cache',async()=>{
 (prisma.serverNode.findMany as jest.Mock).mockClear().mockResolvedValue([
  {id:'free',city:'Frankfurt',countryCode:'DE',isPremiumOnly:false,healthCheckedAt:new Date(Date.now())},
  {id:'vip',city:'Frankfurt',countryCode:'DE',isPremiumOnly:true,healthCheckedAt:new Date(Date.now())}
 ]);
 const free={json:jest.fn(),status:jest.fn()},vip={json:jest.fn(),status:jest.fn()};
 await handler({device:{tier:'FREE'},query:{includePremium:'false'}},free);
 await handler({device:{tier:'VIP'},query:{}},vip);
 expect(free.json.mock.calls[0][0].data.servers.map((x:any)=>x.id)).toEqual(['free']);
 expect(vip.json.mock.calls[0][0].data.servers.map((x:any)=>x.id)).toEqual(['free','vip']);
 expect(prisma.serverNode.findMany).toHaveBeenCalledTimes(1);
});
