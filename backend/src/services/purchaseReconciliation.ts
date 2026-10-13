import crypto from 'crypto';
import { Prisma } from '@prisma/client';
import { prisma } from '../db';
import { PlayBillingService, SubscriptionStatus } from './billing';

export const purchaseHash = (token: string) => crypto.createHash('sha256').update(token).digest('hex');
function tokenKey() {
  const key = process.env.PLAY_TOKEN_ENCRYPTION_KEY;
  if (!key || !/^[a-f0-9]{64}$/i.test(key)) throw new Error('BILLING_TOKEN_ENCRYPTION_REQUIRED');
  return Buffer.from(key, 'hex');
}
export function encryptPurchaseToken(token: string): string {
  const iv=crypto.randomBytes(12), cipher=crypto.createCipheriv('aes-256-gcm',tokenKey(),iv);
  const body=Buffer.concat([cipher.update(token,'utf8'),cipher.final()]);
  return 'v1:'+Buffer.concat([iv,cipher.getAuthTag(),body]).toString('base64');
}
export function decryptPurchaseToken(text: string): string {
  if(!text.startsWith('v1:')) throw new Error('BILLING_RECEIPT_INVALID');
  const bytes=Buffer.from(text.slice(3),'base64');
  if(bytes.length<29) throw new Error('BILLING_RECEIPT_INVALID');
  const decipher=crypto.createDecipheriv('aes-256-gcm',tokenKey(),bytes.subarray(0,12));
  decipher.setAuthTag(bytes.subarray(12,28));
  return Buffer.concat([decipher.update(bytes.subarray(28)),decipher.final()]).toString('utf8');
}

export function effectivePurchaseEntitlement(claims: {active:boolean;adminRevoked:boolean;expiresAt:Date;
  protectedVipUntil:Date|null;protectedVipIndefinite:boolean}[], now = new Date()) {
  if(claims.some(c=>c.protectedVipIndefinite)) return {tier:'VIP' as const,passExpiresAt:null};
  const dates=claims.flatMap(c=>[c.protectedVipUntil,...(c.active&&!c.adminRevoked?[c.expiresAt]:[])])
    .filter((d):d is Date=>!!d&&d>now);
  return dates.length?{tier:'VIP' as const,passExpiresAt:new Date(Math.max(...dates.map(d=>d.getTime())))}:
    {tier:'FREE' as const,passExpiresAt:null};
}

export async function preserveNonPurchaseGrant(tx: Prisma.TransactionClient, deviceId:string, until:Date|null) {
  // Called under the same device reward lock as all entitlement writers.
  if(until===null) await tx.purchaseClaim.updateMany({where:{deviceId},data:{protectedVipIndefinite:true}});
  else await tx.purchaseClaim.updateMany({where:{deviceId,OR:[{protectedVipUntil:null},{protectedVipUntil:{lt:until}}]},data:{protectedVipUntil:until}});
}

async function boundPremiumPeers(tx:Prisma.TransactionClient,deviceId:string,entitlement:{tier:string;passExpiresAt:Date|null}) {
  const expiry=entitlement.tier==='FREE'?new Date():entitlement.passExpiresAt;
  if(expiry) await tx.peerSession.updateMany({where:{deviceId,server:{isPremiumOnly:true},expiresAt:{gt:expiry}},data:{expiresAt:expiry}});
}

