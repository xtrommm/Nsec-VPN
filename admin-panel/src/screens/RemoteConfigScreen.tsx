import { useState, useEffect, useMemo } from 'react';
import {
  Save,
  RotateCcw,
  AlertTriangle,
  Bell,
  Smartphone,
  Shield,
  Layers,
  Clock,
  Sliders,
  Eye,
  RefreshCw,
  Check,
  FileCode
} from 'lucide-react';
import { AppConfigData } from '../types';
import { ConfirmModal } from '../components/ui/ConfirmModal';

interface RemoteConfigScreenProps {
  config: AppConfigData | null;
  onSaveConfig: (updatedConfig: AppConfigData) => Promise<boolean>;
  onRefreshConfig?: () => Promise<void>;
}

export const RemoteConfigScreen = ({
  config,
  onSaveConfig,
  onRefreshConfig
}: RemoteConfigScreenProps) => {
  // Local working state
  const [formData, setFormData] = useState<AppConfigData>({
    minAppVersionCode: 1,
    latestAppVersionCode: 1,
    forceUpdate: false,
    maintenanceMode: false,
    announcementActive: false,
    announcementTitle: null,
    announcementBody: null,
    bannerAdsEnabled: true,
    interstitialAdsEnabled: true,
    interstitialIntervalSeconds: 120,
    rewardedAdsEnabled: true,
    nativeAdsEnabled: true
  });

  const [isSaving, setIsSaving] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [activeSubTab, setActiveSubTab] = useState<'ads' | 'version' | 'announcement' | 'raw'>('ads');

  // Synchronize when parent config changes
  useEffect(() => {
    if (config) {
      setFormData({
        ...config,
        minAppVersionCode: config.minAppVersionCode ?? 1,
        latestAppVersionCode: config.latestAppVersionCode ?? 1,
        forceUpdate: Boolean(config.forceUpdate),
        maintenanceMode: Boolean(config.maintenanceMode),
        announcementActive: Boolean(config.announcementActive),
        announcementTitle: config.announcementTitle ?? '',
        announcementBody: config.announcementBody ?? '',
        bannerAdsEnabled: config.bannerAdsEnabled ?? true,
        interstitialAdsEnabled: config.interstitialAdsEnabled ?? true,
        interstitialIntervalSeconds: config.interstitialIntervalSeconds ?? 120,
        rewardedAdsEnabled: config.rewardedAdsEnabled ?? true,
        nativeAdsEnabled: config.nativeAdsEnabled ?? true
      });
    }
  }, [config]);

  // Track if changes have been made
  const hasChanges = useMemo(() => {
    if (!config) return false;
    return (
      formData.minAppVersionCode !== (config.minAppVersionCode ?? 1) ||
      formData.latestAppVersionCode !== (config.latestAppVersionCode ?? 1) ||
      formData.forceUpdate !== Boolean(config.forceUpdate) ||
      formData.maintenanceMode !== Boolean(config.maintenanceMode) ||
      formData.announcementActive !== Boolean(config.announcementActive) ||
      (formData.announcementTitle || '') !== (config.announcementTitle || '') ||
      (formData.announcementBody || '') !== (config.announcementBody || '') ||
      formData.bannerAdsEnabled !== (config.bannerAdsEnabled ?? true) ||
      formData.interstitialAdsEnabled !== (config.interstitialAdsEnabled ?? true) ||
      formData.interstitialIntervalSeconds !== (config.interstitialIntervalSeconds ?? 120) ||
      formData.rewardedAdsEnabled !== (config.rewardedAdsEnabled ?? true) ||
      formData.nativeAdsEnabled !== (config.nativeAdsEnabled ?? true)
    );
  }, [formData, config]);

  // Global Ads Kill Switch: true if ANY ad is enabled
  const allAdsDisabled =
    !formData.bannerAdsEnabled &&
    !formData.interstitialAdsEnabled &&
    !formData.rewardedAdsEnabled &&
    !formData.nativeAdsEnabled;

  const handleToggleGlobalAds = (enableAll: boolean) => {
    setFormData((prev) => ({
      ...prev,
      bannerAdsEnabled: enableAll,
      interstitialAdsEnabled: enableAll,
      rewardedAdsEnabled: enableAll,
      nativeAdsEnabled: enableAll
    }));
  };

  const handleReset = () => {
    if (config) {
      setFormData({
        ...config,
        announcementTitle: config.announcementTitle ?? '',
        announcementBody: config.announcementBody ?? ''
      });
      setValidationErrors([]);
    }
  };

  const handleManualRefresh = async () => {
    if (!onRefreshConfig) return;
    setIsRefreshing(true);
    try {
      await onRefreshConfig();
    } finally {
      setIsRefreshing(false);
    }
  };

  const validateForm = (): boolean => {
    const errors: string[] = [];
    if (formData.minAppVersionCode < 1) {
      errors.push('Minimum app version code must be at least 1.');
    }
    if (formData.latestAppVersionCode < formData.minAppVersionCode) {
      errors.push('Latest app version code cannot be lower than minimum version code.');
    }
    if (formData.interstitialIntervalSeconds < 15 || formData.interstitialIntervalSeconds > 1800) {
      errors.push('Interstitial ad interval must be between 15 and 1800 seconds (30 minutes).');
    }
    if (formData.announcementActive) {
      if (!formData.announcementTitle || !formData.announcementTitle.trim()) {
        errors.push('Announcement title is required when announcement is active.');
      }
      if (!formData.announcementBody || !formData.announcementBody.trim()) {
        errors.push('Announcement body is required when announcement is active.');
      }
    }
    setValidationErrors(errors);
    return errors.length === 0;
  };

  const handleInitiatePublish = () => {
    if (validateForm()) {
      setShowConfirmModal(true);
    }
  };

  const handleConfirmPublish = async () => {
    setIsSaving(true);
    setShowConfirmModal(false);
    try {
      const payload: AppConfigData = {
        ...formData,
        minAppVersionCode: Number(formData.minAppVersionCode),
        latestAppVersionCode: Number(formData.latestAppVersionCode),
        interstitialIntervalSeconds: Number(formData.interstitialIntervalSeconds),
        announcementTitle: formData.announcementTitle ? formData.announcementTitle.trim() : null,
        announcementBody: formData.announcementBody ? formData.announcementBody.trim() : null
      };
      await onSaveConfig(payload);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="space-y-6 max-w-6xl mx-auto">
      {/* Top Banner / Actions Header */}
      <div className="bg-[#0B132B] border border-slate-800 rounded-2xl p-6 shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2 text-xs font-semibold text-cyan-400 mb-1">
            <Sliders className="w-4 h-4" />
            <span>GLOBAL CLOUD POLICY & MONETIZATION ENGINE</span>
          </div>
          <h2 className="text-xl font-bold text-white tracking-tight">
            Remote Configuration & Ads Management
          </h2>
          <p className="text-xs text-slate-400 mt-1 flex flex-wrap items-center gap-2">
            <span>Directly synchronized with Android clients via secure cache-invalidated REST API.</span>
            {config?.updatedAt && (
              <>
                <span>•</span>
                <span className="text-slate-300 font-medium">
                  Last Published: {new Date(config.updatedAt).toLocaleString()}
                </span>
              </>
            )}
          </p>
        </div>

        <div className="flex items-center space-x-3 shrink-0">
          {onRefreshConfig && (
            <button
              onClick={handleManualRefresh}
              disabled={isRefreshing || isSaving}
              className="p-2.5 rounded-xl text-xs font-semibold text-slate-300 bg-slate-900 border border-slate-800 hover:bg-slate-800 transition disabled:opacity-50"
              title="Reload from Cloud Database"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin text-cyan-400' : ''}`} />
            </button>
          )}

          {hasChanges && (
            <button
              onClick={handleReset}
              disabled={isSaving}
              className="flex items-center space-x-1.5 px-3.5 py-2.5 rounded-xl text-xs font-semibold text-slate-300 bg-slate-900 border border-slate-800 hover:bg-slate-800 transition disabled:opacity-50"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>Discard Edits</span>
            </button>
          )}

          <button
            onClick={handleInitiatePublish}
            disabled={isSaving || !hasChanges}
            className={`flex items-center space-x-2 px-5 py-2.5 rounded-xl text-xs font-bold transition shadow-lg ${
              hasChanges
                ? 'bg-gradient-to-r from-blue-600 to-cyan-500 hover:from-blue-500 hover:to-cyan-400 text-white shadow-cyan-500/20'
                : 'bg-slate-800 text-slate-500 cursor-not-allowed shadow-none'
            }`}
          >
            {isSaving ? (
              <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : hasChanges ? (
              <Save className="w-4 h-4" />
            ) : (
              <Check className="w-4 h-4 text-emerald-400" />
            )}
            <span>{isSaving ? 'Publishing...' : hasChanges ? 'Publish Changes' : 'All Changes Saved'}</span>
          </button>
        </div>
      </div>

      {/* Validation Alert */}
      {validationErrors.length > 0 && (
        <div className="bg-rose-500/10 border border-rose-500/30 rounded-xl p-4 text-xs text-rose-300 space-y-1">
          <div className="font-bold flex items-center space-x-1.5 text-rose-200">
            <AlertTriangle className="w-4 h-4" />
            <span>Please correct the following configuration errors:</span>
          </div>
          <ul className="list-disc list-inside space-y-0.5 text-rose-300/90 pl-1">
            {validationErrors.map((err, i) => (
              <li key={i}>{err}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Unsaved Changes Warning Badge */}
      {hasChanges && (
        <div className="bg-amber-500/10 border border-amber-500/25 rounded-xl px-4 py-2.5 flex items-center justify-between text-xs text-amber-300">
          <div className="flex items-center space-x-2">
            <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
            <span>You have draft edits in this session. Click <strong>Publish Changes</strong> to commit them to the live cloud API.</span>
          </div>
          <span className="text-[10px] font-bold uppercase px-2 py-0.5 bg-amber-400/20 rounded text-amber-200">
            DRAFT PENDING
          </span>
        </div>
      )}

      {/* Sub-tab navigation */}
      <div className="flex items-center space-x-2 border-b border-slate-800 pb-3 overflow-x-auto">
        <button
          onClick={() => setActiveSubTab('ads')}
          className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-semibold transition shrink-0 ${
            activeSubTab === 'ads'
              ? 'bg-[#2563EB] text-white shadow-md shadow-blue-500/20'
              : 'text-slate-400 hover:text-white hover:bg-slate-900'
          }`}
        >
          <Layers className="w-4 h-4" />
          <span>Google AdMob Monetization</span>
        </button>

        <button
          onClick={() => setActiveSubTab('version')}
          className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-semibold transition shrink-0 ${
            activeSubTab === 'version'
              ? 'bg-[#2563EB] text-white shadow-md shadow-blue-500/20'
              : 'text-slate-400 hover:text-white hover:bg-slate-900'
          }`}
        >
          <Smartphone className="w-4 h-4" />
          <span>Version Policy & Force Update</span>
        </button>

        <button
          onClick={() => setActiveSubTab('announcement')}
          className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-semibold transition shrink-0 ${
            activeSubTab === 'announcement'
              ? 'bg-[#2563EB] text-white shadow-md shadow-blue-500/20'
              : 'text-slate-400 hover:text-white hover:bg-slate-900'
          }`}
        >
          <Bell className="w-4 h-4" />
          <span>Emergency Broadcast Banner</span>
          {formData.announcementActive && (
            <span className="w-2 h-2 rounded-full bg-rose-500 animate-pulse" />
          )}
        </button>

        <button
          onClick={() => setActiveSubTab('raw')}
          className={`flex items-center space-x-2 px-4 py-2 rounded-xl text-xs font-semibold transition shrink-0 ${
            activeSubTab === 'raw'
              ? 'bg-[#2563EB] text-white shadow-md shadow-blue-500/20'
              : 'text-slate-400 hover:text-white hover:bg-slate-900'
          }`}
        >
          <FileCode className="w-4 h-4" />
          <span>Raw JSON Snapshot</span>
        </button>
      </div>

      {/* SUB-TAB 1: ADMOB MONETIZATION */}
      {activeSubTab === 'ads' && (
        <div className="space-y-6">
          {/* Global Kill Switch Card */}
          <div className="bg-[#0B132B] border border-slate-800 rounded-2xl p-6 shadow-xl">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-slate-800/80">
              <div className="space-y-1">
                <div className="flex items-center space-x-2">
                  <Shield className="w-4 h-4 text-cyan-400" />
                  <span className="font-bold text-sm text-white">Global Monetization Kill Switch</span>
                  {allAdsDisabled ? (
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30">
                      ALL ADS DISABLED
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                      ACTIVE MONETIZATION
                    </span>
                  )}
                </div>
                <p className="text-xs text-slate-400">
                  Instantly toggle advertising across the entire Android fleet without rebuilding APKs.
                </p>
              </div>

              <div className="flex items-center space-x-3">
                <button
                  type="button"
                  onClick={() => handleToggleGlobalAds(false)}
                  disabled={allAdsDisabled}
                  className="px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-rose-500/10 border border-rose-500/30 text-rose-300 hover:bg-rose-500/20 transition disabled:opacity-40"
                >
                  Disable All Ads
                </button>
                <button
                  type="button"
                  onClick={() => handleToggleGlobalAds(true)}
                  disabled={!allAdsDisabled}
                  className="px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 hover:bg-emerald-500/20 transition disabled:opacity-40"
                >
                  Enable All Ads
                </button>
              </div>
            </div>

            {/* Placement Toggles Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-6">
              {/* Banner Ads */}
              <div className="p-4 bg-slate-900/60 border border-slate-800/80 rounded-xl flex items-start justify-between">
                <div className="space-y-1 pr-3">
                  <div className="font-bold text-white text-xs flex items-center space-x-2">
                    <span>Adaptive Banner Ads</span>
                    {formData.bannerAdsEnabled ? (
                      <span className="text-[10px] text-emerald-400 font-bold">● ON</span>
                    ) : (
                      <span className="text-[10px] text-slate-500 font-bold">○ OFF</span>
                    )}
                  </div>
                  <p className="text-[11px] text-slate-400 leading-relaxed">
                    Bottom fixed anchor in Server List & Settings for Free tier users.
                  </p>
                  <div className="text-[10px] font-mono text-cyan-400/80 break-all">ca-app-pub-3940256099942544/6300978111</div>
                </div>

                <label className="relative inline-flex items-center cursor-pointer shrink-0 mt-1">
                  <input
                    type="checkbox"
                    checked={formData.bannerAdsEnabled}
                    onChange={(e) =>
                      setFormData((prev) => ({ ...prev, bannerAdsEnabled: e.target.checked }))
                    }
                    className="sr-only peer"
                  />
                  <div className="w-10 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-blue-600" />
                </label>
              </div>

              {/* Interstitial Ads */}
              <div className="p-4 bg-slate-900/60 border border-slate-800/80 rounded-xl flex items-start justify-between">
                <div className="space-y-1 pr-3">
                  <div className="font-bold text-white text-xs flex items-center space-x-2">
                    <span>Interstitial Ads (Post-Disconnect)</span>
                    {formData.interstitialAdsEnabled ? (
                      <span className="text-[10px] text-emerald-400 font-bold">● ON</span>
                    ) : (
                      <span className="text-[10px] text-slate-500 font-bold">○ OFF</span>
                    )}
                  </div>
                  <p className="text-[11px] text-slate-400 leading-relaxed">
                    Triggered when free-tier users terminate a VPN session. Frequency throttled.
                  </p>
                  <div className="text-[10px] font-mono text-cyan-400/80 break-all">ca-app-pub-3940256099942544/1033173712</div>
                </div>

                <label className="relative inline-flex items-center cursor-pointer shrink-0 mt-1">
                  <input
                    type="checkbox"
                    checked={formData.interstitialAdsEnabled}
                    onChange={(e) =>
                      setFormData((prev) => ({ ...prev, interstitialAdsEnabled: e.target.checked }))
                    }
                    className="sr-only peer"
                  />
                  <div className="w-10 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-blue-600" />
                </label>
              </div>

              {/* Rewarded Video Ads */}
              <div className="p-4 bg-slate-900/60 border border-slate-800/80 rounded-xl flex items-start justify-between">
                <div className="space-y-1 pr-3">
                  <div className="font-bold text-white text-xs flex items-center space-x-2">
                    <span>Rewarded Video Ads</span>
                    {formData.rewardedAdsEnabled ? (
                      <span className="text-[10px] text-emerald-400 font-bold">● ON</span>
                    ) : (
                      <span className="text-[10px] text-slate-500 font-bold">○ OFF</span>
                    )}
                  </div>
                  <p className="text-[11px] text-slate-400 leading-relaxed">
                    Grants 30-minute high-speed VIP pass upon video completion.
                  </p>
                  <div className="text-[10px] font-mono text-cyan-400/80 break-all">ca-app-pub-3940256099942544/5224354917</div>
                </div>

                <label className="relative inline-flex items-center cursor-pointer shrink-0 mt-1">
                  <input
                    type="checkbox"
                    checked={formData.rewardedAdsEnabled}
                    onChange={(e) =>
                      setFormData((prev) => ({ ...prev, rewardedAdsEnabled: e.target.checked }))
                    }
                    className="sr-only peer"
                  />
                  <div className="w-10 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-blue-600" />
                </label>
              </div>

              {/* Native Ads */}
              <div className="p-4 bg-slate-900/60 border border-slate-800/80 rounded-xl flex items-start justify-between">
                <div className="space-y-1 pr-3">
                  <div className="font-bold text-white text-xs flex items-center space-x-2">
                    <span>Smart Native Ads</span>
                    {formData.nativeAdsEnabled ? (
                      <span className="text-[10px] text-emerald-400 font-bold">● ON</span>
                    ) : (
                      <span className="text-[10px] text-slate-500 font-bold">○ OFF</span>
                    )}
                  </div>
                  <p className="text-[11px] text-slate-400 leading-relaxed">
                    Seamless card units in Home, Server List cluster rows, & Speed Test.
                  </p>
                  <div className="text-[10px] font-mono text-cyan-400/80 break-all">ca-app-pub-3940256099942544/2247696110</div>
                </div>

                <label className="relative inline-flex items-center cursor-pointer shrink-0 mt-1">
                  <input
                    type="checkbox"
                    checked={formData.nativeAdsEnabled ?? true}
                    onChange={(e) =>
                      setFormData((prev) => ({ ...prev, nativeAdsEnabled: e.target.checked }))
                    }
                    className="sr-only peer"
                  />
                  <div className="w-10 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-blue-600" />
                </label>
              </div>
            </div>

            {/* Frequency Interval Capping */}
            <div className="mt-6 pt-6 border-t border-slate-800/80">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-3">
                <div>
                  <div className="font-bold text-xs text-white flex items-center space-x-2">
                    <Clock className="w-4 h-4 text-cyan-400" />
                    <span>Interstitial Ad Frequency Throttle</span>
                  </div>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Minimum cooldown seconds required between consecutive interstitial ads.
                  </p>
                </div>
                <div className="text-xs font-mono font-bold text-cyan-400 bg-cyan-950/60 border border-cyan-800/50 px-3 py-1 rounded-lg">
                  {formData.interstitialIntervalSeconds}s ({(formData.interstitialIntervalSeconds / 60).toFixed(1)} min)
                </div>
              </div>

              <div className="flex items-center space-x-4">
                <input
                  type="range"
                  min={15}
                  max={600}
                  step={15}
                  value={formData.interstitialIntervalSeconds}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      interstitialIntervalSeconds: parseInt(e.target.value, 10) || 120
                    }))
                  }
                  className="w-full accent-cyan-400 h-2 bg-slate-800 rounded-lg cursor-pointer"
                />
                <input
                  type="number"
                  min={15}
                  max={1800}
                  value={formData.interstitialIntervalSeconds}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      interstitialIntervalSeconds: parseInt(e.target.value, 10) || 15
                    }))
                  }
                  className="w-24 bg-slate-900 border border-slate-800 rounded-xl px-3 py-1.5 text-xs text-white font-mono text-center focus:outline-none focus:border-cyan-500"
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* SUB-TAB 2: VERSION POLICY */}
      {activeSubTab === 'version' && (
        <div className="space-y-6">
          <div className="bg-[#0B132B] border border-slate-800 rounded-2xl p-6 shadow-xl space-y-6">
            <div className="space-y-1">
              <h3 className="font-bold text-sm text-white flex items-center space-x-2">
                <Smartphone className="w-4 h-4 text-blue-400" />
                <span>App Version Enforcement & Update Trigger</span>
              </h3>
              <p className="text-xs text-slate-400">
                Control minimum supported APK build and enforce required security upgrades.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <label className="text-xs font-semibold text-slate-300 block">
                  MINIMUM SUPPORTED VERSION CODE
                </label>
                <input
                  type="number"
                  min={1}
                  value={formData.minAppVersionCode}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      minAppVersionCode: parseInt(e.target.value, 10) || 1
                    }))
                  }
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white font-mono focus:outline-none focus:border-blue-500"
                />
                <p className="text-[11px] text-slate-500">
                  Clients below this version will be blocked or prompted to update.
                </p>
              </div>

              <div className="space-y-2">
                <label className="text-xs font-semibold text-slate-300 block">
                  LATEST RELEASED VERSION CODE
                </label>
                <input
                  type="number"
                  min={1}
                  value={formData.latestAppVersionCode}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      latestAppVersionCode: parseInt(e.target.value, 10) || 1
                    }))
                  }
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white font-mono focus:outline-none focus:border-blue-500"
                />
                <p className="text-[11px] text-slate-500">
                  Current production release version code on Google Play.
                </p>
              </div>
            </div>

            <div className="pt-6 border-t border-slate-800/80 grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Force Update Toggle */}
              <div className="p-4 bg-slate-900/60 border border-slate-800/80 rounded-xl flex items-start justify-between">
                <div className="space-y-1">
                  <div className="font-bold text-xs text-white">Mandatory Force Update</div>
                  <p className="text-[11px] text-slate-400">
                    If active, outdated clients cannot dismiss update modal or connect.
                  </p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer shrink-0 mt-1">
                  <input
                    type="checkbox"
                    checked={formData.forceUpdate}
                    onChange={(e) =>
                      setFormData((prev) => ({ ...prev, forceUpdate: e.target.checked }))
                    }
                    className="sr-only peer"
                  />
                  <div className="w-10 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-rose-600" />
                </label>
              </div>

              {/* Maintenance Mode Toggle */}
              <div className="p-4 bg-slate-900/60 border border-slate-800/80 rounded-xl flex items-start justify-between">
                <div className="space-y-1">
                  <div className="font-bold text-xs text-white">Maintenance Mode</div>
                  <p className="text-[11px] text-slate-400">
                    Displays maintenance banner across Android client app.
                  </p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer shrink-0 mt-1">
                  <input
                    type="checkbox"
                    checked={formData.maintenanceMode}
                    onChange={(e) =>
                      setFormData((prev) => ({ ...prev, maintenanceMode: e.target.checked }))
                    }
                    className="sr-only peer"
                  />
                  <div className="w-10 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-amber-600" />
                </label>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* SUB-TAB 3: EMERGENCY BROADCAST */}
      {activeSubTab === 'announcement' && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Edit Form */}
          <div className="lg:col-span-7 bg-[#0B132B] border border-slate-800 rounded-2xl p-6 shadow-xl space-y-5">
            <div className="flex items-center justify-between pb-4 border-b border-slate-800">
              <div>
                <h3 className="font-bold text-sm text-white flex items-center space-x-2">
                  <Bell className="w-4 h-4 text-amber-400" />
                  <span>Network Broadcast Configuration</span>
                </h3>
                <p className="text-xs text-slate-400">
                  Broadcast maintenance windows or alerts directly to mobile app home screen.
                </p>
              </div>

              <label className="relative inline-flex items-center cursor-pointer shrink-0">
                <input
                  type="checkbox"
                  checked={formData.announcementActive}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, announcementActive: e.target.checked }))
                  }
                  className="sr-only peer"
                />
                <div className="w-10 h-5 bg-slate-800 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-amber-500" />
              </label>
            </div>

            <div className="space-y-4">
              <div>
                <div className="flex items-center justify-between text-xs font-semibold text-slate-300 mb-1.5">
                  <label>ANNOUNCEMENT TITLE</label>
                  <span className="text-[10px] text-slate-500 font-mono">
                    {(formData.announcementTitle || '').length}/128
                  </span>
                </div>
                <input
                  type="text"
                  maxLength={128}
                  placeholder="e.g. Frankfurt Node Cluster Upgraded"
                  value={formData.announcementTitle || ''}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, announcementTitle: e.target.value }))
                  }
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-xs text-white focus:outline-none focus:border-amber-500 font-medium"
                />
              </div>

              <div>
                <div className="flex items-center justify-between text-xs font-semibold text-slate-300 mb-1.5">
                  <label>ANNOUNCEMENT DETAILS / BODY</label>
                  <span className="text-[10px] text-slate-500 font-mono">
                    {(formData.announcementBody || '').length}/512
                  </span>
                </div>
                <textarea
                  rows={4}
                  maxLength={512}
                  placeholder="e.g. Dedicated 10Gbps uplinks are now live across all European gateway nodes."
                  value={formData.announcementBody || ''}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, announcementBody: e.target.value }))
                  }
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl p-4 text-xs text-white focus:outline-none focus:border-amber-500 leading-relaxed"
                />
              </div>
            </div>
          </div>

          {/* Interactive Mobile Client Preview */}
          <div className="lg:col-span-5 bg-[#0B132B] border border-slate-800 rounded-2xl p-6 shadow-xl flex flex-col justify-between">
            <div>
              <div className="flex items-center space-x-2 text-xs font-bold text-slate-400 uppercase tracking-wider mb-4">
                <Eye className="w-4 h-4 text-cyan-400" />
                <span>Live Android Client Preview</span>
              </div>

              {/* Mock Mobile Screen Frame */}
              <div className="bg-[#000B29] border border-slate-800 rounded-3xl p-4 shadow-inner max-w-xs mx-auto">
                <div className="flex items-center justify-between text-[10px] text-slate-500 mb-3 px-1 font-mono">
                  <span>9:41 AM</span>
                  <div className="flex items-center space-x-1">
                    <span>5G</span>
                    <span>100%</span>
                  </div>
                </div>

                <div className="space-y-3">
                  <div className="text-center font-bold text-xs text-white pb-2 border-b border-slate-800/80">
                    Nsec VPN
                  </div>

                  {formData.announcementActive ? (
                    <div className="bg-amber-500/15 border border-amber-500/30 rounded-xl p-3 text-left space-y-1">
                      <div className="flex items-center space-x-1.5 text-amber-300 text-[11px] font-bold">
                        <Bell className="w-3.5 h-3.5 shrink-0" />
                        <span className="truncate">{formData.announcementTitle || 'Announcement Title'}</span>
                      </div>
                      <p className="text-[10px] text-amber-200/80 leading-normal line-clamp-3">
                        {formData.announcementBody || 'Announcement message will appear right here on the client.'}
                      </p>
                    </div>
                  ) : (
                    <div className="p-4 border border-dashed border-slate-800 rounded-xl text-center text-[10px] text-slate-600">
                      Announcement banner is currently disabled.
                    </div>
                  )}

                  {/* Mock Connect Button */}
                  <div className="py-6 flex flex-col items-center justify-center">
                    <div className="w-20 h-20 rounded-full bg-blue-600/20 border-2 border-blue-500 flex items-center justify-center text-blue-400 font-bold text-[10px] shadow-lg shadow-blue-500/20">
                      SECURED
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="text-[11px] text-slate-500 text-center mt-4">
              Real-time representation of <code>HomeScreen.kt</code> alert card.
            </div>
          </div>
        </div>
      )}

      {/* SUB-TAB 4: RAW JSON VIEW */}
      {activeSubTab === 'raw' && (
        <div className="bg-[#0B132B] border border-slate-800 rounded-2xl p-6 shadow-xl space-y-3">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Live Payload Snapshot (Prisma AppConfig model)</span>
            <span className="font-mono text-[10px] text-cyan-400">REST: /api/v1/admin/config</span>
          </div>
          <pre className="p-4 bg-slate-950 rounded-xl text-xs font-mono text-cyan-300 overflow-x-auto border border-slate-800 max-h-96">
            {JSON.stringify(formData, null, 2)}
          </pre>
        </div>
      )}

      {/* Confirmation Modal */}
      <ConfirmModal
        isOpen={showConfirmModal}
        title="Publish Remote Configuration"
        message="Are you sure you want to push these configuration changes live? All active Android clients will fetch and apply these settings on their next configuration synchronization."
        confirmLabel="Publish to Cloud"
        cancelLabel="Keep Reviewing"
        isLoading={isSaving}
        onConfirm={handleConfirmPublish}
        onCancel={() => setShowConfirmModal(false)}
      />
    </div>
  );
};
