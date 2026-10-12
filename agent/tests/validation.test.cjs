const { test } = require('node:test');
const assert = require('node:assert/strict');
process.env.AGENT_SECRET = require('node:crypto').randomBytes(32).toString('hex');
const { validAddress, validKey, publicIpv4 } = require('../dist/index');
test('rejects command injection and malformed keys', () => {
  for (const key of ['; rm -rf /', 'abc', 'x'.repeat(44)]) assert.equal(validKey(key), false);
  assert.equal(validKey(Buffer.alloc(32,7).toString('base64')), true);
});
test('accepts actual private tunnel /32 but rejects malformed or public allocations', () => {
  assert.equal(validAddress('10.8.0.2/32'), true);
  for (const value of ['10.999.0.2/32', '10.8.0.1/32', '10.8.0.255/32', '10.8.0.2/24', '47.85.109.170/32', '10.8.0.2/32;whoami']) assert.equal(validAddress(value), false);
});
test('public endpoint policy excludes private and documentation addresses', () => {
  assert.equal(publicIpv4('47.85.109.170'), true);
  for (const address of ['172.30.148.192','203.0.113.1','10.8.0.1']) assert.equal(publicIpv4(address), false);
});
