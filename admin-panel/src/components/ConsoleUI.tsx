import { ReactNode, useEffect, useRef } from 'react';
export type Notify = (message: string, type?: 'success' | 'error') => void;
export function Pager({page,pages,total,onPage}:{page:number;pages:number;total:number;onPage:(n:number)=>void}) {
 return <div className="pager"><span>{total} records · Page {page} of {pages}</span><div className="actions"><button disabled={page<=1} onClick={()=>onPage(page-1)}>Previous</button><button disabled={page>=pages} onClick={()=>onPage(page+1)}>Next</button></div></div>;
}
export function Dialog({title,onClose,children,busy=false}:{title:string;onClose:()=>void;children:ReactNode;busy?:boolean}) {
 const ref=useRef<HTMLElement>(null);const close=useRef(onClose);close.current=onClose;
 useEffect(()=>{
  const previous=document.activeElement as HTMLElement|null;const panel=ref.current;
  const selector='button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]';
  (panel?.querySelector(selector) as HTMLElement|null)?.focus();
  const key=(e:KeyboardEvent)=>{if(e.key==='Escape'&&!busy)close.current();if(e.key==='Tab'){
   const items=Array.from(panel?.querySelectorAll<HTMLElement>(selector)||[]).filter(el=>el.getClientRects().length);
   if(!items.length){e.preventDefault();return;}const first=items[0],last=items[items.length-1];
   if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}
   else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}
  }};
  document.addEventListener('keydown',key);return()=>{document.removeEventListener('keydown',key);previous?.focus();};
 },[busy]);
 return <div className="modal-backdrop"><section ref={ref} className="panel modal" role="dialog" aria-modal="true" aria-label={title}><div className="section-heading"><h2>{title}</h2><button aria-label="Close dialog" disabled={busy} onClick={onClose}>Close</button></div>{children}</section></div>;
}
export function ConfirmDialog({title,message,busy,onCancel,onConfirm}:{title:string;message:string;busy:boolean;onCancel:()=>void;onConfirm:()=>void}) {
 return <Dialog title={title} onClose={onCancel} busy={busy}><p>{message}</p><div className="actions"><button disabled={busy} onClick={onCancel}>Cancel</button><button className="danger" disabled={busy} onClick={onConfirm}>{busy?'Working…':'Confirm'}</button></div></Dialog>;
}
export function LoadingRows({columns=5}:{columns?:number}){return <tbody aria-label="Loading records">{[0,1,2].map(i=><tr key={i}>{Array.from({length:columns},(_,j)=><td key={j}><span className="skeleton"/></td>)}</tr>)}</tbody>;}
export async function copyText(value:string,notify:Notify){try{await navigator.clipboard.writeText(value);notify('Copied to clipboard');}catch{notify('Clipboard unavailable. Select and copy the text manually.','error');}}
export const errorText=(e:unknown)=>e instanceof Error?e.message:'Request failed. Try again.';
