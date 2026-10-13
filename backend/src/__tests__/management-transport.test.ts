import { managementOriginAllowed } from '../services/managementTransport';
it('requires TLS for public management while retaining the private SSH-forward origin', () => {
  for (const origin of ['https://node.example.org:51821','http://172.18.0.1:51823','http://127.0.0.1:51821'])
    expect(managementOriginAllowed(origin)).toBe(true);
  for (const origin of ['http://47.85.109.170:51821','http://node.example.org:51821','http://169.254.169.254','https://user:password@node.example.org','https://node.example.org/path','https://node.example.org?token=abc'])
    expect(managementOriginAllowed(origin)).toBe(false);
});
