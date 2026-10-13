import { clientVersion, enforceClientVersion } from '../services/clientVersion';
it('parses only positive numeric build codes', () => {
  expect(clientVersion('2')).toBe(2);
  for (const value of [undefined, '', '0', '-1', '1.0.1', '2e4', '2147483648']) expect(clientVersion(value)).toBeUndefined();
});
it('blocks old and missing versions only when forced updates are enabled', () => {
  expect(() => enforceClientVersion({forceUpdate:false,minAppVersionCode:99},undefined)).not.toThrow();
  for (const code of [undefined, 1]) expect(() => enforceClientVersion({forceUpdate:true,minAppVersionCode:2},code)).toThrow('APP_UPDATE_REQUIRED');
  expect(() => enforceClientVersion({forceUpdate:true,minAppVersionCode:2},2)).not.toThrow();
});
