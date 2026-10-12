import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(new URL('../src/lib/nodeValidation.ts', import.meta.url), 'utf8');
const moduleText = ts.transpile(source, { module: ts.ModuleKind.ES2022 });
const { validateNode } = await import('data:text/javascript;base64,' + Buffer.from(moduleText).toString('base64'));
const valid = { endpoint: '47.85.109.170:51820', publicKey: Buffer.alloc(32,7).toString('base64'), subnetIpv4: '10.8.0.0/24', dnsServers: ['1.1.1.1'], agentUrl: 'http://47.85.109.170:51821' };
test('accepts real public endpoint', () => assert.deepEqual(validateNode(valid), []));
test('rejects private endpoint, agent UDP port, invalid key and subnet', () => {
  for (const endpoint of ['172.30.148.192:51820','47.85.109.170:51821','47.85.109.170:65536']) assert.ok(validateNode({ ...valid, endpoint }).length > 0);
  assert.ok(validateNode({ ...valid, publicKey: 'invalid' }).length > 0);
  assert.ok(validateNode({ ...valid, subnetIpv4: 'invalid' }).length > 0);
});
