import { AppConfigData } from '../types';
export function configPayload(value: AppConfigData): Record<string, unknown> {
 const fields: (keyof AppConfigData)[] = ['minAppVersionCode','latestAppVersionCode','forceUpdate','maintenanceMode','announcementActive',
  'announcementTitle','announcementBody','bannerAdsEnabled','nativeAdsEnabled','interstitialAdsEnabled','interstitialIntervalSeconds',
  'rewardedAdsEnabled','bannerAdUnitId','nativeAdUnitId','interstitialAdUnitId','rewardedAdUnitId','rewardedPassMinutes','adsEnabled','supportEmail'];
 const result: Record<string, unknown> = {};
 for(const field of fields){const raw=value[field];if(raw!==undefined)result[field]=typeof raw==='string'?raw.trim()||null:raw;}
 return result;
}
export function validateConfig(value: AppConfigData): string[] {
 const errors:string[]=[];
 if(!Number.isInteger(value.minAppVersionCode)||value.minAppVersionCode<1||!Number.isInteger(value.latestAppVersionCode)||value.latestAppVersionCode<value.minAppVersionCode)errors.push('Latest version must be at least the minimum supported version.');
 if(!Number.isInteger(value.interstitialIntervalSeconds)||value.interstitialIntervalSeconds<15||value.interstitialIntervalSeconds>1800)errors.push('Interstitial interval must be 15–1800 seconds.');
 if(!Number.isInteger(value.rewardedPassMinutes)||value.rewardedPassMinutes!<1||value.rewardedPassMinutes!>1440)errors.push('Reward duration must be 1–1440 minutes.');
 if(value.announcementActive&&(!value.announcementTitle?.trim()||!value.announcementBody?.trim()))errors.push('Active announcements require a title and message.');
 for(const field of ['bannerAdUnitId','nativeAdUnitId','interstitialAdUnitId','rewardedAdUnitId'] as const){const unit=value[field]?.trim();if(unit&&(!/^ca-app-pub-[0-9]{16}\/[0-9]{10}$/.test(unit)||unit.startsWith('ca-app-pub-3940256099942544/')))errors.push(`${field}: enter a production ad unit ID.`);}
 if(value.supportEmail&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.supportEmail.trim()))errors.push('Enter a valid support email.');
 return errors;
}
