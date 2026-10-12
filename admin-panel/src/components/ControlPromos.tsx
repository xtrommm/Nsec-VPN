import { useCallback, useEffect, useState } from 'react';
import { ConsoleApi } from './ControlNodes';
type Promo={id:string;label:string;passMinutes:number;maxRedemptions:number;redemptionCount:number;expiresAt:string|null;active:boolean};
export function ControlPromos({api,canWrite}:{api:ConsoleApi;canWrite:boolean}) {
 const [promos,setPromos]=useState<Promo[]>([]);const [busy,setBusy]=useState(false);const [error,setError]=useState('');
 const [label,setLabel]=useState('');const [code,setCode]=useState('');const [minutes,setMinutes]=useState(30);const [limit,setLimit]=useState(1);const [expires,setExpires]=useState('');
 const [createdCode,setCreatedCode]=useState<string|null>(null);
 const load=useCallback(async()=>{try{setPromos((await api('/promos')).promos);setError('');}catch(e){setError(e instanceof Error?e.message:'Could not load promotions');}},[api]);
 useEffect(()=>{void load();},[load]);
 async function create(e:React.FormEvent){e.preventDefault();setBusy(true);setError('');setCreatedCode(null);
  try{const r=await api('/promos',{method:'POST',body:JSON.stringify({label,passMinutes:minutes,maxRedemptions:limit,
   expiresAt:expires?new Date(expires).toISOString():null,...(code.trim()?{code:code.trim()}:{})})});
   setCreatedCode(r.code);setLabel('');setCode('');await load();
  }catch(e){setError(e instanceof Error?e.message:'Could not create promotion');}finally{setBusy(false);}}
 async function toggle(p:Promo){setBusy(true);try{await api(`/promos/${p.id}`,{method:'PUT',body:JSON.stringify({active:!p.active})});await load();}
 catch(e){setError(e instanceof Error?e.message:'Could not update promotion');}finally{setBusy(false);}}
 return <section className="console-section"><div className="section-heading"><div><h2>Promo codes</h2><p>Time-limited ad-free premium access. One redemption per device, with a shared campaign limit.</p></div><button disabled={busy} onClick={()=>void load()}>Refresh</button></div>
 {error&&<p className="error" role="alert">{error}</p>}
 {createdCode&&<div className="success" role="status"><strong>Code created — save it now</strong><code>{createdCode}</code><p>Only its hash is stored. The full code cannot be retrieved later.</p><button onClick={()=>setCreatedCode(null)}>Dismiss</button></div>}
 {canWrite&&<form className="panel" onSubmit={create}><h3>Create promotion</h3><div className="form-grid">
  <label>Campaign label<input required maxLength={80} value={label} onChange={e=>setLabel(e.target.value)}/></label>
  <label>Custom code (optional)<input pattern="[A-Za-z0-9-]{6,32}" value={code} onChange={e=>setCode(e.target.value)} placeholder="Leave blank to generate securely"/></label>
  <label>Pass duration (minutes)<input required type="number" min="1" max="525600" value={minutes} onChange={e=>setMinutes(Number(e.target.value))}/></label>
  <label>Maximum redemptions<input required type="number" min="1" max="1000000" value={limit} onChange={e=>setLimit(Number(e.target.value))}/></label>
  <label>Campaign expiry (your local time)<input type="datetime-local" value={expires} onChange={e=>setExpires(e.target.value)}/></label>
 </div><button className="primary" disabled={busy}>{busy?'Saving…':'Create code'}</button></form>}
 <div className="panel table-scroll"><table><thead><tr><th>Campaign</th><th>Pass</th><th>Redeemed</th><th>Expiry</th><th>Status</th>{canWrite&&<th>Action</th>}</tr></thead>
 <tbody>{promos.map(p=><tr key={p.id}><td>{p.label}</td><td>{p.passMinutes} min</td><td>{p.redemptionCount} / {p.maxRedemptions}</td><td>{p.expiresAt?new Date(p.expiresAt).toLocaleString():'No campaign expiry'}</td>
 <td>{!p.active?'Disabled':p.expiresAt&&Date.parse(p.expiresAt)<=Date.now()?'Expired':p.redemptionCount>=p.maxRedemptions?'Exhausted':'Active'}</td>{canWrite&&<td><button disabled={busy} onClick={()=>void toggle(p)}>{p.active?'Disable':'Enable'}</button></td>}</tr>)}</tbody></table>
 {!promos.length&&<p className="empty">No promotions found.</p>}<p className="hint">Showing up to the latest 500 campaigns. Disabling a campaign prevents new redemptions; it does not revoke passes already granted.</p></div></section>;
}
