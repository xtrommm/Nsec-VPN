// Isolated intercepted fixtures only; never included in the production bundle.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const assert=require('node:assert/strict');
const {mkdirSync}=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_BINARY,args:['--no-sandbox']});
 try{
 const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 const writes=[];let statsReads=0;const now=new Date().toISOString();
 const config={id:'global_config',updatedAt:now,minAppVersionCode:1,latestAppVersionCode:2,forceUpdate:false,maintenanceMode:false,announcementActive:false,announcementTitle:null,announcementBody:null,bannerAdsEnabled:false,nativeAdsEnabled:false,interstitialAdsEnabled:false,rewardedAdsEnabled:false,interstitialIntervalSeconds:120,rewardedPassMinutes:30,bannerAdUnitId:null,nativeAdUnitId:null,interstitialAdUnitId:null,rewardedAdUnitId:null,adsEnabled:true,supportEmail:null};
 const node={id:'fixture-node',name:'Long node name for responsive layout verification',countryCode:'US',countryName:'United States',city:'Virginia',flagEmoji:'',endpoint:'47.85.109.170:51820',publicKey:Buffer.alloc(32,7).toString('base64'),agentUrl:'http://127.0.0.1:51821',subnetIpv4:'10.8.0.0/24',dnsServers:['1.1.1.1'],mtu:1420,maxPeers:250,currentLoad:4,isPremiumOnly:false,isRecommended:true,active:true,healthy:true,healthCheckedAt:now,publicIpv4:'47.85.109.170',sortOrder:0};
 const promo={id:'fixture-promo',label:'Campaign with long descriptive name',passDays:1,passMinutes:1440,maxRedemptions:10,redemptionCount:1,remainingUses:9,startsAt:null,expiresAt:null,active:true,status:'active',codeAvailable:true};
 const device={id:'fixture-device',deviceId:'fixture-installation-identifier-for-wrapping-0123456789',deviceModel:'Android test device',osVersion:'16',appVersion:'1.0',tier:'FREE',passExpiresAt:null,isBlocked:false};
 const pagination={total:1,totalPages:1,page:1,limit:20};
 await page.route('**/api/v1/admin/**',async route=>{
  const req=route.request();const path=new URL(req.url()).pathname.replace('/api/v1/admin','');const body=req.postDataJSON();if(req.method()!=='GET')writes.push({path,body});let result={success:true};
  if(path==='/login')Object.assign(result,{token:'test-only-session',admin:{email:'test@example.org',role:'SUPER_ADMIN'}});
  else if(path==='/me')Object.assign(result,{admin:{email:'test@example.org',role:'SUPER_ADMIN'}});
  else if(path==='/servers')Object.assign(result,{servers:[node]});
  else if(path==='/stats'){statsReads++;Object.assign(result,{data:{activeTunnels:1,totalDevices:10,vipSubscribers:2,totalServers:1,avgServerLoad:4}});}
  else if(path==='/config'){if(req.method()==='PUT'){Object.assign(config,body);config.updatedAt=new Date(Date.now()+1000).toISOString();}Object.assign(result,{config});}
  else if(path==='/promos/bulk')Object.assign(result,{promos:[{code:'TEST-CODE-A'},{code:'TEST-CODE-B'}]});
  else if(path==='/promos')Object.assign(result,{promos:[promo],pagination});
  else if(path.includes('/redemptions'))Object.assign(result,{redemptions:[{id:'fixture-redemption',device:{deviceId:device.deviceId},redeemedAt:now,grantExpiresAt:now}],pagination});
  else if(path==='/devices')Object.assign(result,{devices:[device],pagination});
  else if(path==='/server-requests')Object.assign(result,{requests:[{id:'fixture-request',city:'Nicosia',country:'Cyprus',note:'A long note to confirm text wraps inside the table.',status:'PENDING',createdAt:now,device:{deviceId:device.deviceId}}],pagination});
  else if(path==='/audit-logs')Object.assign(result,{logs:[{id:'fixture-log',action:'UPDATE_CONFIG',targetResource:'CONFIG',createdAt:now,admin:{email:'test@example.org'}}],pagination});
  else if(path==='/mfa/status')Object.assign(result,{enabled:false,enrollmentConfigured:true});
  await route.fulfill({json:result});
 });
 await page.goto(process.env.PANEL_TEST_URL||'http://127.0.0.1:4173');
 await page.getByLabel('Email',{exact:true}).fill('test@example.org');await page.getByLabel('Password',{exact:true}).fill('test-only-password');await page.getByRole('button',{name:'Sign in securely'}).click();await page.getByRole('heading',{name:'Keep everything connected.'}).waitFor();
 async function tab(name){const toggle=page.getByRole('button',{name:'Toggle navigation'});if(await toggle.isVisible())await toggle.click();await page.locator('nav').getByRole('button',{name:new RegExp('^'+name+'(?:\\s*\\d+)?$')}).click();}
 await tab('Ads & Rewards');await page.getByLabel('Global ads enabled').uncheck();await page.getByRole('button',{name:'Publish settings'}).click();await page.getByText('Settings published successfully.').waitFor();
 const saved=writes.find(x=>x.path==='/config').body;assert.equal(saved.adsEnabled,false);assert.equal('minAppVersionCode' in saved,false);assert.equal('id' in saved,false);
 await tab('App Controls');await page.getByLabel('Support email').fill('draft@example.org');config.updatedAt=new Date(Date.now()+5000).toISOString();config.supportEmail='remote@example.org';await page.getByRole('button',{name:'Refresh',exact:true}).click();await page.getByText('A newer configuration is available.',{exact:false}).waitFor();assert.equal(await page.getByLabel('Support email').inputValue(),'draft@example.org');page.once('dialog',d=>d.accept());await page.getByRole('button',{name:'Discard changes'}).click();assert.equal(await page.getByLabel('Support email').inputValue(),'remote@example.org');
 await tab('Promotions');await page.getByRole('button',{name:'Usage',exact:true}).click();await page.getByRole('dialog',{name:'Usage: '+promo.label}).waitFor();assert.equal(await page.evaluate(()=>document.body.style.overflow),'hidden');await page.keyboard.press('Escape');await page.getByRole('dialog').waitFor({state:'hidden'});assert.notEqual(await page.evaluate(()=>document.body.style.overflow),'hidden');
 await page.getByRole('button',{name:'Create promotion',exact:true}).click();await page.getByLabel('Campaign label / note').fill('Test campaign');await page.getByLabel('Codes to generate').fill('2');await page.getByRole('button',{name:'Save promotion'}).click();await page.getByRole('dialog',{name:'Promotion codes created'}).waitFor();assert.equal(writes.find(x=>x.path==='/promos/bulk').body.passDays,1);await page.getByRole('button',{name:'Close dialog'}).click();
 const shots=process.env.SHOT_DIR;if(shots)mkdirSync(shots,{recursive:true});
 for(const theme of ['dark','light']){
  const current=await page.locator('.console').getAttribute('data-theme');if(current!==theme)await page.getByRole('button',{name:theme==='light'?'Use light mode':'Use dark mode'}).click();
  for(const width of [360,768,1024,1440]){
   await page.setViewportSize({width,height:900});
   for(const name of ['Overview','VPN Servers','Device Access','Promotions','Server Requests','App Controls','Ads & Rewards','Announcements','Account Security','Audit Trail']){
    await tab(name);await page.waitForTimeout(250);
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1),`${name} overflows at ${width}px in ${theme}`);
   }
   await tab('Promotions');await page.getByRole('button',{name:'Usage',exact:true}).click();
   const panel=page.getByRole('dialog');await panel.waitFor();const bounds=await panel.boundingBox();assert.ok(bounds.x>=0&&bounds.x+bounds.width<=width+1);
   if(shots)await page.screenshot({path:`${shots}/${theme}-${width}.png`,fullPage:true});await page.keyboard.press('Escape');
  }
 }
 await page.setViewportSize({width:1440,height:900});await tab('Device Access');const count=statsReads;await page.clock.install();await page.clock.fastForward(31000);assert.equal(statsReads,count,'Off-screen dashboard polled');
 await page.getByRole('button',{name:'Sign out',exact:true}).click();await page.getByRole('heading',{name:'Welcome back.'}).waitFor();assert.equal(await page.evaluate(()=>sessionStorage.getItem('nsec_console_token')),null);assert.deepEqual(errors,[]);
 console.log('PASS: 10 tabs, 4 widths, 2 themes, modal bounds/focus scrolling, scoped saves, dirty drafts, background polling, bulk promos and logout');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
