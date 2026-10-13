const verify=jest.fn();
jest.mock('googleapis',()=>({google:{auth:{OAuth2:jest.fn().mockImplementation(()=>({verifyIdToken:verify}))}}}));
import { authenticatePlayPush,decodePlayNotification } from '../services/playNotifications';
beforeEach(()=>{jest.clearAllMocks();process.env.PLAY_RTDN_AUDIENCE='https://api.example.org/billing/rtdn';process.env.PLAY_RTDN_SERVICE_ACCOUNT_EMAIL='push@project.iam.gserviceaccount.com';});
afterEach(()=>{delete process.env.PLAY_RTDN_AUDIENCE;delete process.env.PLAY_RTDN_SERVICE_ACCOUNT_EMAIL;});
it('rejects unauthenticated, wrong-account and unverified-email pushes',async()=>{
  await expect(authenticatePlayPush(undefined)).rejects.toThrow('RTDN_UNAUTHORIZED');
  for(const payload of [{iss:'https://accounts.google.com',email:'wrong',email_verified:true},{iss:'https://accounts.google.com',email:process.env.PLAY_RTDN_SERVICE_ACCOUNT_EMAIL,email_verified:false}]) {
    verify.mockResolvedValue({getPayload:()=>payload});
    await expect(authenticatePlayPush('Bearer test-signed-token')).rejects.toThrow('RTDN_UNAUTHORIZED');
  }
});
it('requires Google signature/audience verification and the exact configured identity',async()=>{
  verify.mockResolvedValue({getPayload:()=>({iss:'https://accounts.google.com',email:process.env.PLAY_RTDN_SERVICE_ACCOUNT_EMAIL,email_verified:true})});
  await authenticatePlayPush('Bearer test-signed-token');
  expect(verify).toHaveBeenCalledWith({idToken:'test-signed-token',audience:process.env.PLAY_RTDN_AUDIENCE});
});
it('validates the envelope, package and event data before receipt reconciliation',()=>{
  const encode=(data:unknown)=>({message:{data:Buffer.from(JSON.stringify(data)).toString('base64')}});
  expect(()=>decodePlayNotification(encode({packageName:'another.app',eventTimeMillis:'123',testNotification:{version:'1.0'}}))).toThrow();
  expect(()=>decodePlayNotification({message:{data:'!!!!'}})).toThrow();
  expect(decodePlayNotification(encode({packageName:'com.nsec.vpn',eventTimeMillis:'123',testNotification:{version:'1.0'}})).testNotification).toBeTruthy();
});
