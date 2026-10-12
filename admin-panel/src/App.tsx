import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { Activity, Bell, ChevronRight, ClipboardList, Gift, LayoutDashboard, LogOut, Menu, Moon, Server, Settings, ShieldCheck, Sun, Users, X } from 'lucide-react';
import { ControlNodes, ConsoleApi } from './components/ControlNodes';
import { ControlSettings } from './components/ControlSettings';
import { ControlPromos } from './components/ControlPromos';
import { ControlRequests } from './components/ControlRequests';
import { ControlDevices } from './components/ControlDevices';
import { ControlAudit } from './components/ControlAudit';
import { Notify, errorText } from './components/ConsoleUI';
import { ServerNode, StatsData, AppConfigData } from './types';
type Tab='overview'|'nodes'|'devices'|'promos'|'requests'|'settings'|'audit';
const sections={overview:{title:'Overview',icon:LayoutDashboard},nodes:{title:'VPN servers',icon:Server},devices:{title:'Devices',icon:Users},promos:{title:'Promotions',icon:Gift},requests:{title:'Server requests',icon:ClipboardList},settings:{title:'Remote config & ads',icon:Settings},audit:{title:'Audit trail',icon:ShieldCheck}};
export default function App(){
 const [token,setToken]=useState<string|null>(()=>sessionStorage.getItem('nsec_console_token'));
 const session=useRef(token);const coreSeq=useRef(0);
 const [email,setEmail]=useState(()=>sessionStorage.getItem('nsec_console_email')||'');
 const [role,setRole]=useState(()=>sessionStorage.getItem('nsec_console_role')||'');
 const [tab,setTab]=useState<Tab>('overview');const [menu,setMenu]=useState(false);
 const [theme,setTheme]=useState(()=>localStorage.getItem('nsec_console_theme')==='light'?'light':'dark');
 const [error,setError]=useState('');const [busy,setBusy]=useState(false);const [refreshKey,setRefreshKey]=useState(0);
 const [loginEmail,setLoginEmail]=useState('');const [password,setPassword]=useState('');const [totp,setTotp]=useState('');
 const [nodes,setNodes]=useState<ServerNode[]>([]);const [loaded,setLoaded]=useState(false);const [stats,setStats]=useState<StatsData|null>(null);const [config,setConfig]=useState<AppConfigData|null>(null);const [configError,setConfigError]=useState('');
 const [toasts,setToasts]=useState<{id:number;message:string;type:string}[]>([]);const nextToast=useRef(0);const canWrite=role==='SUPER_ADMIN';
 const notify:Notify=useCallback((message,type='success')=>{if(!session.current)return;const id=++nextToast.current;setToasts(v=>[...v.slice(-3),{id,message,type}]);},[]);
 const logout=useCallback(()=>{
  session.current=null;coreSeq.current++;
  ['nsec_console_token','nsec_console_email','nsec_console_role'].forEach(k=>sessionStorage.removeItem(k));
  ['nsec_admin_token','nsec_admin_email','nsec_admin_role'].forEach(k=>localStorage.removeItem(k));
  setToken(null);setRole('');setEmail('');setNodes([]);setLoaded(false);setStats(null);setConfig(null);setPassword('');setTotp('');setError('');setConfigError('');setToasts([]);setTab('overview');setBusy(false);
 },[]);
 const api:ConsoleApi=useCallback(async(path,options={})=>{
  if(!token||session.current!==token)throw new Error('Session expired. Sign in again.');
  const response=await fetch('/api/v1/admin'+path,{...options,headers:{'Content-Type':'application/json',Authorization:`Bearer ${token}`,...options.headers},signal:options.signal?AbortSignal.any([options.signal,AbortSignal.timeout(15000)]):AbortSignal.timeout(15000)});
  const data=await response.json().catch(()=>({}));
  if(session.current!==token)throw new Error('Session changed.');
  if(response.status===401){logout();throw new Error('Session expired. Sign in again.');}
  if(!response.ok||!data.success){const validation=Array.isArray(data.details)?data.details.map((d:{field:string;message:string})=>`${d.field}: ${d.message}`).join('; '):'';throw new Error(validation||data.message||data.error||`Request failed (${response.status})`);}
  return data;
 },[token,logout]);
 const core=useCallback(async()=>{
  const id=++coreSeq.current;setBusy(true);const results=await Promise.allSettled([api('/servers'),api('/stats')]);
  if(id!==coreSeq.current||session.current!==token)return;
  if(results[0].status==='fulfilled'){setNodes(results[0].value.servers);setLoaded(true);}
  if(results[1].status==='fulfilled')setStats(results[1].value.data);
  const failed=results.find(r=>r.status==='rejected');setError(failed?.status==='rejected'?errorText(failed.reason):'');setBusy(false);
 },[api,token]);
 const loadConfig=useCallback(async()=>{try{const r=await api('/config');if(session.current===token){setConfig(r.config);setConfigError('');}}catch(e){if(session.current===token)setConfigError(errorText(e));}},[api,token]);
 useEffect(()=>{if(!token)return;void core();void loadConfig();void api('/me').then(r=>{setRole(r.admin.role);setEmail(r.admin.email);sessionStorage.setItem('nsec_console_role',r.admin.role);}).catch(e=>{if(session.current===token)setError(errorText(e));});const timer=setInterval(()=>void core(),30000);return()=>clearInterval(timer);},[token,api,core,loadConfig]);
 useEffect(()=>{const timer=setInterval(()=>setToasts(v=>v.slice(1)),6000);return()=>clearInterval(timer);},[]);
 useEffect(()=>{if(tab==='audit'&&!canWrite)setTab('overview');},[tab,canWrite]);
 async function login(e:FormEvent){e.preventDefault();setBusy(true);setError('');try{
  const r=await fetch('/api/v1/admin/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:loginEmail,password,totpCode:totp||undefined}),signal:AbortSignal.timeout(15000)});
  const d=await r.json();if(!r.ok||!d.success||!d.token||!['ADMIN','SUPER_ADMIN'].includes(d.admin?.role))throw new Error(d.message||'Sign-in failed');
  ['nsec_admin_token','nsec_admin_email','nsec_admin_role'].forEach(k=>localStorage.removeItem(k));
  sessionStorage.setItem('nsec_console_token',d.token);sessionStorage.setItem('nsec_console_email',d.admin.email);sessionStorage.setItem('nsec_console_role',d.admin.role);
  session.current=d.token;setEmail(d.admin.email);setRole(d.admin.role);setToken(d.token);setPassword('');setTotp('');
 }catch(e){setError(errorText(e));}finally{setBusy(false);}}
 async function exportStats(){try{const response=await fetch('/api/v1/admin/export/stats',{headers:{Authorization:`Bearer ${token}`},signal:AbortSignal.timeout(15000)});if(session.current!==token)return;if(response.status===401){logout();return;}if(!response.ok)throw new Error('Export failed');const url=URL.createObjectURL(await response.blob());const link=document.createElement('a');link.href=url;link.download='nsec-aggregate-statistics.csv';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);notify('Aggregate statistics exported');}catch(e){notify(errorText(e),'error');}}
 function refresh(){setRefreshKey(v=>v+1);void core();if(tab==='settings'||tab==='overview')void loadConfig();}
 const healthy=nodes.filter(n=>n.active&&n.healthy&&n.healthCheckedAt&&Date.now()-Date.parse(n.healthCheckedAt)<120000).length;
 const nodeProblems=nodes.filter(n=>n.active&&(!n.healthy||!n.healthCheckedAt||Date.now()-Date.parse(n.healthCheckedAt)>=120000));
 return <div className="console" data-theme={theme}>
 {!token?<main className="login-wrap"><form className="panel login" onSubmit={login}><div className="brand-mark"><ShieldCheck/></div><span className="eyebrow">NSEC / OPERATIONS</span><h1>Welcome back.</h1><p>Sign in to manage your network.</p><label>Email<input type="email" autoComplete="username" required value={loginEmail} onChange={e=>setLoginEmail(e.target.value)}/></label><label>Password<input type="password" autoComplete="current-password" required value={password} onChange={e=>setPassword(e.target.value)}/></label><label>Authenticator code (if enabled)<input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" value={totp} onChange={e=>setTotp(e.target.value)}/></label>{error&&<p className="error" role="alert">{error}</p>}<button className="primary" disabled={busy}>{busy?'Signing in…':'Sign in securely'}<ChevronRight size={16}/></button></form></main>:<div className="console-layout">
 <aside className={`console-sidebar ${menu?'open':''}`}><div className="brand"><div className="brand-mark"><ShieldCheck/></div><div><strong>Nsec VPN</strong><span>Network workspace</span></div></div><span className="nav-label">WORKSPACE</span><nav aria-label="Main navigation">{(Object.keys(sections) as Tab[]).filter(t=>t!=='audit'||canWrite).map(t=>{const Icon=sections[t].icon;return <button key={t} className={tab===t?'selected':''} aria-current={tab===t?'page':undefined} onClick={()=>{setTab(t);setMenu(false);}}><Icon size={18}/>{sections[t].title}{t==='overview'&&loaded&&<span className="nav-count">{nodes.length}</span>}</button>;})}</nav><div className="sidebar-footer"><div className="secure-note"><ShieldCheck size={18}/><span>Traffic stays private</span></div><p>No client-IP, DNS-query or browsing records.</p><small>Authorized operations only</small></div></aside>
 {menu&&<button className="sidebar-overlay" aria-label="Close navigation" onClick={()=>setMenu(false)}/>}
 <main className="console-main"><header className="console-top"><div className="page-breadcrumb"><button className="menu-button" aria-label="Toggle navigation" onClick={()=>setMenu(!menu)}><Menu size={20}/></button><span>Workspace</span><ChevronRight size={14}/><strong>{sections[tab].title}</strong></div><div className="actions"><button className="icon-button" aria-label={theme==='dark'?'Use light mode':'Use dark mode'} onClick={()=>{const next=theme==='dark'?'light':'dark';setTheme(next);localStorage.setItem('nsec_console_theme',next);}}>{theme==='dark'?<Sun size={18}/>:<Moon size={18}/>}</button><button disabled={busy} onClick={refresh}>{busy?'Refreshing…':'Refresh'}</button><div className="profile"><span className="avatar">{email.slice(0,1).toUpperCase()}</span><div><strong>{email}</strong><small>{role.replace(/_/g,' ')}</small></div><button className="icon-button" aria-label="Sign out" onClick={logout}><LogOut size={18}/></button></div></div></header>
 {error&&<p className="error" role="alert">{error}</p>}
 {tab==='overview'&&<section className="console-section"><div className="section-heading"><div><span className="eyebrow">NETWORK OVERVIEW</span><h1>Keep everything connected.</h1><p>Infrastructure, access and app controls in one workspace.</p></div><button className="primary" onClick={()=>setTab('nodes')}><Server size={16}/>Manage servers</button></div><div className="metrics">{[{label:'Healthy servers',value:loaded?healthy:null,icon:Activity,detail:loaded?`${nodes.length} total nodes`:'Waiting for server data'},{label:'Devices',value:stats?.totalDevices,icon:Users,detail:'Registered installations'},{label:'VIP access',value:stats?.vipSubscribers,icon:ShieldCheck,detail:'Current, unexpired VIP'},{label:'Active leases',value:stats?.activeTunnels,icon:Server,detail:'Unexpired peer allocations'}].map(({label,value,icon:Icon,detail})=><article className="panel metric" key={label}><div className="metric-heading"><span>{label}</span><Icon size={18}/></div><strong>{value??'—'}</strong><small>{detail}</small></article>)}</div><div className="settings-grid"><section className="panel"><div className="section-heading"><h3>Network health</h3><span className="badge">{loaded?`${healthy}/${nodes.length} healthy`:'Loading'}</span></div>{loaded&&!nodes.length?<p>No servers added yet.</p>:!loaded?<div className="skeleton"/>:nodeProblems.length?nodeProblems.map(n=><div className="readiness-row" key={n.id}><div><strong>{n.name}</strong><small>{n.endpoint}</small></div><button onClick={()=>setTab('nodes')}>Review</button></div>):<div className="healthy-state"><ShieldCheck size={38}/><strong>Enabled servers are healthy</strong><p>Availability reflects the latest agent checks.</p></div>}<p className="hint">Leases indicate allocated access. They do not prove a phone handshake or traffic.</p></section><section className="panel"><h3>App operations</h3><div className="readiness-row"><div><strong>Rewards & promotions</strong><small>{config?`Reward duration: ${config.rewardedPassMinutes} minutes`:'Settings not loaded'}</small></div><button onClick={()=>setTab('promos')}>Manage</button></div><div className="readiness-row"><div><strong>Remote configuration</strong><small>{config?.updatedAt?`Published ${new Date(config.updatedAt).toLocaleString()}`:'Not loaded'}</small></div><button onClick={()=>setTab('settings')}>Open</button></div><div className="readiness-row"><div><strong>Requested locations</strong><small>Review requests from app installations</small></div><button onClick={()=>setTab('requests')}>Review</button></div>{configError&&<p className="error" role="alert">{configError}</p>}</section></div><section className="panel activity-note"><Bell size={22}/><div><strong>A workspace for real operations</strong><p>Admin actions are auditable. Device access and promo limits are checked server-side.</p></div><button onClick={()=>setTab(canWrite?'audit':'devices')}>{canWrite?'View audit trail':'View devices'}</button><button onClick={()=>void exportStats()}>Export stats</button></section></section>}
 {tab==='nodes'&&<ControlNodes nodes={nodes} api={api} refresh={core} canWrite={canWrite} notify={notify}/>}
 {tab==='settings'&&<><p className="error" role="alert" hidden={!configError}>{configError}</p><ControlSettings config={config} api={api} onSaved={setConfig} canWrite={canWrite} notify={notify}/></>}
 {tab==='promos'&&<ControlPromos api={api} canWrite={canWrite} notify={notify} refreshKey={refreshKey}/>}
 {tab==='requests'&&<ControlRequests api={api} canWrite={canWrite} notify={notify} refreshKey={refreshKey}/>}
 {tab==='devices'&&<ControlDevices api={api} canWrite={canWrite} notify={notify} refreshKey={refreshKey}/>}
 {tab==='audit'&&canWrite&&<ControlAudit api={api} refreshKey={refreshKey}/>}
 </main></div>}
 <div className="toast-stack" aria-live="polite">{toasts.map(t=><div className={`toast ${t.type}`} key={t.id}><span>{t.message}</span><button aria-label="Dismiss notification" onClick={()=>setToasts(v=>v.filter(x=>x.id!==t.id))}><X size={16}/></button></div>)}</div>
 </div>;
}
