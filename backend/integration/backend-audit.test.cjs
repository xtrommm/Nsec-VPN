const {test,before,after}=require('node:test');
const assert=require('node:assert/strict');
const crypto=require('node:crypto');
const database=new URL(process.env.DATABASE_URL||'file:invalid').pathname;
if(!/(test|_ci)/.test(database))throw new Error('Use a dedicated test database');
const {prisma}=require('../dist/db');
const {redeemPromotion,promoHash,archivedPromoHash}=require('../dist/services/promotions');
const {consumeAdminTotp}=require('../dist/services/adminMfa');
const {rotateRefreshToken}=require('../dist/services/refreshTokens');
const {encryptTotpSeed}=require('../dist/services/totp');
const tag=crypto.randomUUID();let devices=[],promo,admin;
const code='TEST-'+tag.replace(/-/g,'').slice(0,24).toUpperCase();
// Validate fixture before writing any isolated database rows.
promoHash(code);
before(async()=>{
 devices=await Promise.all([0,1].map(i=>prisma.device.create({data:{deviceId:`test-${tag}-${i}`}})));
 promo=await prisma.promoCode.create({data:{codeHash:promoHash(code),label:'Isolated audit test',passMinutes:1440,passDays:1,maxRedemptions:1}});
 process.env.ADMIN_TOTP_ENCRYPTION_KEY=crypto.randomBytes(32).toString('hex');
 admin=await prisma.adminUser.create({data:{email:`${tag}@test.invalid`,passwordHash:'unused-test-password-hash',totpEnabled:true,
  totpSecret:encryptTotpSeed('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ',0)}});
});
test('real PostgreSQL serializes last-use redemption across devices',async()=>{
 const results=await Promise.allSettled(devices.map(d=>redeemPromotion(d.id,code)));
 assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
 assert.match(results.find(x=>x.status==='rejected').reason.message,/PROMO_EXHAUSTED/);
 assert.equal((await prisma.promoCode.findUniqueOrThrow({where:{id:promo.id}})).redemptionCount,1);
 assert.equal(await prisma.promoRedemption.count({where:{promoId:promo.id}}),1);
});
test('archiving a deleted campaign frees its unique code while retaining its redemption',async()=>{
 await prisma.promoCode.update({where:{id:promo.id},data:{active:false,deletedAt:new Date(),codeHash:archivedPromoHash(promo.id)}});
 const replacement=await prisma.promoCode.create({data:{codeHash:promoHash(code),label:'Replacement campaign',passMinutes:1440,passDays:1,maxRedemptions:1}});
 assert.notEqual(replacement.id,promo.id);assert.equal(await prisma.promoRedemption.count({where:{promoId:promo.id}}),1);
 await prisma.promoCode.delete({where:{id:replacement.id}});
});
test('only one concurrent authenticator use succeeds under the database lock',async()=>{
 const now=Date.now;Date.now=()=>59000;
 try{
  const results=await Promise.allSettled([consumeAdminTotp(admin.id,'287082'),consumeAdminTotp(admin.id,'287082')]);
  assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
  assert.equal((await prisma.adminUser.findUniqueOrThrow({where:{id:admin.id}})).totpLastStep,1);
 }finally{Date.now=now;}
});
test('simultaneous refresh reuse commits revocation of the newly issued credential',async()=>{
 const raw=crypto.randomBytes(32).toString('hex');
 await prisma.refreshToken.create({data:{deviceId:devices[0].id,tokenHash:crypto.createHash('sha256').update(raw).digest('hex'),expiresAt:new Date(Date.now()+60000)}});
 const results=await Promise.all([rotateRefreshToken(raw),rotateRefreshToken(raw)]);
 assert.equal(results.filter(x=>x.data).length,1);assert.ok(results.some(x=>x.error==='TOKEN_COMPROMISED'));
 assert.equal((await prisma.device.findUniqueOrThrow({where:{id:devices[0].id}})).authEpoch,1);
 assert.equal(await prisma.refreshToken.count({where:{deviceId:devices[0].id,revoked:false}}),0);
});
after(async()=>{
 await prisma.promoRedemption.deleteMany({where:{deviceId:{in:devices.map(d=>d.id)}}});
 if(promo)await prisma.promoCode.deleteMany({where:{id:promo.id}});
 await prisma.refreshToken.deleteMany({where:{deviceId:{in:devices.map(d=>d.id)}}});
 await prisma.device.deleteMany({where:{id:{in:devices.map(d=>d.id)}}});
 if(admin)await prisma.adminUser.deleteMany({where:{id:admin.id}});
 await prisma.$disconnect();
});
