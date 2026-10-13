import {readSnapshot} from '../services/readSnapshot';
it('coalesces ten simultaneous aggregate reads and refreshes only after expiry',async()=>{
 jest.useFakeTimers();
 try{const load=jest.fn().mockResolvedValue({count:3});const cache=readSnapshot(2000,load);
 const rows=await Promise.all(Array.from({length:10},()=>cache.read()));expect(rows).toHaveLength(10);expect(load).toHaveBeenCalledTimes(1);
 await cache.read();expect(load).toHaveBeenCalledTimes(1);
 jest.advanceTimersByTime(2001);await cache.read();expect(load).toHaveBeenCalledTimes(2);
 }finally{jest.useRealTimers();}
});
it('does not cache an exception and allows dependency recovery',async()=>{
 const load=jest.fn().mockRejectedValueOnce(new Error('unavailable')).mockResolvedValue('recovered');const cache=readSnapshot(2000,load);
 await expect(cache.read()).rejects.toThrow('unavailable');expect(await cache.read()).toBe('recovered');
});
it('does not let an old in-flight result overwrite an invalidated read',async()=>{
 let finish!:(v:string)=>void;const load=jest.fn().mockImplementationOnce(()=>new Promise(r=>finish=r)).mockResolvedValue('new');
 const cache=readSnapshot(2000,load);const old=cache.read();await Promise.resolve();cache.invalidate();expect(await cache.read()).toBe('new');finish('old');await old;expect(await cache.read()).toBe('new');
});
