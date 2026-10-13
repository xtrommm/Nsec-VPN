jest.mock('../secrets', () => ({ JWT_SECRET: require('crypto').randomBytes(32).toString('hex') }));
jest.mock('../db', () => ({ prisma: { $disconnect: jest.fn(), device: { findUnique: jest.fn() }, purchaseClaim: { findUnique: jest.fn() } } }));
jest.mock('../services/bootstrapAdmin', () => ({ bootstrapAdmin: jest.fn().mockResolvedValue(false) }));
jest.mock('../services/nodeHealth', () => ({ refreshNodeHealth: jest.fn().mockResolvedValue(undefined), testNode: jest.fn() }));
import { prisma } from '../db';
import jwt from 'jsonwebtoken';
import { JWT_SECRET } from '../secrets';
let lifecycle: typeof import('../index');
let base: string;
let server: import('http').Server;
beforeAll(async () => {
  const old = process.env.PORT; process.env.PORT = '0'; lifecycle = require('../index');
  server = (await lifecycle.startServer())!;
  if (!server.listening) await new Promise<void>(resolve => server.once('listening', resolve));
  base = 'http://127.0.0.1:' + (server.address() as import('net').AddressInfo).port;
  if (old === undefined) delete process.env.PORT; else process.env.PORT = old;
});
afterAll(async () => { await lifecycle.shutdown(); });
it('sets no-store for API metadata and emits a safe unknown-route envelope', async () => {
  const response = await fetch(base + '/api/v1/does-not-exist');
  expect(response.status).toBe(404); expect(response.headers.get('Cache-Control')).toBe('no-store');
});
it('malformed JSON and oversized payloads use clear safe errors', async () => {
  const invalid = await fetch(base + '/api/v1/auth/register-device-v2', { method: 'POST', headers: {'Content-Type':'application/json'}, body:'{' });
  expect(invalid.status).toBe(400); expect((await invalid.json() as any).error).toBe('INVALID_JSON');
  const huge = await fetch(base + '/api/v1/auth/register-device-v2', { method: 'POST', headers: {'Content-Type':'application/json'}, body:JSON.stringify({unused:'x'.repeat(1024*1024)}) });
  expect(huge.status).toBe(413); expect((await huge.json() as any).error).toBe('PAYLOAD_TOO_LARGE');
});
it('authenticated billing verification has an independent device quota', async () => {
  (prisma.device.findUnique as jest.Mock).mockResolvedValue({id:'fixture-device-id',deviceId:'fixture-installation',tier:'FREE',authEpoch:0,isBlocked:false,passExpiresAt:null});
  const token=jwt.sign({deviceId:'fixture-installation',authEpoch:0},JWT_SECRET);
  let status=0;
  for(let i=0;i<11;i++) {
    const response=await fetch(base+'/api/v1/billing/verify',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},
      body:JSON.stringify({packageName:'com.nsec.vpn',productId:'fixture-vip',purchaseToken:'fixture-receipt-token'})});
    status=response.status;await response.text();
  }
  expect(status).toBe(429);
});
it('all MFA routes reject missing authentication and device bearer tokens',async()=>{
 const token=jwt.sign({deviceId:'fixture-installation',authEpoch:0},JWT_SECRET);
 for(const [path,method] of [['status','GET'],['enrollment','POST'],['enable','PUT']]){
  for(const headers of [{}, {Authorization:'Bearer '+token}] as Record<string,string>[]){
   const response=await fetch(base+'/api/v1/admin/mfa/'+path,{method,headers});
   expect(response.status).toBe(401);await response.text();
  }
 }
});
it('shutdown clears recurring schedules and makes readiness fail closed without querying DB', async () => {
  const clear=jest.spyOn(global,'clearInterval');await lifecycle.shutdown();expect(clear).toHaveBeenCalledTimes(4);
  expect(prisma.$disconnect).toHaveBeenCalledTimes(1);
  const handler=(lifecycle.default as any)._router.stack.find((l:any)=>l.route?.path==='/ready').route.stack[0].handle;
  const res:any={status:jest.fn().mockReturnThis(),json:jest.fn()};await handler({},res);
  expect(res.status).toHaveBeenCalledWith(503);expect(res.json).toHaveBeenCalledWith({status:'DRAINING'});clear.mockRestore();
});
