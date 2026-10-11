import { useState } from 'react';
import { Save } from 'lucide-react';

export const ConfigScreen = ({ config, onSave }: any) => {
  const [localConfig, setLocalConfig] = useState(config);
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    setSaving(true);
    await onSave(localConfig);
    setSaving(false);
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold text-white">Remote Configuration</h2>
          <p className="text-sm text-slate-400">Manage app behavior, monetization, and alerts in real-time.</p>
        </div>
        <button
          onClick={handleSave}
          disabled={saving}
          className="flex items-center space-x-2 bg-[#2563EB] hover:bg-blue-600 px-6 py-2.5 rounded-xl text-sm font-semibold text-white transition shadow-lg shadow-blue-500/20 disabled:opacity-50"
        >
          <Save className="w-4 h-4" />
          <span>{saving ? 'Saving...' : 'Publish Changes'}</span>
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* AdMob Toggles */}
        <div className="bg-[#0A122E] border border-slate-800 rounded-2xl p-6 space-y-4">
          <h3 className="font-semibold text-white text-sm">Monetization Control</h3>
          {['bannerAdsEnabled', 'interstitialAdsEnabled', 'rewardedAdsEnabled'].map((key) => (
            <div key={key} className="flex items-center justify-between py-2 border-b border-slate-800/50">
              <label className="text-sm text-slate-300 capitalize">{key.replace('Enabled', ' Ads')}</label>
              <input
                type="checkbox"
                checked={localConfig[key]}
                onChange={(e) => setLocalConfig({ ...localConfig, [key]: e.target.checked })}
                className="w-5 h-5 accent-blue-600 rounded"
              />
            </div>
          ))}
        </div>

        {/* Version Policy */}
        <div className="bg-[#0A122E] border border-slate-800 rounded-2xl p-6 space-y-4">
          <h3 className="font-semibold text-white text-sm">Version Policy</h3>
          <div>
            <label className="text-xs text-slate-400 block mb-1">MIN APP VERSION</label>
            <input
              type="number"
              value={localConfig.minAppVersionCode}
              onChange={(e) => setLocalConfig({ ...localConfig, minAppVersionCode: parseInt(e.target.value) })}
              className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white"
            />
          </div>
        </div>
      </div>
    </div>
  );
};
