/** Short-lived read-only snapshots. Caller must authenticate before invoking. */
export function readSnapshot<T>(ttlMs:number, load:()=>Promise<T>) {
  let value:T|undefined,hasValue=false,expires=0,version=0;
  let pending:Promise<T>|null=null;
  const read=()=>{
    if(hasValue&&Date.now()<expires)return Promise.resolve(value as T);
    if(pending)return pending;
    const generation=version;
    const task=Promise.resolve().then(load).then(result=>{
      if(generation===version){value=result;hasValue=true;expires=Date.now()+ttlMs;}
      return result;
    });
    pending=task;
    void task.finally(()=>{if(pending===task)pending=null;}).catch(()=>undefined);
    return task;
  };
  return {read,invalidate:()=>{version++;hasValue=false;value=undefined;pending=null;expires=0;}};
}
