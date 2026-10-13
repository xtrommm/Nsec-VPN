jest.mock('../db',()=>({prisma:{serverNode:{findMany:jest.fn(),updateMany:jest.fn()}}}));
jest.mock('../services/agentClient',()=>({AgentClient:{getNodeHealth:jest.fn()}}));
jest.mock('dns/promises',()=>({lookup:jest.fn()}));
import {prisma} from '../db';
import {AgentClient} from '../services/agentClient';
import {lookup} from 'dns/promises';
import {refreshNodeHealth,testNode} from '../services/nodeHealth';
const node:any={id:'node',endpoint:'47.85.109.170:51820',subnetIpv4:'10.8.0.0/24',publicKey:Buffer.alloc(32,2).toString('base64'),maxPeers:100,dnsServers:['1.1.1.1'],active:true};
const health={online:true,publicIpv4:'47.85.109.170',listenPort:51820,publicKey:node.publicKey,peerCount:1};
beforeEach(()=>{jest.clearAllMocks();(prisma.serverNode.updateMany as jest.Mock).mockResolvedValue({count:1});(lookup as jest.Mock).mockResolvedValue([{address:'47.85.109.170'}]);});
it('starts a ninth fast-node probe without waiting for an earlier slow peer and prevents overlapping jobs',async()=>{
 let release!:(value:unknown)=>void;let calls=0;
 (prisma.serverNode.findMany as jest.Mock).mockResolvedValue(Array.from({length:9},(_,i)=>({...node,id:String(i)})));
 (AgentClient.getNodeHealth as jest.Mock).mockImplementation(()=>++calls===1?new Promise(r=>release=r):Promise.resolve(health));
 const job=refreshNodeHealth();await refreshNodeHealth();
 for(let i=0;i<25;i++)await Promise.resolve();
 expect(calls).toBe(9);expect(prisma.serverNode.findMany).toHaveBeenCalledTimes(1);
 release(health);await job;
});
it('an unresolved DNS lookup fails node validation after the named two-second deadline',async()=>{
 jest.useFakeTimers();(AgentClient.getNodeHealth as jest.Mock).mockResolvedValue(health);(lookup as jest.Mock).mockImplementation(()=>new Promise(()=>undefined));
 try{const job=testNode(node);await Promise.resolve();await jest.advanceTimersByTimeAsync(2000);expect((await job).online).toBe(false);}
 finally{jest.useRealTimers();}
});
