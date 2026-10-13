jest.mock('../db',()=>({prisma:{purchaseClaim:{findUnique:jest.fn(),findMany:jest.fn()},$transaction:jest.fn()}}));
jest.mock('../services/billing',()=>({PlayBillingService:{verifySubscription:jest.fn()}}));
import crypto from 'crypto';
import { prisma } from '../db';
import { PlayBillingService } from '../services/billing';
import { applyVerifiedPurchase,decryptPurchaseToken,effectivePurchaseEntitlement,encryptPurchaseToken,purchaseHash,reconcilePurchase } from '../services/purchaseReconciliation';
const tx={$executeRaw:jest.fn(),purchaseClaim:{findUnique:jest.fn(),upsert:jest.fn(),findMany:jest.fn(),update:jest.fn()},
  device:{findUnique:jest.fn(),findUniqueOrThrow:jest.fn(),update:jest.fn()},peerSession:{updateMany:jest.fn()}};
const until=new Date(Date.now()+86400000),promoUntil=new Date(Date.now()+2*86400000);
const token='test-receipt-'+crypto.randomBytes(24).toString('hex');
const claim={tokenHash:purchaseHash(token),deviceId:'d1',expiresAt:until,active:true,adminRevoked:false,verifiedAt:null,
  productId:'premium',purchaseTokenCiphertext:null,protectedVipUntil:null,protectedVipIndefinite:false,provenanceInitialized:true};
beforeEach(()=>{
  jest.clearAllMocks();process.env.PLAY_TOKEN_ENCRYPTION_KEY=crypto.randomBytes(32).toString('hex');
  (prisma.$transaction as jest.Mock).mockImplementation(work=>work(tx));
  (prisma.purchaseClaim.findUnique as jest.Mock).mockResolvedValue(claim);
  tx.purchaseClaim.findUnique.mockResolvedValue(claim);
  tx.device.findUnique.mockResolvedValue({id:'d1',deviceId:'installation'});
  tx.device.findUniqueOrThrow.mockResolvedValue({id:'d1',deviceId:'installation',isBlocked:false,tier:'VIP',passExpiresAt:until});
  tx.purchaseClaim.findMany.mockResolvedValue([{...claim,active:false}]);
  (PlayBillingService.verifySubscription as jest.Mock).mockResolvedValue({isValid:false,tier:'FREE',state:'REFUNDED'});
});
afterEach(()=>{delete process.env.PLAY_TOKEN_ENCRYPTION_KEY;});
it('stores authenticated ciphertext, detects tampering and fails without its dedicated key',()=>{
  const text=encryptPurchaseToken(token);expect(text).not.toContain(token);expect(decryptPurchaseToken(text)).toBe(token);
  const bytes=Buffer.from(text.slice(3),'base64');bytes[30]^=1;
  expect(()=>decryptPurchaseToken('v1:'+bytes.toString('base64'))).toThrow();
  delete process.env.PLAY_TOKEN_ENCRYPTION_KEY;expect(()=>encryptPurchaseToken(token)).toThrow('BILLING_TOKEN_ENCRYPTION_REQUIRED');
});
it('a revoked purchase removes only its own contribution and preserves unrelated grants',()=>{
  expect(effectivePurchaseEntitlement([{...claim,active:false}]).tier).toBe('FREE');
  expect(effectivePurchaseEntitlement([{...claim,active:false,protectedVipUntil:promoUntil}])).toEqual({tier:'VIP',passExpiresAt:promoUntil});
  expect(effectivePurchaseEntitlement([{...claim,active:false,protectedVipIndefinite:true}])).toEqual({tier:'VIP',passExpiresAt:null});
  expect(effectivePurchaseEntitlement([{...claim,active:true},{...claim,active:false}]).tier).toBe('VIP');
});
it('refund reconciliation expires premium peers only when no other VIP grant remains',async()=>{
  await reconcilePurchase(claim.tokenHash,token);
  expect(tx.device.update).toHaveBeenCalledWith({where:{id:'d1'},data:{tier:'FREE',passExpiresAt:null}});
  expect(tx.peerSession.updateMany).toHaveBeenCalled();
  jest.clearAllMocks();tx.purchaseClaim.findMany.mockResolvedValue([{...claim,active:false,protectedVipUntil:promoUntil}]);
  await reconcilePurchase(claim.tokenHash,token);
  expect(tx.device.update).toHaveBeenCalledWith({where:{id:'d1'},data:{tier:'VIP',passExpiresAt:promoUntil}});
  expect(tx.peerSession.updateMany).toHaveBeenCalledWith({where:{deviceId:'d1',server:{isPremiumOnly:true},expiresAt:{gt:promoUntil}},data:{expiresAt:promoUntil}});
});
it('rejects cross-device receipt reuse and ignores older verification responses',async()=>{
  await expect(applyVerifiedPurchase('other','premium',token,{isValid:true,tier:'VIP',state:'ACTIVE',expiryTimeMs:until.getTime()})).rejects.toThrow('PURCHASE_ALREADY_BOUND');
  expect(tx.purchaseClaim.upsert).not.toHaveBeenCalled();
  tx.purchaseClaim.findUnique.mockResolvedValue({...claim,verifiedAt:new Date(Date.now()+1000)});
  await applyVerifiedPurchase('d1','premium',token,{isValid:false,tier:'FREE',state:'REFUNDED'},new Date(0));
  expect(tx.purchaseClaim.upsert).not.toHaveBeenCalled();
});
it('does not convert an existing paid expiry into a protected grant when adding another receipt',async()=>{
  tx.purchaseClaim.findUnique.mockResolvedValue(null);tx.purchaseClaim.findMany.mockResolvedValue([claim]);
  await applyVerifiedPurchase('d1','premium',token,{isValid:true,tier:'VIP',state:'ACTIVE',expiryTimeMs:until.getTime()});
  expect(tx.purchaseClaim.upsert).toHaveBeenCalledWith(expect.objectContaining({create:expect.objectContaining({protectedVipUntil:null,protectedVipIndefinite:false})}));
});
it('a provider outage never overwrites a valid entitlement',async()=>{
  (PlayBillingService.verifySubscription as jest.Mock).mockRejectedValue(new Error('unavailable'));
  await expect(reconcilePurchase(claim.tokenHash,token)).rejects.toThrow('unavailable');
  expect(tx.device.update).not.toHaveBeenCalled();
});
