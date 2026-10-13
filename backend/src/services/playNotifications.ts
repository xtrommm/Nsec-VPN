import { z } from 'zod';
import { purchaseHash, reconcilePurchase } from './purchaseReconciliation';

const notification=z.object({packageName:z.literal('com.nsec.vpn'),eventTimeMillis:z.string().regex(/^[0-9]{1,16}$/),
  subscriptionNotification:z.object({purchaseToken:z.string().min(10).max(16384),notificationType:z.number().int().positive()}).passthrough().optional(),
  voidedPurchaseNotification:z.object({purchaseToken:z.string().min(10).max(16384)}).passthrough().optional(),
  testNotification:z.object({version:z.string()}).optional()}).passthrough();

export function decodePlayNotification(body:unknown) {
  const envelope=z.object({message:z.object({data:z.string().min(1).max(100000),messageId:z.string().min(1).max(256).optional()}).passthrough()}).passthrough().parse(body);
  const text=envelope.message.data;
  if(!/^[A-Za-z0-9+/]*={0,2}$/.test(text)||text.length%4!==0) throw new Error('RTDN_PAYLOAD_INVALID');
  const decoded=Buffer.from(text,'base64');
  if(decoded.toString('base64')!==text) throw new Error('RTDN_PAYLOAD_INVALID');
  const data=notification.parse(JSON.parse(decoded.toString('utf8')));
  if(!data.testNotification&&!data.subscriptionNotification&&!data.voidedPurchaseNotification) throw new Error('RTDN_PAYLOAD_INVALID');
  return data;
}

export async function authenticatePlayPush(authorization:string|undefined) {
  const audience=process.env.PLAY_RTDN_AUDIENCE;
  const email=process.env.PLAY_RTDN_SERVICE_ACCOUNT_EMAIL;
  if(!audience||!email) throw new Error('RTDN_NOT_CONFIGURED');
  if(!authorization?.startsWith('Bearer ')) throw new Error('RTDN_UNAUTHORIZED');
  const {google}=await import('googleapis');
  const ticket=await new google.auth.OAuth2().verifyIdToken({idToken:authorization.slice(7),audience});
  const payload=ticket.getPayload();
  if(!payload||payload.email!==email||payload.email_verified!==true||
    !['accounts.google.com','https://accounts.google.com'].includes(payload.iss)) throw new Error('RTDN_UNAUTHORIZED');
}

export async function handlePlayNotification(body:unknown) {
  const data=decodePlayNotification(body);
  if(data.testNotification) return;
  const token=data.subscriptionNotification?.purchaseToken??data.voidedPurchaseNotification!.purchaseToken;
  // Notifications do not grant access. Google Play is queried for the current state,
  // so replayed and out-of-order pushes cannot extend or revive a subscription.
  await reconcilePurchase(purchaseHash(token),token);
}
