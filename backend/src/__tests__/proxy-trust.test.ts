import express from 'express';
import { AddressInfo } from 'net';
import { trustedProxyCidrs } from '../services/proxyTrust';
it('rejects universal trust, hop counts, hostnames and malformed CIDRs', () => {
  for (const value of ['true','1','0.0.0.0/0','::/0','proxy.example/32','127.0.0.1/33','::1/129','999.1.1.1/32'])
    expect(() => trustedProxyCidrs(value)).toThrow();
  expect(trustedProxyCidrs()).toEqual([]);
});
it('uses forwarded client identity only when the actual peer is an explicitly trusted proxy', async () => {
  const app = express();
  app.get('/', (req,res) => res.json({ address:req.ip }));
  const server = app.listen(0,'127.0.0.1');
  await new Promise<void>(resolve => server.once('listening',resolve));
  try {
    const url = `http://127.0.0.1:${(server.address() as AddressInfo).port}/`;
    const headers = {'X-Forwarded-For':'198.51.100.8'};
    expect((await (await fetch(url,{headers})).json() as any).address).toBe('127.0.0.1');
    app.set('trust proxy',trustedProxyCidrs('127.0.0.1/32'));
    expect((await (await fetch(url,{headers})).json() as any).address).toBe('198.51.100.8');
    app.set('trust proxy',trustedProxyCidrs('192.0.2.1/32'));
    expect((await (await fetch(url,{headers})).json() as any).address).toBe('127.0.0.1');
  } finally { await new Promise<void>((resolve,reject) => server.close(error => error ? reject(error) : resolve())); }
});