export async function applyVerifiedPurchase(deviceId:string, productId:string, token:string,
  status:SubscriptionStatus, startedAt = new Date()) {
  const tokenHash=purchaseHash(token), ciphertext=encryptPurchaseToken(token);
  return prisma.$transaction(async tx=>{
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${tokenHash}))`;
    const existing=await tx.purchaseClaim.findUnique({where:{tokenHash}});
    if(existing&&existing.deviceId!==deviceId) throw new Error('PURCHASE_ALREADY_BOUND');
    if(existing?.productId&&existing.productId!==productId) throw new Error('PURCHASE_PRODUCT_MISMATCH');
    const initial=await tx.device.findUniqueOrThrow({where:{id:deviceId}});
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${initial.deviceId}`}))`;
    const device=await tx.device.findUniqueOrThrow({where:{id:deviceId}});
    if(device.isBlocked) throw new Error('DEVICE_BLOCKED');
    if(existing?.verifiedAt&&existing.verifiedAt>startedAt) return {tier:device.tier,passExpiresAt:device.passExpiresAt};
    const priorClaims=await tx.purchaseClaim.findMany({where:{deviceId}});
    const protectedDates=priorClaims.map(c=>c.protectedVipUntil).filter((d):d is Date=>!!d);
    const legacy=existing&&!existing.provenanceInitialized;
    const protectedUntil=!legacy&&priorClaims.length?(protectedDates.length?new Date(Math.max(...protectedDates.map(d=>d.getTime()))):null):
      device.tier!=='FREE'?device.passExpiresAt:null;
    const protectedIndefinite=!legacy&&priorClaims.length?priorClaims.some(c=>c.protectedVipIndefinite):
      device.tier==='VIP'&&device.passExpiresAt===null;
    const expiry=new Date(status.expiryTimeMs??0);
    await tx.purchaseClaim.upsert({where:{tokenHash},create:{tokenHash,deviceId,productId,purchaseTokenCiphertext:ciphertext,
      expiresAt:expiry,active:status.isValid,verifiedAt:startedAt,provenanceInitialized:true,
      protectedVipUntil:protectedUntil,protectedVipIndefinite:protectedIndefinite},
      update:{productId,purchaseTokenCiphertext:ciphertext,expiresAt:expiry,active:status.isValid,adminRevoked:false,verifiedAt:startedAt,
        ...(legacy?{provenanceInitialized:true,protectedVipUntil:protectedUntil,protectedVipIndefinite:protectedIndefinite}:{})}});
    const claims=await tx.purchaseClaim.findMany({where:{deviceId}});
    const entitlement=effectivePurchaseEntitlement(claims);
    await boundPremiumPeers(tx,deviceId,entitlement);
    return tx.device.update({where:{id:deviceId},data:entitlement,select:{tier:true,passExpiresAt:true}});
  },{timeout:10000});
}

export async function reconcilePurchase(tokenHash:string, suppliedToken?:string) {
  const existing=await prisma.purchaseClaim.findUnique({where:{tokenHash}});
  if(!existing||existing.adminRevoked||!existing.productId||!existing.provenanceInitialized) return;
  const token=suppliedToken??(existing.purchaseTokenCiphertext?decryptPurchaseToken(existing.purchaseTokenCiphertext):null);
  if(!token||purchaseHash(token)!==tokenHash) throw new Error('BILLING_RECEIPT_INVALID');
  const startedAt=new Date();
  const status=await PlayBillingService.verifySubscription('com.nsec.vpn',existing.productId,token);
  await prisma.$transaction(async tx=>{
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${tokenHash}))`;
    const current=await tx.purchaseClaim.findUnique({where:{tokenHash}});
    if(!current||current.adminRevoked||(current.verifiedAt&&current.verifiedAt>startedAt)) return;
    const device=await tx.device.findUnique({where:{id:current.deviceId}});
    if(!device) return;
    await tx.$executeRaw`SELECT pg_advisory_xact_lock(hashtext(${`reward:${device.deviceId}`}))`;
    await tx.purchaseClaim.update({where:{tokenHash},data:{active:status.isValid,
      expiresAt:new Date(status.expiryTimeMs??0),verifiedAt:startedAt}});
    const claims=await tx.purchaseClaim.findMany({where:{deviceId:current.deviceId}});
    const entitlement=effectivePurchaseEntitlement(claims);
    await tx.device.update({where:{id:current.deviceId},data:entitlement});
    await boundPremiumPeers(tx,current.deviceId,entitlement);
  },{timeout:10000});
}

let checking=false;
export async function reconcilePurchases() {
  if(checking||!process.env.PLAY_TOKEN_ENCRYPTION_KEY||!process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) return;
  checking=true;
  try {
    const before=new Date(Date.now()-5*60000), recent=new Date(Date.now()-30*86400000);
    const claims=await prisma.purchaseClaim.findMany({where:{adminRevoked:false,purchaseTokenCiphertext:{not:null},
      productId:{not:null},expiresAt:{gt:recent},OR:[{verifiedAt:null},{verifiedAt:{lt:before}}]},
      orderBy:{verifiedAt:'asc'},take:20,select:{tokenHash:true}});
    let failed=0;
    for(const claim of claims) {
      try { await reconcilePurchase(claim.tokenHash); }
      catch { failed++; }
    }
    if(failed) throw new Error('BILLING_RECONCILIATION_PARTIAL_FAILURE');
  } finally { checking=false; }
}
