import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { ShieldCheck, KeyRound } from 'lucide-react';
import { ConsoleApi } from './ControlNodes';
import { Notify, errorText } from './ConsoleUI';
export function ControlSecurity({api,notify,onToken}:{api:ConsoleApi;notify:Notify;onToken:(token:string)=>void}) {
 const [status,setStatus]=useState<{enabled:boolean;enrollmentConfigured:boolean}|null>(null);
 const [password,setPassword]=useState('');const [code,setCode]=useState('');const [busy,setBusy]=useState(false);const [error,setError]=useState('');
 const [setup,setSetup]=useState<{secret:string;authenticatorUri:string;expiresAt:string}|null>(null);const [qr,setQr]=useState('');
 useEffect(()=>{const c=new AbortController();void api('/mfa/status',{signal:c.signal}).then(setStatus).catch(e=>{if(!c.signal.aborted)setError(errorText(e));});return()=>c.abort();},[api]);
 useEffect(()=>{let active=true;setQr('');if(setup)void QRCode.toDataURL(setup.authenticatorUri,{width:240,margin:2}).then(v=>{if(active)setQr(v);}).catch(()=>{if(active)setError('Cannot generate QR. Use the private setup key below.');});return()=>{active=false;};},[setup]);
 async function submit(e:React.FormEvent){e.preventDefault();if(busy)return;setBusy(true);setError('');try{
  if(!setup){const r=await api('/mfa/enrollment',{method:'POST',body:JSON.stringify({password})});setSetup(r.data);}
  else{const r=await api('/mfa/enable',{method:'PUT',body:JSON.stringify({password,code})});setSetup(null);setPassword('');setCode('');setQr('');setStatus({enabled:true,enrollmentConfigured:true});onToken(r.token);notify('Authenticator enabled. Future sign-ins require its six-digit code.');}
 }catch(e){setError(errorText(e));}finally{setBusy(false);}}
 return <section className="console-section"><div className="section-heading"><div><span className="eyebrow">ACCOUNT SECURITY</span><h2>Authenticator Protection</h2><p>Protect your own administrator account with a second factor.</p></div><span className="badge">{status?.enabled?'Protected':status?'Not enabled':'Checking'}</span></div>
 <div className="security-grid"><section className="panel"><div className="security-heading"><ShieldCheck size={24}/><h3>Two-Factor Authentication</h3></div>{error&&<p className="error" role="alert">{error}</p>}
 {status?.enabled?<div className="healthy-state"><ShieldCheck size={40}/><strong>Authenticator Enabled</strong><p>Open your authenticator app for the current six-digit code when signing in. Each code can be used once.</p></div>:!status?<p>Loading security settings…</p>:!status.enrollmentConfigured?<div className="notice"><strong>Server setup required</strong><p>Configure ADMIN_TOTP_ENCRYPTION_KEY in the protected backend environment and apply the migration first. Never enter the server key here.</p></div>:<form onSubmit={submit}>
 <label>Current account password<input type="password" required autoComplete="current-password" maxLength={128} value={password} onChange={e=>setPassword(e.target.value)} disabled={busy}/></label>
 {setup&&<div className="enrollment"><strong>1. Scan in your authenticator app</strong>{qr&&<img src={qr} alt="Private authenticator enrollment QR code" width={240} height={240}/>}
 <a href={setup.authenticatorUri}>Open Authenticator App</a><p className="hint">Use Google Authenticator, Microsoft Authenticator or another TOTP app. Keep this QR and setup key private.</p><details><summary>Cannot scan? Enter the setup key manually</summary><code className="private-key">{setup.secret}</code></details><p className="hint">Setup expires {new Date(setup.expiresAt).toLocaleTimeString()}.</p>
 <label>2. Current six-digit code<input required inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} value={code} onChange={e=>setCode(e.target.value.replace(/\D/g,''))} disabled={busy}/></label></div>}
 <div className="actions"><button className="primary" disabled={busy}>{busy?'Verifying…':setup?'Verify & Enable':'Set Up Authenticator'}</button>{setup&&<button type="button" disabled={busy} onClick={()=>{setSetup(null);setQr('');setPassword('');setCode('');setError('');}}>Cancel setup</button>}</div></form>}
 </section><section className="panel"><div className="security-heading"><KeyRound size={24}/><h3>Security Notes</h3></div><p>The login code comes from your authenticator app, not email or SMS.</p><ul className="security-notes"><li>Your password and a verified code are required.</li><li>Old sessions without MFA stop working when enabled.</li><li>Keep an encrypted backup of your authenticator account.</li><li>Phone lost? Contact your server operator for controlled recovery. This page cannot disable protection.</li></ul></section></div></section>;
}
