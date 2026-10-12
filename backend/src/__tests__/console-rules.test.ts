jest.mock('../db', () => ({ prisma: { $transaction: jest.fn() } }));
import crypto from 'crypto';
import { prisma } from '../db';
import { encryptPromo, decryptPromo } from '../services/promoCipher';
import { CreatePromo, BulkPromo, promoHash, promoStatus, promoUpdate } from '../services/promotions';
import { PageQuery } from '../services/consoleQuery';
import { ServerRequestInput, submitServerRequest } from '../services/serverRequests';
const tx = { $executeRaw: jest.fn(), device: { findUniqueOrThrow: jest.fn() }, serverRequest: { findFirst: jest.fn(), create: jest.fn() } };
beforeEach(() => { jest.clearAllMocks();process.env.PROMO_CODE_ENCRYPTION_KEY=crypto.randomBytes(32).toString('hex');(prisma.$transaction as jest.Mock).mockImplementation(work=>work(tx));tx.device.findUniqueOrThrow.mockResolvedValue({deviceId:'installation-test',isBlocked:false,appVersion:'2'});tx.serverRequest.findFirst.mockResolvedValue(null);tx.serverRequest.create.mockImplementation(({data})=>Promise.resolve(data)); });
afterAll(()=>delete process.env.PROMO_CODE_ENCRYPTION_KEY);
it('encrypts retrievable codes with authenticated randomized encryption',()=>{
 const first=encryptPromo('TEST-CODE');const second=encryptPromo('TEST-CODE');expect(first).not.toBe(second);expect(first).not.toContain('TEST-CODE');expect(decryptPromo(first)).toBe('TEST-CODE');
 const parts=first.split('.');const data=Buffer.from(parts[3],'base64');data[0]^=1;parts[3]=data.toString('base64');expect(()=>decryptPromo(parts.join('.'))).toThrow('PROMO_CODE_UNAVAILABLE');
});
it('fails closed without a configured code encryption key',()=>{delete process.env.PROMO_CODE_ENCRYPTION_KEY;expect(()=>encryptPromo('TEST-CODE')).toThrow('PROMO_ENCRYPTION_NOT_CONFIGURED');});
it('requires whole days for new consoles and preserves exact legacy minutes',()=>{
 expect(CreatePromo.parse({label:'Test',passDays:2,maxRedemptions:1}).passDays).toBe(2);
 expect(CreatePromo.parse({label:'Test',passMinutes:30,maxRedemptions:1}).passMinutes).toBe(30);
 expect(()=>CreatePromo.parse({label:'Test',passDays:0.5,maxRedemptions:1})).toThrow();
 expect(()=>CreatePromo.parse({label:'Test',passDays:2,passMinutes:30,maxRedemptions:1})).toThrow();
 expect(()=>BulkPromo.parse({label:'Test',passDays:1,maxRedemptions:1,count:101})).toThrow();
});
it('rejects inverted campaign schedules',()=>{expect(()=>CreatePromo.parse({label:'Test',passDays:1,maxRedemptions:1,startsAt:'2030-01-02T00:00:00Z',expiresAt:'2030-01-01T00:00:00Z'})).toThrow();});
it('enforces update invariants against existing usage and immutable code',()=>{
 const existing={codeHash:promoHash('TEST-CODE'),redemptionCount:3,startsAt:null,expiresAt:null};
 expect(()=>promoUpdate(existing,{maxRedemptions:2})).toThrow('PROMO_LIMIT_BELOW_USAGE');
 expect(()=>promoUpdate(existing,{code:'OTHER-CODE'})).toThrow('PROMO_CODE_IMMUTABLE');
 expect(promoUpdate(existing,{code:'test-code',label:'Revised'})).toEqual({label:'Revised'});
 expect(promoUpdate(existing,{passDays:3})).toEqual({passDays:3,passMinutes:4320});
 expect(promoUpdate(existing,{active:false})).toEqual({active:false});
});
it('classifies status from actual dates, capacity and tombstones',()=>{
 const base={active:true,expiresAt:null,startsAt:null,redemptionCount:0,maxRedemptions:1};
 expect(promoStatus(base)).toBe('active');expect(promoStatus({...base,startsAt:new Date(Date.now()+60000)})).toBe('scheduled');expect(promoStatus({...base,deletedAt:new Date()})).toBe('deleted');expect(promoStatus({...base,redemptionCount:1})).toBe('used-up');expect(promoStatus({...base,expiresAt:new Date(0)})).toBe('expired');
});
it('validates locations and refuses a caller-provided owner or status',()=>{
 expect(ServerRequestInput.parse({country:'Bangladesh',city:'Dhaka',note:'  Please add a node  '})).toEqual({country:'Bangladesh',city:'Dhaka',note:'Please add a node'});
 for(const payload of [{country:'',city:'Dhaka'},{country:'<script>',city:'Dhaka'},{country:'Bangladesh',city:'Dhaka',deviceId:'other-device'},{country:'Bangladesh',city:'Dhaka',status:'DONE'}])expect(()=>ServerRequestInput.parse(payload)).toThrow();
});
it('locks request identity, binds app version from the registered device, and saves pending',async()=>{
 const r=await submitServerRequest('owned-device',{country:'Bangladesh',city:'Dhaka'});
 expect(tx.$executeRaw.mock.invocationCallOrder[0]).toBeLessThan(tx.serverRequest.create.mock.invocationCallOrder[0]);expect(r.request).toEqual(expect.objectContaining({deviceId:'owned-device',appVersion:'2',status:'PENDING'}));
});
it('enforces persistent cooldown without creating another request',async()=>{
 tx.serverRequest.findFirst.mockResolvedValue({createdAt:new Date(Date.now()-10000)});const r=await submitServerRequest('owned-device',{country:'Bangladesh',city:'Dhaka'});expect(r.request).toBeNull();expect(r.retryAfterSeconds).toBeGreaterThan(580);expect(tx.serverRequest.create).not.toHaveBeenCalled();
});
it('rejects a blocked device before storing a request',async()=>{
 tx.device.findUniqueOrThrow.mockResolvedValue({isBlocked:true});await expect(submitServerRequest('blocked-device',{country:'Bangladesh',city:'Dhaka'})).rejects.toThrow('DEVICE_BLOCKED');expect(tx.serverRequest.create).not.toHaveBeenCalled();
});
it('bounds paginated queries and rejects repeated arrays or malformed pages',()=>{
 expect(PageQuery.parse({})).toEqual({page:1,limit:20,search:'',sort:'newest'});
 for(const q of [{page:'-1'},{limit:'101'},{search:['a','b']},{sort:'invalid'}])expect(()=>PageQuery.parse(q)).toThrow();
});
