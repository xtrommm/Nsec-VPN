import { prisma } from '../db';
const select = { id:true,name:true,countryCode:true,countryName:true,city:true,flagEmoji:true,currentLoad:true,
  isPremiumOnly:true,isRecommended:true,endpoint:true,publicIpv4:true,healthCheckedAt:true } as const;
type Rows = Awaited<ReturnType<typeof load>>;
const load = () => prisma.serverNode.findMany({ where:{active:true,healthy:true,healthCheckedAt:{gt:new Date(Date.now()-120000)}},
  select,orderBy:[{sortOrder:'asc'},{isRecommended:'desc'},{currentLoad:'asc'}] });
let cached:Rows|null=null,expiry=0,generation=0;
let pending:{generation:number;promise:Promise<Rows>}|null=null;
export function invalidateServerCatalog(){generation++;cached=null;expiry=0;pending=null;}
export async function healthyServerCatalog(){
  const now=Date.now();
  if(!cached||expiry<=now){
    const version=generation;
    if(!pending||pending.generation!==version){
      const promise=load().then(rows=>{if(version===generation){cached=rows;expiry=Date.now()+2000;}return rows;});
      const request={generation:version,promise};pending=request;
      void promise.finally(()=>{if(pending===request)pending=null;}).catch(()=>undefined);
    }
    const rows=await pending.promise;
    return rows.filter(row=>!!row.healthCheckedAt&&row.healthCheckedAt.getTime()>Date.now()-120000);
  }
  return cached.filter(row=>!!row.healthCheckedAt&&row.healthCheckedAt.getTime()>now-120000);
}
