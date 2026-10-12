// Test fixtures only: intercepted HTTP responses never enter a production bundle.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_BINARY,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const writes=[];const now=new Date().toISOString();
 const config={id:'global_config',updatedAt:now,minAppVersionCode:1,latestAppVersionCode:2,forceUpdate:false,maintenanceMode:false,announcementActive:false,announcementTitle:null,announcementBody:null,bannerAdsEnabled:false,nativeAdsEnabled:false,interstitialAdsEnabled:false,rewardedAdsEnabled:false,interstitialIntervalSeconds:120,rewardedPassMinutes:30,bannerAdUnitId:null,nativeAdUnitId:null,interstitialAdUnitId:null,rewardedAdUnitId:null,adsEnabled:true,supportEmail:null};
 const node={id:'af8f6a42-0fd5-494f-b874-355b5579c11e',name:'Test node',countryCode:'US',countryName:'United States',city:'Virginia',flagEmoji:'',endpoint:'47.85.109.170:51820',publicKey:'QkKFJCMH/g0/d0rdvzrj50qViD0IrLs07GI5FM3RCHg=',agentUrl:'http://127.0.0.1:51821',subnetIpv4:'10.8.0.0/24',dnsServers:['1.1.1.1'],mtu:1420,maxPeers:250,currentLoad:4,isPremiumOnly:false,isRecommended:true,active:true,healthy:true,healthCheckedAt:now,publicIpv4:'47.85.109.170',sortOrder:0};
 const pagination={total:0,totalPages:1,page:1,limit:20};
 await page.route('**/api/v1/admin/**',async route=>{
  const req=route.request();const path=new URL(req.url()).pathname.replace('/api/v1/admin','');const body=req.postDataJSON();if(req.method()!=='GET')writes.push({path,body});let result={success:true};
  if(path==='/login')Object.assign(result,{token:'test-only-session',admin:{email:'test@example.org',role:'SUPER_ADMIN'}});
  else if(path==='/me')Object.assign(result,{admin:{email:'test@example.org',role:'SUPER_ADMIN'}});
  else if(path==='/servers')Object.assign(result,{servers:[node]});
  else if(path==='/stats')Object.assign(result,{data:{activeTunnels:1,totalDevices:10,vipSubscribers:2,totalServers:1,avgServerLoad:4}});
  else if(path==='/config'){if(req.method()==='PUT')Object.assign(config,body);Object.assign(result,{config});}
  else if(path==='/promos/bulk')Object.assign(result,{promos:[{code:'TEST-CODE-A'},{code:'TEST-CODE-B'}]});
  else if(path==='/promos')Object.assign(result,{promos:[],pagination});
  else if(path==='/devices')Object.assign(result,{devices:[],pagination});
  else if(path==='/server-requests')Object.assign(result,{requests:[],pagination});
  else if(path==='/audit-logs')Object.assign(result,{logs:[],pagination});
  await route.fulfill({json:result});
 });
 await page.goto('http://127.0.0.1:4173');await page.getByLabel('Email',{exact:true}).fill('test@example.org');await page.getByLabel('Password',{exact:true}).fill('test-only-password');await page.getByRole('button',{name:'Sign in securely'}).click();await page.getByRole('heading',{name:'Keep everything connected.'}).waitFor();
 await page.screenshot({path:process.env.SHOT_DIR+'/overview.png',fullPage:true});
 await page.getByRole('button',{name:'Remote config & ads',exact:true}).click();await page.getByLabel('Global ads enabled').uncheck();await page.getByRole('button',{name:'Publish settings'}).click();await page.getByText('Settings published successfully.').waitFor();
 const saved=writes.find(x=>x.path==='/config').body;assert.equal(saved.adsEnabled,false);assert.equal('id' in saved,false);assert.equal('updatedAt' in saved,false);await page.screenshot({path:process.env.SHOT_DIR+'/settings.png',fullPage:true});
 await page.getByRole('button',{name:'Promotions',exact:true}).click();await page.getByRole('button',{name:'Create promotion',exact:true}).click();await page.getByLabel('Campaign label / note').fill('Test campaign');await page.getByLabel('Codes to generate').fill('2');await page.getByRole('button',{name:'Save promotion'}).click();await page.getByRole('dialog',{name:'Promotion codes created'}).waitFor();assert.equal(writes.find(x=>x.path==='/promos/bulk').body.passDays,1);await page.getByRole('button',{name:'Close dialog'}).click();
 for(const name of ['Server requests','Devices','Audit trail']){await page.getByRole('button',{name,exact:true}).click();await page.getByRole('heading',{name:name==='Devices'?'Devices & access':name}).waitFor();}
 await page.getByRole('button',{name:'VPN servers',exact:true}).click();await page.getByRole('button',{name:'Edit',exact:true}).click();await page.getByLabel('Node name').fill('Edited test node');await page.getByRole('button',{name:'Save node'}).click();await page.getByRole('dialog',{name:'Edit VPN node'}).waitFor({state:'hidden'});const nodeWrite=writes.find(x=>x.path.includes('/servers/'));assert.equal('healthy' in nodeWrite.body,false);assert.equal('id' in nodeWrite.body,false);
 await page.getByRole('button',{name:'Use light mode'}).click();await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'Toggle navigation'}).click();await page.getByRole('button',{name:'Promotions',exact:true}).click();await page.screenshot({path:process.env.SHOT_DIR+'/mobile.png',fullPage:true});
 await page.getByRole('button',{name:'Sign out',exact:true}).click();await page.getByRole('heading',{name:'Welcome back.'}).waitFor();assert.equal(await page.evaluate(()=>sessionStorage.getItem('nsec_console_token')),null);assert.deepEqual(errors,[]);
 console.log('PASS: console login, navigation, config publish DTO, bulk days, node edit DTO, responsive layout and logout');await browser.close();
})().catch(e=>{console.error(e);process.exitCode=1;});
