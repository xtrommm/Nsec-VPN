import { useState } from 'react';
import { ServerNode } from '../types';
export type ConsoleApi = (path: string, options?: RequestInit) => Promise<any>;
type NodeForm = { name: string; countryCode: string; countryName: string; city: string; flagEmoji: string;
 endpoint: string; publicKey: string; subnetIpv4: string; dns: string; mtu: number; maxPeers: number;
 agentUrl: string; agentSecret: string; isPremiumOnly: boolean; isRecommended: boolean; active: boolean };
const emptyNode = (): NodeForm => ({name:'',countryCode:'',countryName:'',city:'',flagEmoji:'',endpoint:'',publicKey:'',
 subnetIpv4:'10.8.0.0/24',dns:'1.1.1.1, 1.0.0.1',mtu:1420,maxPeers:250,agentUrl:'',agentSecret:'',
 isPremiumOnly:false,isRecommended:false,active:true});
export function ControlNodes({nodes, api, refresh, canWrite}:{nodes:ServerNode[];api:ConsoleApi;refresh:()=>Promise<void>;canWrite:boolean}) {
 const [edit,setEdit]=useState<ServerNode | 'new' | null>(null);
 const [form,setForm]=useState<NodeForm>(emptyNode);
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 const [health,setHealth]=useState<Record<string,unknown> | null>(null);
 const [query,setQuery]=useState('');
 function open(node:ServerNode | 'new') {
  setError('');setEdit(node);
  setForm(node==='new'?emptyNode():{...emptyNode(),...node,agentSecret:'',dns:(node.dnsServers||[]).join(', '),
    subnetIpv4:node.subnetIpv4||'',mtu:node.mtu||1420,maxPeers:node.maxPeers||250});
 }
 async function action(path:string,data?:unknown) {
  setBusy(true);setError('');
  try {const result=await api(path,{method:'POST',...(data?{body:JSON.stringify(data)}:{})});await refresh();return result;}
  catch(e){setError(e instanceof Error?e.message:'Operation failed');return null;}
  finally{setBusy(false);}
 }
 async function save(event:React.FormEvent) {
  event.preventDefault();setBusy(true);setError('');
  try {
   const {dns,agentSecret,...fields}=form;
   const data={...fields,dnsServers:dns.split(',').map(x=>x.trim()).filter(Boolean),...(agentSecret?{agentSecret}:{})};
   await api(edit==='new'?'/servers':`/servers/${(edit as ServerNode).id}`,{method:edit==='new'?'POST':'PUT',body:JSON.stringify(data)});
   setEdit(null);await refresh();
  }catch(e){setError(e instanceof Error?e.message:'Could not save node');}finally{setBusy(false);}
 }
 async function remove(node:ServerNode) {
  if(!window.confirm(`Delete ${node.name}? Its registered peer leases will also be removed.`))return;
  setBusy(true);setError('');
  try{await api(`/servers/${node.id}`,{method:'DELETE'});await refresh();}
  catch(e){setError(e instanceof Error?e.message:'Could not delete node');}finally{setBusy(false);}
 }
 return <section className="console-section">
  <div className="section-heading"><div><h2>VPN nodes</h2><p>Only enabled nodes with a recent successful health check appear in the app.</p></div>
   {canWrite&&<button className="primary" onClick={()=>open('new')}>Add node</button>}</div>
  {error&&<p className="error" role="alert">{error}</p>}
  <input aria-label="Search nodes" placeholder="Search name, country or endpoint" value={query} onChange={e=>setQuery(e.target.value)}/>
  <div className="node-grid">{nodes.filter(n=>`${n.name} ${n.countryName} ${n.endpoint}`.toLowerCase().includes(query.toLowerCase())).map(n=>{
   const fresh=n.healthCheckedAt&&Date.now()-Date.parse(n.healthCheckedAt)<120000;
   return <article className="panel node-card" key={n.id}><div className="card-heading"><h3>{n.flagEmoji} {n.name}</h3>
    <span className={`badge ${n.active&&n.healthy&&fresh?'healthy':'offline'}`}>{!n.active?'Disabled':n.healthy&&fresh?'Healthy':'Needs attention'}</span></div>
    <p>{n.city}, {n.countryName}</p><code>{n.endpoint}</code>
    <dl><dt>Plan</dt><dd>{n.isPremiumOnly?'Premium':'Free'}</dd><dt>Peer load</dt><dd>{n.currentLoad}% / {n.maxPeers??'Unknown'} capacity</dd>
    <dt>Public IP</dt><dd>{n.publicIpv4||'Not verified'}</dd><dt>Last health check</dt><dd>{n.healthCheckedAt?new Date(n.healthCheckedAt).toLocaleString():'Not checked'}</dd></dl>
    <div className="actions"><button disabled={busy} onClick={async()=>{const r=await action(`/servers/${n.id}/ping`);if(r)setHealth(r.health);}}>Test node</button>
     {canWrite&&<><button disabled={busy} onClick={()=>open(n)}>Edit</button><button disabled={busy} onClick={async()=>{
       if(window.confirm('Adopt this node’s current public key? Existing clients must reconnect.')){const r=await action(`/servers/${n.id}/sync-public-key`);if(r)setHealth(r.health);}
     }}>Sync key</button><button className="danger" disabled={busy} onClick={()=>remove(n)}>Delete</button></>}</div></article>;
  })}</div>
  {!nodes.length&&<div className="empty">No nodes have been added.</div>}
  {health&&<div className="modal-backdrop"><section className="panel modal" role="dialog" aria-modal="true" aria-label="Node test result">
    <h2>Node test result</h2><p>These are the actual agent measurements and client configuration values.</p>
    <pre>{JSON.stringify(health,null,2)}</pre><button onClick={()=>setHealth(null)}>Close</button></section></div>}
  {edit&&<div className="modal-backdrop"><form className="panel modal" onSubmit={save} role="dialog" aria-modal="true" aria-label="Node form">
   <h2>{edit==='new'?'Add VPN node':'Edit VPN node'}</h2><div className="form-grid">
    {(['name','countryCode','countryName','city','flagEmoji','endpoint','publicKey','subnetIpv4','dns','agentUrl','agentSecret'] as const).map(k=><label key={k}>
     {{name:'Node name',countryCode:'Country code (2 letters)',countryName:'Country',city:'City',flagEmoji:'Flag',endpoint:'Public endpoint: host:UDP port',publicKey:'Current WireGuard public key',subnetIpv4:'Tunnel subnet (/24)',dns:'DNS resolvers (comma separated)',agentUrl:'Agent URL',agentSecret:'Credential reference (env:VARIABLE)'}[k]}
     <input required={k!=='agentSecret'||edit==='new'} value={form[k]} onChange={e=>setForm({...form,[k]:e.target.value})}/></label>)}
    {(['mtu','maxPeers'] as const).map(k=><label key={k}>{k==='mtu'?'MTU':'Maximum peers'}<input type="number" required min={k==='mtu'?1280:1} max={k==='mtu'?1420:253} value={form[k]} onChange={e=>setForm({...form,[k]:Number(e.target.value)})}/></label>)}
   </div><p className="hint">Use the node’s public endpoint and UDP port. Credentials stay in backend environment variables. Leave the reference blank when editing to retain it.</p>
   <div className="actions">{(['active','isPremiumOnly','isRecommended'] as const).map(k=><label className="checkbox" key={k}><input type="checkbox" checked={form[k]} onChange={e=>setForm({...form,[k]:e.target.checked})}/>{k==='active'?'Enabled':k==='isPremiumOnly'?'Premium only':'Recommended'}</label>)}</div>
   {error&&<p className="error" role="alert">{error}</p>}<div className="actions"><button className="primary" disabled={busy}>{busy?'Saving…':'Save node'}</button><button type="button" disabled={busy} onClick={()=>setEdit(null)}>Cancel</button></div>
  </form></div>}
 </section>;
}
