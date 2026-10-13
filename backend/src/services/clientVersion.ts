export function clientVersion(header: string | undefined): number | undefined {
  if (!header || !/^[0-9]{1,10}$/.test(header)) return undefined;
  const code = Number(header);
  return Number.isSafeInteger(code) && code > 0 && code <= 2147483647 ? code : undefined;
}
export function enforceClientVersion(config: { forceUpdate?: boolean; minAppVersionCode?: number } | null,
  code: number | undefined) {
  if (config?.forceUpdate && (code === undefined || code < (config.minAppVersionCode ?? 1)))
    throw new Error('APP_UPDATE_REQUIRED');
}
