jest.mock('../secrets', () => ({ JWT_SECRET: require('crypto').randomBytes(32).toString('hex') }));
jest.mock('../db', () => ({ prisma: { $transaction: jest.fn(), serverNode: { findUniqueOrThrow: jest.fn(), updateMany: jest.fn() } } }));
jest.mock('../services/agentClient', () => ({ AgentClient: { getNodeHealth: jest.fn() } }));
jest.mock('dns/promises', () => ({ lookup: jest.fn().mockResolvedValue([{address:'47.85.109.170'}]) }));
import { prisma } from '../db';
import { AgentClient } from '../services/agentClient';
import { testNode } from '../services/nodeHealth';
import admin from '../routes/admin';
import { DeviceIdSchema } from '../types';
import { csvCell } from '../services/csv';
const node: any = {id:'node',active:true,healthy:false,healthCheckedAt:null,endpoint:'47.85.109.170:51820',publicKey:Buffer.alloc(32,8).toString('base64'),
  agentUrl:'http://127.0.0.1:51821',agentSecretHash:'env:TEST_SECRET',subnetIpv4:'10.8.0.0/24',maxPeers:250,mtu:1420,dnsServers:['1.1.1.1']};
const route = (method:string,path:string) => (admin as any).stack.find((layer:any)=>layer.route?.path===path && layer.route.methods[method]).route.stack.at(-1).handle;
const response = () => {const r:any={code:200};r.status=(code:number)=>{r.code=code;return r;};r.json=(body:unknown)=>{r.body=body;return r;};return r;};
beforeEach(()=>{jest.clearAllMocks();(AgentClient.getNodeHealth as jest.Mock).mockResolvedValue({online:true,publicKey:node.publicKey,publicIpv4:'47.85.109.170',listenPort:51820,peerCount:0});});
it('health updates compare credential, subnet, capacity and resolver inputs, not just endpoint/key', async()=>{
  (prisma.serverNode.updateMany as jest.Mock).mockResolvedValue({count:0});
  const result=await testNode(node);
  expect(result.online).toBe(false);expect(result.error).toBe('NODE_CHANGED');
  expect(prisma.serverNode.updateMany).toHaveBeenCalledWith(expect.objectContaining({where:expect.objectContaining({agentSecretHash:node.agentSecretHash,subnetIpv4:node.subnetIpv4,maxPeers:250,dnsServers:{equals:node.dnsServers}})}));
});
it('successful create is not reported as a failed write when its later health probe fails', async()=>{
  const tx:any={$executeRaw:jest.fn(),serverNode:{findFirst:jest.fn().mockResolvedValue(null),create:jest.fn().mockResolvedValue(node)},auditLog:{create:jest.fn()}};
  (prisma.$transaction as jest.Mock).mockImplementation(work=>work(tx));
  (AgentClient.getNodeHealth as jest.Mock).mockRejectedValue(new Error('unavailable'));
  const res=response();await route('post','/servers')({body:{...node,agentSecret:'env:TEST_SECRET'},admin:{adminId:'admin'}},res);
  expect(res.code).toBe(201);expect(res.body.success).toBe(true);expect(res.body.healthCheckError).toBe('NODE_HEALTH_CHECK_UNAVAILABLE');
  expect(res.body.server.healthy).toBe(false);expect(res.body.server.agentSecretHash).toBeUndefined();
});
it('update distinguishes a missing node from a database outage', async()=>{
  for(const [code,status] of [['P2025',404],['P1001',503]] as const){
    (prisma.$transaction as jest.Mock).mockRejectedValueOnce({code});
    const res=response();await route('put','/servers/:id')({params:{id:'node'},body:{name:'Name'},admin:{adminId:'admin'}},res);expect(res.code).toBe(status);
  }
});
it('admin ID validation accepts exactly the safe ID format used by registration, including legacy IDs', ()=>{
  expect(DeviceIdSchema.safeParse('legacy-device-123').success).toBe(true);
  expect(DeviceIdSchema.safeParse('af8f6a42-0fd5-494f-b874-355b5579c11e').success).toBe(true);
  expect(DeviceIdSchema.safeParse('../not-safe').success).toBe(false);
});
it('CSV exports neutralize whitespace/control-prefixed formulas and quote content', ()=>{
  for(const input of ['=1+1','  +1','\t=1','\r\n@SUM(1)','\uFEFF-1']) expect(csvCell(input).startsWith('"\'')).toBe(true);
  expect(csvCell('Valid "value"')).toBe('"Valid ""value"""');
});
