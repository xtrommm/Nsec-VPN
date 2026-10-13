const { test, after } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const cp = require('node:child_process');
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'nsec-agent-test-'));
process.env.AGENT_STATE_DIR = dir;
process.env.AGENT_SECRET = crypto.randomBytes(32).toString('hex');
const calls = [];
let configured = '', failSave = false;
const original = cp.execFile;
cp.execFile = (command, args, options, callback) => {
  calls.push({ command, args });
  let stdout = '';
  if (command === 'ip') stdout = '2: wg0 inet 10.8.0.1/24 scope global wg0';
  if (command === 'wg' && args[0] === 'show') stdout = configured;
  if (command === 'wg' && args[0] === 'set') configured = args.at(-1) === 'remove' ? '' : `${args[3]}\t${args[5]}\n`;
  if (command === 'wg-quick' && failSave) { failSave = false; return callback(new Error('save failed')); }
  callback(null, stdout, '');
};
cp.execFile[require('node:util').promisify.custom] = (command,args,options) => new Promise((resolve,reject) => cp.execFile(command,args,options,(error,stdout,stderr) => error ? reject(error) : resolve({stdout,stderr})));
const app = require('../dist/index').default;
const handler = app._router.stack.find(layer => layer.route?.path === '/api/v1/agent/peers' && layer.route.methods.post).route.stack.at(-1).handle;
async function add(key) {
  let status = 200, body;
  const res = { status(value) { status = value; return this; }, json(value) { body = value; return this; } };
  await handler({ body: { publicKey: key, allocatedIp: '10.8.0.2/32', expiresAt: new Date(Date.now() + 60000).toISOString() } }, res);
  return { status, body };
}
test('confirms kernel peer and saves before success; uses execFile argument arrays', async () => {
  const key = Buffer.alloc(32, 8).toString('base64');
  assert.deepEqual(await add(key), { status: 200, body: { success: true, persisted: true } });
  assert.equal(JSON.parse(fs.readFileSync(path.join(dir,'leases.json')))[key].allocatedIp, '10.8.0.2/32');
  assert.ok(calls.some(call => call.command === 'wg-quick' && call.args.join(' ') === 'save wg0'));
  const set = calls.find(call => call.command === 'wg' && call.args[0] === 'set');
  assert.deepEqual(set.args, ['set','wg0','peer',key,'allowed-ips','10.8.0.2/32']);
});
test('persistence failure returns error and removes unconfirmed peer', async () => {
  configured = ''; failSave = true;
  const key = Buffer.alloc(32, 9).toString('base64');
  assert.equal((await add(key)).status, 503);
  assert.ok(calls.some(call => call.command === 'wg' && call.args[3] === key && call.args.at(-1) === 'remove'));
  assert.equal(JSON.parse(fs.readFileSync(path.join(dir,'leases.json')))[key], undefined);
});
test('malformed key never reaches command runner', async () => {
  const before = calls.length;
  assert.equal((await add('bad;whoami')).status, 400);
  assert.equal(calls.length, before);
});
after(() => { cp.execFile = original; fs.rmSync(dir, { recursive: true, force: true }); });
test('authentication binds method/path/body and rejects replay without invoking commands', () => {
  const route = app._router.stack.find(layer=>layer.route?.path==='/api/v1/agent/peers' && layer.route.methods.post).route;
  const auth = route.stack[0].handle;
  const timestamp = Date.now().toString();
  const body = {publicKey:Buffer.alloc(32,10).toString('base64'),allocatedIp:'10.8.0.3/32'};
  const signature = crypto.createHmac('sha256',process.env.AGENT_SECRET).update(`${timestamp}.POST./api/v1/agent/peers.${JSON.stringify(body)}`).digest('hex');
  const headers = {'X-Agent-Timestamp':timestamp,'X-Agent-Signature':signature,'X-Agent-Version':'2'};
  const req = {method:'POST',path:'/api/v1/agent/peers',body,get:name=>headers[name]};
  let code = 200, accepted = 0;
  const res={status(value){code=value;return this;},json(){return this;}};
  auth({...req,path:'/api/v1/agent/health'},res,()=>accepted++);
  assert.equal(code,403);assert.equal(accepted,0);
  auth(req,res,()=>accepted++);assert.equal(accepted,1);
  auth(req,res,()=>accepted++);assert.equal(code,401);assert.equal(accepted,1);
});

test('legacy unbound signatures are disabled unless explicitly enabled for migration', () => {
  const auth=app._router.stack.find(layer=>layer.route?.path==='/api/v1/agent/peers' && layer.route.methods.post).route.stack[0].handle;
  let code=200, accepted=false;
  const res={status(value){code=value;return this;},json(){return this;}};
  delete process.env.ALLOW_LEGACY_AGENT_V1;
  auth({get:()=>undefined},res,()=>accepted=true);
  assert.equal(code,426);assert.equal(accepted,false);
});

test('missing, null, array or scalar signed bodies fail before command execution', async () => {
  const before = calls.length;
  for (const body of [undefined,null,[],true,'invalid']) {
    let code = 200;
    const res = { status(value) { code = value; return this; }, json() { return this; } };
    await handler({body},res);
    assert.equal(code,400);
  }
  assert.equal(calls.length,before);
});
