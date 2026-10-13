const {test, before, after} = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
// This harness refuses a production-looking database and creates isolated records only.
const database = new URL(process.env.DATABASE_URL || 'file:invalid').pathname;
if (!/(test|_ci)/.test(database)) throw new Error('Use a dedicated test database whose name contains test or _ci');
const {prisma} = require('../dist/db');
const {AgentClient} = require('../dist/services/agentClient');
const health = require('../dist/services/nodeHealth');
const {provisionPeer, disconnectPeer} = require('../dist/services/peerProvisioning');
const tag = crypto.randomUUID();
let node, devices;
const key = byte => Buffer.alloc(32,byte).toString('base64');
before(async () => {
  node = await prisma.serverNode.create({data:{name:'Integration test',countryCode:'US',countryName:'Test',city:'Test',flagEmoji:'US',endpoint:'47.85.109.170:51820',publicKey:key(7),agentUrl:'http://test.invalid',agentSecretHash:crypto.randomBytes(32).toString('hex')}});
  devices = await Promise.all([0,1,2].map(i=>prisma.device.create({data:{deviceId:`test-${tag}-${i}`}})));
  health.testNode = async () => ({online:true,agentOnline:true,publicIpv4:'47.85.109.170'});
  AgentClient.addPeer = async () => {};
  AgentClient.removePeer = async () => {};
});
test('concurrent transactions allocate unique addresses and repeated device connect is idempotent', async () => {
  const results = await Promise.all(devices.slice(0,2).map((d,i)=>provisionPeer(d.id,node.id,key(i+10),'FREE')));
  assert.notEqual(results[0].tunnelConfig.clientAddress,results[1].tunnelConfig.clientAddress);
  const again = await provisionPeer(devices[0].id,node.id,key(10),'FREE');
  assert.notEqual(again.peerId,results[0].peerId);
  assert.equal(await prisma.peerSession.count({where:{serverId:node.id,deviceId:devices[0].id,active:true}}),1);
});
test('confirmed compensation releases a failed PostgreSQL reservation', async () => {
  AgentClient.addPeer = async () => {throw new Error('integration failure');};
  await assert.rejects(provisionPeer(devices[2].id,node.id,key(12),'FREE'),/AGENT_UNREACHABLE/);
  assert.equal(await prisma.peerSession.count({where:{serverId:node.id,deviceId:devices[2].id}}),0);
});
test('an ambiguous timeout keeps its IP reserved until confirmed reconciliation', async () => {
  AgentClient.addPeer = async () => {throw new Error('ambiguous timeout');};
  AgentClient.removePeer = async () => {throw new Error('unreachable');};
  await assert.rejects(provisionPeer(devices[2].id,node.id,key(12),'FREE'),/AGENT_UNREACHABLE/);
  const pending = await prisma.peerSession.findFirst({where:{serverId:node.id,deviceId:devices[2].id,active:true}});
  assert.equal(pending.pendingPublicKey,key(12));
  AgentClient.removePeer = async () => {};
  await disconnectPeer(devices[2].id,pending.peerId);
  assert.equal(await prisma.peerSession.count({where:{id:pending.id}}),0);
});
after(async () => {
  if(node) await prisma.serverNode.delete({where:{id:node.id}});
  if(devices) await prisma.device.deleteMany({where:{id:{in:devices.map(d=>d.id)}}});
  await prisma.$disconnect();
});
