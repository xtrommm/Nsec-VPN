import { useEffect, useState } from 'react';
import { AppConfigData } from '../types';
import { ConsoleApi } from './ControlNodes';
const idFields=['bannerAdUnitId','nativeAdUnitId','interstitialAdUnitId','rewardedAdUnitId'] as const;
export function ControlSettings({config,api,onSaved,canWrite}:{config:AppConfigData|null;api:ConsoleApi;onSaved:(c:AppConfigData)=>void;canWrite:boolean}) {
 const [draft,setDraft]=useState<AppConfigData|null>(config);
 const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [saved,setSaved]=useState(false);
 useEffect(()=>{setDraft(config);setSaved(false);},[config]);
 if(!draft)return <div className="empty">Configuration unavailable. Refresh to retry.</div>;
 const value=draft;
 async function save(e:React.FormEvent){
  e.preventDefault();setBusy(true);setError('');setSaved(false);
  try {
   const {id,updatedAt,...fields}=value;
   void id;void updatedAt;
   const payload={...fields,...Object.fromEntries(idFields.map(k=>[k,value[k]?.trim()||null]))};
   const r=await api('/config',{method:'PUT',body:JSON.stringify(payload)});onSaved(r.config);setSaved(true);
  }catch(e){setError(e instanceof Error?e.message:'Could not publish configuration');}finally{setBusy(false);}
 }
 return <form onSubmit={save} className="console-section">
  <div className="section-heading"><div><h2>App settings & monetization</h2><p>Publish verified settings to Android clients. Changes are fetched on the app’s next configuration refresh.</p></div>
   <button className="primary" disabled={busy||!canWrite}>{busy?'Publishing…':'Publish settings'}</button></div>
  {!canWrite&&<p className="hint">Only a Super Admin can publish settings.</p>}
  {error&&<p role="alert" className="error">{error}</p>}{saved&&<p role="status" className="success">Settings published successfully.</p>}
  <fieldset disabled={!canWrite||busy}><div className="settings-grid">
   <section className="panel"><h3>AdMob ad units</h3><p>These IDs override the ad units used by release builds. Debug builds retain safe Google test ads.</p>
    {idFields.map(k=><label key={k}>{{bannerAdUnitId:'Banner',nativeAdUnitId:'Native',interstitialAdUnitId:'Interstitial',rewardedAdUnitId:'Rewarded'}[k]} ad unit ID
     <input pattern="ca-app-pub-[0-9]{16}/[0-9]{10}" value={value[k]||''} onChange={e=>setDraft({...value,[k]:e.target.value})}/></label>)}
    <p className="hint">Blank units disable the corresponding release placement. The AdMob application ID uses “~” and is embedded in the APK manifest; changing it requires a new build, not a remote override.</p>
   </section>
   <section className="panel"><h3>Rewarded access</h3><label>Reward duration (minutes)<input type="number" required min="1" max="1440" value={value.rewardedPassMinutes??30} onChange={e=>setDraft({...value,rewardedPassMinutes:Number(e.target.value)})}/></label>
    <p>After the SDK confirms completion, the app stops ads immediately. The authenticated reward callback grants a one-use VIP pass. Google SSV subsequently verifies the event.</p>
    <div className="notice"><strong>AdMob SSV callback</strong><code>https://api.nsecvpn.com/api/v1/billing/rewarded/ssv</code><p>Configure this URL in your rewarded ad unit’s server-side verification settings. The backend verifies the published rewarded unit ID.</p></div>
    <p className="hint">Test ads demonstrate local ad-free time. They do not prove live server-side verification.</p>
    <h3>Placement switches</h3>{(['bannerAdsEnabled','nativeAdsEnabled','interstitialAdsEnabled','rewardedAdsEnabled'] as const).map(k=><label className="checkbox" key={k}><input type="checkbox" checked={value[k]??true} onChange={e=>setDraft({...value,[k]:e.target.checked})}/>{{bannerAdsEnabled:'Banner',nativeAdsEnabled:'Native',interstitialAdsEnabled:'Interstitial',rewardedAdsEnabled:'Rewarded'}[k]} enabled</label>)}
    <label>Minimum interstitial interval (seconds)<input type="number" required min="15" max="1800" value={value.interstitialIntervalSeconds} onChange={e=>setDraft({...value,interstitialIntervalSeconds:Number(e.target.value)})}/></label>
   </section>
   <section className="panel"><h3>Versions & maintenance</h3>
    {(['minAppVersionCode','latestAppVersionCode'] as const).map(k=><label key={k}>{k==='minAppVersionCode'?'Minimum supported version code':'Latest version code'}<input type="number" required min="1" value={value[k]} onChange={e=>setDraft({...value,[k]:Number(e.target.value)})}/></label>)}
    {(['forceUpdate','maintenanceMode'] as const).map(k=><label className="checkbox" key={k}><input type="checkbox" checked={value[k]} onChange={e=>setDraft({...value,[k]:e.target.checked})}/>{k==='forceUpdate'?'Require app update':'Show maintenance notice'}</label>)}
   </section>
   <section className="panel"><h3>Announcement</h3><label className="checkbox"><input type="checkbox" checked={value.announcementActive} onChange={e=>setDraft({...value,announcementActive:e.target.checked})}/>Show announcement</label>
    <label>Title<input maxLength={128} required={value.announcementActive} value={value.announcementTitle||''} onChange={e=>setDraft({...value,announcementTitle:e.target.value})}/></label>
    <label>Message<textarea maxLength={512} required={value.announcementActive} value={value.announcementBody||''} onChange={e=>setDraft({...value,announcementBody:e.target.value})}/></label>
    <p className="hint">Last published: {config?.updatedAt?new Date(config.updatedAt).toLocaleString():'Not available'}</p>
   </section>
  </div></fieldset>
 </form>;
}
