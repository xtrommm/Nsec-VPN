jest.mock('../secrets', () => ({ JWT_SECRET: require('crypto').randomBytes(32).toString('hex') }));
jest.mock('../db', () => ({ prisma: {} }));
jest.mock('../services/rewardVerification', () => ({ verifyReward: jest.fn() }));
import billing from '../routes/billing';
import { verifyReward } from '../services/rewardVerification';
it('transient verification outages remain retryable; invalid signed events stay rejected',async()=>{
 const handler=(billing as any).stack.find((l:any)=>l.route?.path==='/rewarded/ssv').route.stack.at(-1).handle;
 for(const [error,status] of [['SSV_KEYS_UNAVAILABLE',503],['SSV_NOT_CONFIGURED',503],['SSV_SIGNATURE_INVALID',400],['SSV_EXPIRED',400]] as const){
  (verifyReward as jest.Mock).mockRejectedValue(new Error(error));const res:any={code:0,status(v:number){this.code=v;return this;},json:jest.fn()};
  await handler({originalUrl:'/rewarded/ssv?fixture'},res);expect(res.code).toBe(status);
 }
});
