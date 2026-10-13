jest.mock('../db', () => ({ prisma: { serverNode: { findMany: jest.fn() } } }));
import { prisma } from '../db';
import { healthyServerCatalog, invalidateServerCatalog } from '../services/serverCatalog';
const read = prisma.serverNode.findMany as jest.Mock;
const row = (id:string, age=0) => ({id,healthCheckedAt:new Date(Date.now()-age)});
beforeEach(()=>{jest.clearAllMocks();invalidateServerCatalog();jest.useFakeTimers();});
afterEach(()=>jest.useRealTimers());
it('coalesces concurrent cold reads, expires after two seconds and never selects agent credentials',async()=>{
 read.mockResolvedValue([row('real-node')]);
 const [a,b]=await Promise.all([healthyServerCatalog(),healthyServerCatalog()]);
 expect(a).toEqual(b);expect(read).toHaveBeenCalledTimes(1);
 expect(read.mock.calls[0][0].select).not.toHaveProperty('agentSecretHash');
 await healthyServerCatalog();expect(read).toHaveBeenCalledTimes(1);
 jest.advanceTimersByTime(2001);await healthyServerCatalog();expect(read).toHaveBeenCalledTimes(2);
});
it('cannot repopulate the cache with an invalidated in-flight read',async()=>{
 let resolve!:(v:unknown)=>void;
 read.mockImplementationOnce(()=>new Promise(r=>resolve=r));
 const old=healthyServerCatalog();invalidateServerCatalog();
 read.mockResolvedValue([row('updated-node')]);await healthyServerCatalog();
 resolve([row('old-node')]);await old;
 expect((await healthyServerCatalog())[0].id).toBe('updated-node');expect(read).toHaveBeenCalledTimes(2);
});
it('filters health that becomes stale even while the catalog itself is cached',async()=>{
 read.mockResolvedValue([row('soon-stale',119500),row('fresh')]);
 expect(await healthyServerCatalog()).toHaveLength(2);
 jest.advanceTimersByTime(501);expect((await healthyServerCatalog()).map(x=>x.id)).toEqual(['fresh']);
});
it('retries a rejected read rather than caching a failure',async()=>{
 read.mockRejectedValueOnce(new Error('database unavailable')).mockResolvedValue([row('recovered')]);
 await expect(healthyServerCatalog()).rejects.toThrow('database unavailable');
 expect((await healthyServerCatalog())[0].id).toBe('recovered');
});
