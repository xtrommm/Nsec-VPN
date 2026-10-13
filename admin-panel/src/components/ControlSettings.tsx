import { useEffect, useState } from 'react';
import { AppConfigData } from '../types';
import { ConsoleApi } from './ControlNodes';
import { Notify } from './ConsoleUI';
import { configPayload, validateConfig } from '../lib/configForm';
const idFields=['bannerAdUnitId','nativeAdUnitId','interstitialAdUnitId','rewardedAdUnitId'] as const;
export function ControlSettings({config,api,onSaved,canWrite,notify,onDirty,mode='all'}:{onDirty?:(dirty:boolean)=>void;mode?:'all'|'ads'|'app'|'announcements';config:AppConfigData|null;api:ConsoleApi;onSaved:(c:AppConfigData)=>void;canWrite:boolean;notify:Notify}) {
 const [draft,setDraft]=useState<AppConfigData|null>(config);
 const [dirty,setDirty]=useState(false);const [baseVersion,setBaseVersion]=useState(config?.updatedAt);
 const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [saved,setSaved]=useState(false);
 useEffect(()=>{if(!dirty){setDraft(config);setBaseVersion(config?.updatedAt);}},[config,dirty]);
 function updateDraft(next:AppConfigData){setDirty(true);setDraft(next);setSaved(false);}
 useEffect(()=>{onDirty?.(dirty);return()=>onDirty?.(false);},[dirty,onDirty]);
 if(!draft)return <div className="empty">Configuration unavailable. Refresh to retry.</div>;
 const value=draft;
 async function save(e:React.FormEvent){
  e.preventDefault();if(busy)return;setBusy(true);setError('');setSaved(false);
  try {
   const errors=validateConfig(value,mode);if(errors.length)throw new Error(errors.join(' '));
   const payload=configPayload(value,mode);
   if(baseVersion)payload.expectedUpdatedAt=baseVersion;
   const r=await api('/config',{method:'PUT',body:JSON.stringify(payload)});onSaved(r.config);setDraft(r.config);setBaseVersion(r.config.updatedAt);setDirty(false);setSaved(true);notify('Configuration published');
  }catch(e){setError(e instanceof Error?e.message:'Could not publish configuration');}finally{setBusy(false);}
 }
 return <form onSubmit={save} className="console-section">
  <div className="section-heading"><div><h2>{mode==='ads'?'Ads & Rewards':mode==='announcements'?'Announcements':mode==='app'?'App Controls':'App Settings & Monetization'}</h2><p>Publish verified settings to Android clients. Changes are fetched on the app’s next configuration refresh.</p></div>
   <button className="primary" disabled={busy||!canWrite||!dirty}>{busy?'Publishing…':'Publish settings'}</button></div>
  {dirty&&<div className="notice draft-notice"><span>{config?.updatedAt!==baseVersion?'A newer configuration is available. Discard your draft and reload before publishing.':'You have unpublished changes.'}</span><button type="button" disabled={busy} onClick={()=>{if(window.confirm('Discard unpublished changes?')){setDirty(false);setDraft(config);setBaseVersion(config?.updatedAt);}}}>Discard changes</button></div>}
  {!canWrite&&<p className="hint">Only a Super Admin can publish settings.</p>}
  {error&&<p role="alert" className="error">{error}</p>}{saved&&<p role="status" className="success">Settings published successfully.</p>}
  <fieldset disabled={!canWrite||busy}><div className="settings-grid">
   {(mode==='all'||mode==='ads')&&<section className="panel"><h3>AdMob ad units</h3><label className="checkbox"><input type="checkbox" checked={value.adsEnabled??true} onChange={e=>updateDraft({...value,adsEnabled:e.target.checked})}/>Global ads enabled</label><p className="hint">Turning this off disables every placement and new rewarded-ad preparations.</p><p>These IDs override the ad units used by release builds. Debug builds retain safe Google test ads.</p>
    {idFields.map(k=><label key={k}>{{bannerAdUnitId:'Banner',nativeAdUnitId:'Native',interstitialAdUnitId:'Interstitial',rewardedAdUnitId:'Rewarded'}[k]} ad unit ID
     <input pattern="ca-app-pub-[0-9]{16}/[0-9]{10}" value={value[k]||''} onChange={e=>updateDraft({...value,[k]:e.target.value})}/></label>)}
    <p className="hint">Blank units disable the corresponding release placement. The AdMob application ID uses “~” and is embedded in the APK manifest; changing it requires a new build, not a remote override.</p>
   </section>}
   {(mode==='all'||mode==='ads')&&<section className="panel"><h3>Rewarded access</h3><label>Reward duration (minutes)<input type="number" required min="1" max="1440" value={value.rewardedPassMinutes??30} onChange={e=>updateDraft({...value,rewardedPassMinutes:Number(e.target.value)})}/></label>
    <p>The signed AdMob callback grants VIP and ad-free access together. A client SDK callback alone cannot authorize VIP. Confirmation time depends on AdMob and callback reachability.</p>
    <div className="notice"><strong>AdMob SSV callback</strong><code>https://api.nsecvpn.com/api/v1/billing/rewarded/ssv</code><p>Configure this URL in your rewarded ad unit’s server-side verification settings. The backend verifies the published rewarded unit ID.</p></div>
    <p className="hint">Google sample ads in debug builds do not grant production VIP. Test release verification with the actual rewarded unit and registered test devices.</p>
    <h3>Placement switches</h3>{(['bannerAdsEnabled','nativeAdsEnabled','interstitialAdsEnabled','rewardedAdsEnabled'] as const).map(k=><label className="checkbox" key={k}><input type="checkbox" checked={value[k]??true} onChange={e=>updateDraft({...value,[k]:e.target.checked})}/>{{bannerAdsEnabled:'Banner',nativeAdsEnabled:'Native',interstitialAdsEnabled:'Interstitial',rewardedAdsEnabled:'Rewarded'}[k]} enabled</label>)}
    <label>Minimum interstitial interval (seconds)<input type="number" required min="15" max="1800" value={value.interstitialIntervalSeconds} onChange={e=>updateDraft({...value,interstitialIntervalSeconds:Number(e.target.value)})}/></label>
   </section>}
   {(mode==='all'||mode==='app')&&<section className="panel"><h3>Support & app controls</h3><label>Support email<input type="email" maxLength={254} value={value.supportEmail||''} onChange={e=>updateDraft({...value,supportEmail:e.target.value})}/></label><p className="hint">Shown in updated Android Settings. Leave blank until you configure a real contact address.</p>
    {(['minAppVersionCode','latestAppVersionCode'] as const).map(k=><label key={k}>{k==='minAppVersionCode'?'Minimum supported version code':'Latest version code'}<input type="number" required min="1" value={value[k]} onChange={e=>updateDraft({...value,[k]:Number(e.target.value)})}/></label>)}
    {(['forceUpdate','maintenanceMode'] as const).map(k=><label className="checkbox" key={k}><input type="checkbox" checked={value[k]} onChange={e=>updateDraft({...value,[k]:e.target.checked})}/>{k==='forceUpdate'?'Require app update':'Show maintenance notice'}</label>)}
   </section>}
   {(mode==='all'||mode==='announcements')&&<section className="panel"><h3>Announcement</h3><label className="checkbox"><input type="checkbox" checked={value.announcementActive} onChange={e=>updateDraft({...value,announcementActive:e.target.checked})}/>Show announcement</label>
    <label>Title<input maxLength={128} required={value.announcementActive} value={value.announcementTitle||''} onChange={e=>updateDraft({...value,announcementTitle:e.target.value})}/></label>
    <label>Message<textarea maxLength={512} required={value.announcementActive} value={value.announcementBody||''} onChange={e=>updateDraft({...value,announcementBody:e.target.value})}/></label>
    <p className="hint">Last published: {config?.updatedAt?new Date(config.updatedAt).toLocaleString():'Not available'}</p>
   </section>}
  </div></fieldset>
 </form>;
}
