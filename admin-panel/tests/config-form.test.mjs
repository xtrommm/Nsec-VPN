import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import ts from 'typescript';
const source=await readFile(new URL('../src/lib/configForm.ts',import.meta.url),'utf8');
const {configPayload,validateConfig}=await import('data:text/javascript;base64,'+Buffer.from(ts.transpile(source,{module:ts.ModuleKind.ES2022})).toString('base64'));
const valid={minAppVersionCode:1,latestAppVersionCode:2,forceUpdate:false,maintenanceMode:false,announcementActive:false,announcementTitle:null,announcementBody:null,bannerAdsEnabled:false,nativeAdsEnabled:false,interstitialAdsEnabled:false,rewardedAdsEnabled:false,interstitialIntervalSeconds:120,rewardedPassMinutes:30,adsEnabled:true,supportEmail:null};
test('writes only known configuration fields and preserves false toggles',()=>{
 const payload=configPayload({...valid,id:'global_config',updatedAt:'2030-01-01',unrecognizedServerField:'future'});
 assert.equal(payload.adsEnabled,true);assert.equal(payload.bannerAdsEnabled,false);assert.equal('id' in payload,false);assert.equal('updatedAt' in payload,false);assert.equal('unrecognizedServerField' in payload,false);
});
test('normalizes blank unit IDs and validates duration, versions and announcements',()=>{
 assert.deepEqual(validateConfig(valid),[]);assert.equal(configPayload({...valid,bannerAdUnitId:'   '}).bannerAdUnitId,null);
 for(const change of [{minAppVersionCode:3},{rewardedPassMinutes:0},{interstitialIntervalSeconds:1},{announcementActive:true},{rewardedAdUnitId:'invalid'},{supportEmail:'invalid'}])assert.ok(validateConfig({...valid,...change}).length);
});
test('each tab validates and sends its own fields only',()=>{
 const brokenAds={...valid,rewardedPassMinutes:0,rewardedAdUnitId:'invalid'};
 assert.deepEqual(validateConfig(brokenAds,'app'),[]);
 assert.ok(validateConfig(brokenAds,'ads').length);
 const app=configPayload(valid,'app');assert.equal('rewardedPassMinutes' in app,false);assert.equal('supportEmail' in app,true);
 const ads=configPayload(valid,'ads');assert.equal('maintenanceMode' in ads,false);assert.equal('adsEnabled' in ads,true);
 const announcement=configPayload(valid,'announcements');assert.deepEqual(Object.keys(announcement).sort(),['announcementActive','announcementBody','announcementTitle']);
 assert.ok(validateConfig({...valid,announcementActive:true},'announcements').length);
});
