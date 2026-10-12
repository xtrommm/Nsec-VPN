import { FormEvent, useCallback, useEffect, useState } from 'react';
import { ControlNodes, ConsoleApi } from './components/ControlNodes';
import { ControlSettings } from './components/ControlSettings';
import { ControlPromos } from './components/ControlPromos';
import { ServerNode, DeviceUser, StatsData, AppConfigData, AuditLogEntry } from './types';
type Tab='overview'|'nodes'|'devices'|'promos'|'settings'|'audit';
const titles:Record<Tab,string>={overview:'Overview',nodes:'VPN nodes',devices:'Devices',promos:'Promo codes',settings:'App settings & ads',audit:'Audit trail'};
export default function App(){
 const [token,setToken]=useState<string|null>(()=>sessionStorage.getItem('nsec_console_token'));
 const [email,setEmail]=useState(()=>sessionStorage.getItem('nsec_console_email')||'');
 const [role,setRole]=useState(()=>sessionStorage.getItem('nsec_console_role')||'');
 const [tab,setTab]=useState<Tab>('overview');const [menu,setMenu]=useState(false);
 const [theme,setTheme]=useState(()=>localStorage.getItem('nsec_console_theme')||'dark');
 const [error,setError]=useState('');const [busy,setBusy]=useState(false);
 const [loginEmail,setLoginEmail]=useState('');const [password,setPassword]=useState('');const [totp,setTotp]=useState('');
 const [nodes,setNodes]=useState<ServerNode[]>([]);const [nodesLoaded,setNodesLoaded]=useState(false);const [stats,setStats]=useState<StatsData|null>(null);const [config,setConfig]=useState<AppConfigData|null>(null);
 const [devices,setDevices]=useState<DeviceUser[]>([]);const [search,setSearch]=useState('');const [page,setPage]=useState(1);const [pages,setPages]=useState(1);
 const [logs,setLogs]=useState<AuditLogEntry[]>([]);const canWrite=role==='SUPER_ADMIN';
 const logout=useCallback(()=>{
  ['nsec_console_token','nsec_console_email','nsec_console_role'].forEach(k=>sessionStorage.removeItem(k));
  ['nsec_admin_token','nsec_admin_email','nsec_admin_role'].forEach(k=>localStorage.removeItem(k));
  setToken(null);setRole('');setEmail('');setNodes([]);setNodesLoaded(false);setDevices([]);setStats(null);setConfig(null);setLogs([]);setPassword('');setTotp('');
 },[]);
 const api:ConsoleApi=useCallback(async(path,options={})=>{
  const response=await fetch('/api/v1/admin'+path,{...options,headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`,...options.headers},signal:AbortSignal.timeout(15000)});
  const data=await response.json().catch(()=>({}));
  if(response.status===401){logout();throw new Error('Session expired. Sign in again.');}
  if(!response.ok||!data.success)throw new Error(data.message||data.error||`Request failed (${response.status})`);
  return data;
 },[token,logout]);
 const core=useCallback(async()=>{
  setBusy(true);setError('');const results=await Promise.allSettled([api('/servers'),api('/stats')]);
  if(results[0].status==='fulfilled'){setNodes(results[0].value.servers);setNodesLoaded(true);}
  if(results[1].status==='fulfilled')setStats(results[1].value.data);
  const failed=results.find(r=>r.status==='rejected');if(failed?.status==='rejected')setError(failed.reason instanceof Error?failed.reason.message:'Some data could not be refreshed');
  setBusy(false);
 },[api]);
 const loadDevices=useCallback(async()=>{
  try{const r=await api(`/devices?${new URLSearchParams({page:String(page),limit:'20',search})}`);setDevices(r.devices);setPages(r.pagination.totalPages);setError('');}
  catch(e){setError(e instanceof Error?e.message:'Could not load devices');}
 },[api,page,search]);
 useEffect(()=>{if(!token)return;let live=true;void core();void api('/config').then(r=>{if(live)setConfig(r.config);}).catch(e=>{if(live)setError(e.message);});
  const timer=setInterval(()=>void core(),30000);return()=>{live=false;clearInterval(timer);};},[token,api,core]);
 useEffect(()=>{if(!token||tab!=='devices')return;const timer=setTimeout(()=>void loadDevices(),300);return()=>clearTimeout(timer);},[token,tab,loadDevices]);
 useEffect(()=>{if(token&&tab==='audit'&&canWrite)void api('/audit-logs').then(r=>setLogs(r.logs)).catch(e=>setError(e.message));},[token,tab,canWrite,api]);
 async function login(e:FormEvent){e.preventDefault();setBusy(true);setError('');try{
  const r=await fetch('/api/v1/admin/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:loginEmail,password,totpCode:totp||undefined}),signal:AbortSignal.timeout(15000)});
  const d=await r.json();if(!r.ok||!d.success)throw new Error(d.message||'Sign-in failed');
  ['nsec_admin_token','nsec_admin_email','nsec_admin_role'].forEach(k=>localStorage.removeItem(k));
  sessionStorage.setItem('nsec_console_token',d.token);sessionStorage.setItem('nsec_console_email',d.admin.email);sessionStorage.setItem('nsec_console_role',d.admin.role);
  setEmail(d.admin.email);setRole(d.admin.role);setToken(d.token);setPassword('');setTotp('');
 }catch(e){setError(e instanceof Error?e.message:'Sign-in unavailable');}finally{setBusy(false);}}
 async function deviceAction(d:DeviceUser,kind:'block'|'tier'){
  const data=kind==='block'?{isBlocked:!d.isBlocked}:{tier:d.tier==='FREE'?'VIP':'FREE'};
  if(!window.confirm(kind==='block'?`${d.isBlocked?'Unblock':'Block'} this device?`:d.tier==='FREE'?'Grant VIP for one year?':'Remove premium access?'))return;
  try{await api(`/devices/${d.id}/${kind}`,{method:'PUT',body:JSON.stringify(data)});await loadDevices();await core();}
  catch(e){setError(e instanceof Error?e.message:'Device update failed');}
 }
 async function exportStats(){try{
  const r=await fetch('/api/v1/admin/export/stats',{headers:{Authorization:`Bearer ${token}`},signal:AbortSignal.timeout(15000)});
  if(!r.ok)throw new Error('Export failed');const url=URL.createObjectURL(await r.blob());const a=document.createElement('a');a.href=url;a.download='nsec-aggregate-statistics.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
 }catch(e){setError(e instanceof Error?e.message:'Export failed');}}
 const healthy=nodes.filter(n=>n.active&&n.healthy&&n.healthCheckedAt&&Date.now()-Date.parse(n.healthCheckedAt)<120000).length;
 return <div className="console" data-theme={theme}>
 {!token?<main className="login-wrap"><form className="panel login" onSubmit={login}><div className="brand-mark">N</div><span className="eyebrow">NSEC VPN</span><h1>Operations console</h1><p>Manage VPN infrastructure, access and monetization.</p>
  <label>Email<input type="email" autoComplete="username" required value={loginEmail} onChange={e=>setLoginEmail(e.target.value)}/></label>
  <label>Password<input type="password" autoComplete="current-password" required value={password} onChange={e=>setPassword(e.target.value)}/></label>
  <label>Authenticator code (if enabled)<input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" value={totp} onChange={e=>setTotp(e.target.value)}/></label>
  {error&&<p className="error" role="alert">{error}</p>}<button className="primary" disabled={busy}>{busy?'Signing in…':'Sign in'}</button>
 </form></main>:<div className="console-layout">
 <aside className={`console-sidebar ${menu?'open':''}`}><div className="brand"><div className="brand-mark">N</div><div><strong>Nsec VPN</strong><span>Operations console</span></div></div>
  <nav aria-label="Main navigation">{(Object.keys(titles) as Tab[]).filter(t=>t!=='audit'||canWrite).map(t=><button key={t} className={tab===t?'selected':''} aria-current={tab===t?'page':undefined} onClick={()=>{setTab(t);setMenu(false);}}>{titles[t]}</button>)}</nav>
  <div className="sidebar-footer"><strong>{email}</strong><span>{role.replace('_',' ')}</span><p>No browsing or traffic-content records.</p><button onClick={logout}>Sign out</button></div>
 </aside><main className="console-main"><header className="console-top"><button className="menu-button" aria-label="Toggle navigation" onClick={()=>setMenu(!menu)}>☰</button><div><span className="eyebrow">CONTROL PANEL</span><h1>{titles[tab]}</h1></div>
  <div className="actions"><button onClick={()=>{const next=theme==='dark'?'light':'dark';setTheme(next);localStorage.setItem('nsec_console_theme',next);}}>{theme==='dark'?'Light mode':'Dark mode'}</button><button disabled={busy} onClick={()=>void core()}>{busy?'Refreshing…':'Refresh'}</button></div></header>
  {error&&<p className="error" role="alert">{error}</p>}
  {tab==='overview'&&<section className="console-section"><div className="hero"><span className="eyebrow">NETWORK OPERATIONS</span><h2>Your network, at a glance.</h2><p>Health determines app visibility. Peer leases indicate allocated access, not proof of a live handshake.</p></div>
   <div className="metrics">{[['Healthy nodes',nodesLoaded?healthy:null],['Total nodes',stats?.totalServers],['Registered devices',stats?.totalDevices],['Active peer leases',stats?.activeTunnels]].map(([label,value])=><article className="panel metric" key={String(label)}><span>{label}</span><strong>{value??'—'}</strong></article>)}</div>
   <div className="settings-grid"><section className="panel"><h3>Node readiness</h3>{nodes.length?nodes.map(n=><div className="readiness-row" key={n.id}><div><strong>{n.name}</strong><small>{n.endpoint}</small></div><button onClick={()=>setTab('nodes')}>{!n.active?'Disabled':n.healthy?'Review health':'Needs attention'}</button></div>):<p>No nodes loaded. Add a real node to get started.</p>}</section>
   <section className="panel"><h3>Monetization setup</h3><p>Rewarded access: {config?.rewardedPassMinutes??'—'} minutes</p><p>Rewarded unit: {config?.rewardedAdUnitId?'Configured':'Not configured'}</p><p>Completed rewards unlock ad-free VIP and premium nodes. One-use claims are authenticated; Google SSV confirms the event.</p><div className="actions"><button onClick={()=>setTab('settings')}>Manage AdMob</button><button onClick={()=>setTab('promos')}>Manage promotions</button></div></section></div>
  </section>}
  {tab==='nodes'&&<ControlNodes nodes={nodes} api={api} refresh={core} canWrite={canWrite}/>}
  {tab==='settings'&&<ControlSettings config={config} api={api} onSaved={setConfig} canWrite={canWrite}/>}
  {tab==='promos'&&<ControlPromos api={api} canWrite={canWrite}/>}
  {tab==='devices'&&<section className="console-section"><div className="section-heading"><div><h2>Devices & entitlements</h2><p>Random installation identities. No browsing history or client-IP records.</p></div><button onClick={()=>void exportStats()}>Export aggregate stats</button></div>
   <input aria-label="Search devices" placeholder="Search installation ID or model" value={search} onChange={e=>{setSearch(e.target.value);setPage(1);}}/>
   <div className="panel table-scroll"><table><thead><tr><th>Installation</th><th>Device</th><th>Entitlement</th><th>Expiry</th><th>Status</th><th>Actions</th></tr></thead><tbody>{devices.map(d=><tr key={d.id}><td><code>{d.deviceId}</code></td><td>{d.deviceModel||'Not provided'}<small>{d.osVersion||''} · {d.appVersion||''}</small></td><td>{d.tier}</td><td>{d.passExpiresAt?new Date(d.passExpiresAt).toLocaleString():'No expiry recorded'}</td><td>{d.isBlocked?'Blocked':'Allowed'}</td><td><div className="actions"><button onClick={()=>void deviceAction(d,'block')}>{d.isBlocked?'Unblock':'Block'}</button>{canWrite&&<button onClick={()=>void deviceAction(d,'tier')}>{d.tier==='FREE'?'Grant VIP (1 year)':'Remove premium'}</button>}</div></td></tr>)}</tbody></table>{!devices.length&&<p className="empty">No matching devices.</p>}</div>
   <div className="actions"><button disabled={page<=1} onClick={()=>setPage(page-1)}>Previous</button><span>Page {page} of {pages}</span><button disabled={page>=pages} onClick={()=>setPage(page+1)}>Next</button></div>
  </section>}
  {tab==='audit'&&canWrite&&<section className="console-section"><h2>Administrative audit trail</h2><p>Latest 100 administrative actions. No client traffic logs.</p><div className="panel table-scroll"><table><thead><tr><th>Time</th><th>Administrator</th><th>Action</th><th>Resource</th></tr></thead><tbody>{logs.map(l=><tr key={l.id}><td>{new Date(l.createdAt).toLocaleString()}</td><td>{l.admin?.email||'Not available'}</td><td>{l.action}</td><td>{l.targetResource}<small>{l.targetId}</small></td></tr>)}</tbody></table>{!logs.length&&<p className="empty">No audit records loaded.</p>}</div></section>}
 </main></div>}
 </div>;
}
