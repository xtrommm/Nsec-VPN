import React, { useState } from 'react';
import { 
  Server, Shield, Users, Activity, Settings, Radio, Plus, Trash2, 
  CheckCircle2, AlertTriangle, ExternalLink, Globe, Lock, Bell, RefreshCw
} from 'lucide-react';

interface ServerNode {
  id: string;
  name: string;
  countryCode: string;
  city: string;
  flag: string;
  endpoint: string;
  load: number;
  tier: 'FREE' | 'VIP';
  active: boolean;
}

export default function App() {
  const [currentTab, setCurrentTab] = useState<'servers' | 'users' | 'config' | 'analytics'>('servers');
  const [showAddServerModal, setShowAddServerModal] = useState(false);

  // Initial State for demo & live control
  const [servers, setServers] = useState<ServerNode[]>([
    { id: '1', name: 'US East (New York)', countryCode: 'US', city: 'New York', flag: '🇺🇸', endpoint: 'us.nsecvpn.com:51820', load: 24, tier: 'FREE', active: true },
    { id: '2', name: 'Germany (Frankfurt)', countryCode: 'DE', city: 'Frankfurt', flag: '🇩🇪', endpoint: 'de.nsecvpn.com:51820', load: 38, tier: 'FREE', active: true },
    { id: '3', name: 'Singapore 10G VIP', countryCode: 'SG', city: 'Singapore', flag: '🇸🇬', endpoint: 'sg.nsecvpn.com:51820', load: 14, tier: 'VIP', active: true },
    { id: '4', name: 'Japan (Tokyo VIP)', countryCode: 'JP', city: 'Tokyo', flag: '🇯🇵', endpoint: 'jp.nsecvpn.com:51820', load: 19, tier: 'VIP', active: true },
  ]);

  const [adConfig, setAdConfig] = useState({
    bannerEnabled: true,
    interstitialEnabled: true,
    rewardedEnabled: true,
    interstitialInterval: 120,
    forceUpdate: false,
    minVersionCode: 1,
    announcement: 'WireGuard infrastructure running with zero logs at nsecvpn.com.'
  });

  const [newServer, setNewServer] = useState({
    name: '',
    countryCode: 'US',
    city: '',
    flag: '🇺🇸',
    endpoint: '',
    tier: 'FREE' as 'FREE' | 'VIP'
  });

  const handleAddServer = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newServer.name || !newServer.endpoint) return;
    setServers([...servers, {
      id: String(Date.now()),
      ...newServer,
      load: 5,
      active: true
    }]);
    setShowAddServerModal(false);
    setNewServer({ name: '', countryCode: 'US', city: '', flag: '🇺🇸', endpoint: '', tier: 'FREE' });
  };

  const handleDeleteServer = (id: string) => {
    setServers(servers.filter(s => s.id !== id));
  };

  const handleToggleServer = (id: string) => {
    setServers(servers.map(s => s.id === id ? { ...s, active: !s.active } : s));
  };

  return (
    <div className="min-h-screen bg-[#000B29] text-slate-100 flex flex-col md:flex-row">
      {/* Sidebar Navigation */}
      <aside className="w-full md:w-64 bg-[#0A122E] border-r border-slate-800 p-6 flex flex-col justify-between">
        <div>
          <div className="flex items-center space-x-3 mb-8">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-[#2563EB] to-[#00E5FF] flex items-center justify-center font-black text-xl text-white shadow-lg shadow-cyan-500/20">
              N
            </div>
            <div>
              <h1 className="font-bold text-lg leading-tight text-white">Nsec VPN</h1>
              <p className="text-xs text-[#00E5FF] font-medium tracking-wide">nsecvpn.com Admin</p>
            </div>
          </div>

          <nav className="space-y-1.5">
            <button
              onClick={() => setCurrentTab('servers')}
              className={`w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition ${
                currentTab === 'servers' ? 'bg-[#2563EB] text-white shadow-sm' : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <Server className="w-4 h-4" />
              <span>WireGuard Servers</span>
            </button>

            <button
              onClick={() => setCurrentTab('users')}
              className={`w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition ${
                currentTab === 'users' ? 'bg-[#2563EB] text-white shadow-sm' : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <Users className="w-4 h-4" />
              <span>Devices & VIP Tiers</span>
            </button>

            <button
              onClick={() => setCurrentTab('config')}
              className={`w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition ${
                currentTab === 'config' ? 'bg-[#2563EB] text-white shadow-sm' : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
              }`}
            >
              <Settings className="w-4 h-4" />
              <span>Remote Config & Ads</span>
            </button>
          </nav>
        </div>

        <div className="pt-6 border-t border-slate-800 text-xs text-slate-500 space-y-1">
          <p className="flex items-center space-x-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span>REST API: Online (Port 4000)</span>
          </p>
          <p>Zero-Logs Architecture • v1.0.0</p>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 p-6 md:p-10 overflow-y-auto">
        {/* Top KPI Metrics Bar */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          <div className="bg-[#0A122E] border border-slate-800/80 p-5 rounded-2xl">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
              <span>ACTIVE TUNNELS</span>
              <Activity className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="text-2xl font-bold text-white">418</div>
            <div className="text-xs text-emerald-400 mt-1">Live WireGuard Handshakes</div>
          </div>

          <div className="bg-[#0A122E] border border-slate-800/80 p-5 rounded-2xl">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
              <span>TOTAL SERVERS</span>
              <Server className="w-4 h-4 text-blue-400" />
            </div>
            <div className="text-2xl font-bold text-white">{servers.length}</div>
            <div className="text-xs text-blue-400 mt-1">Global Gateway Nodes</div>
          </div>

          <div className="bg-[#0A122E] border border-slate-800/80 p-5 rounded-2xl">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
              <span>VIP SUBSCRIBERS</span>
              <Shield className="w-4 h-4 text-amber-400" />
            </div>
            <div className="text-2xl font-bold text-white">124</div>
            <div className="text-xs text-amber-400 mt-1">Active Google Play Pass</div>
          </div>

          <div className="bg-[#0A122E] border border-slate-800/80 p-5 rounded-2xl">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
              <span>AVERAGE LOAD</span>
              <Radio className="w-4 h-4 text-[#00E5FF]" />
            </div>
            <div className="text-2xl font-bold text-white">23%</div>
            <div className="text-xs text-[#00E5FF] mt-1">Bandwidth Utilization</div>
          </div>
        </div>

        {/* Tab 1: Server Management */}
        {currentTab === 'servers' && (
          <div>
            <div className="flex items-center justify-between mb-6">
              <div>
                <h2 className="text-xl font-bold text-white">WireGuard Server Nodes</h2>
                <p className="text-sm text-slate-400">Manage endpoints, encryption keys, and tier allocations</p>
              </div>
              <button
                onClick={() => setShowAddServerModal(true)}
                className="flex items-center space-x-2 bg-[#2563EB] hover:bg-blue-600 px-4 py-2 rounded-xl text-sm font-semibold transition shadow-lg shadow-blue-500/20"
              >
                <Plus className="w-4 h-4" />
                <span>Add New Node</span>
              </button>
            </div>

            <div className="bg-[#0A122E] border border-slate-800 rounded-2xl overflow-hidden">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 text-xs text-slate-400 font-semibold bg-slate-900/40">
                    <th className="p-4">LOCATION</th>
                    <th className="p-4">ENDPOINT</th>
                    <th className="p-4">LOAD</th>
                    <th className="p-4">TIER</th>
                    <th className="p-4">STATUS</th>
                    <th className="p-4 text-right">ACTIONS</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-sm">
                  {servers.map(server => (
                    <tr key={server.id} className="hover:bg-slate-800/20 transition">
                      <td className="p-4 font-medium flex items-center space-x-2.5">
                        <span className="text-xl">{server.flag}</span>
                        <div>
                          <div className="text-white font-semibold">{server.name}</div>
                          <div className="text-xs text-slate-400">{server.city}, {server.countryCode}</div>
                        </div>
                      </td>
                      <td className="p-4 font-mono text-xs text-slate-300">{server.endpoint}</td>
                      <td className="p-4">
                        <div className="flex items-center space-x-2">
                          <div className="w-16 bg-slate-800 h-2 rounded-full overflow-hidden">
                            <div className={`h-full ${server.load > 70 ? 'bg-rose-500' : 'bg-emerald-500'}`} style={{ width: `${server.load}%` }} />
                          </div>
                          <span className="text-xs text-slate-400">{server.load}%</span>
                        </div>
                      </td>
                      <td className="p-4">
                        <span className={`px-2.5 py-1 rounded-md text-xs font-bold ${
                          server.tier === 'VIP' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30' : 'bg-slate-800 text-slate-300'
                        }`}>
                          {server.tier}
                        </span>
                      </td>
                      <td className="p-4">
                        <button
                          onClick={() => handleToggleServer(server.id)}
                          className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                            server.active ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
                          }`}
                        >
                          {server.active ? 'Active' : 'Disabled'}
                        </button>
                      </td>
                      <td className="p-4 text-right">
                        <button
                          onClick={() => handleDeleteServer(server.id)}
                          className="p-1.5 text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Tab 2: Devices & Users */}
        {currentTab === 'users' && (
          <div>
            <h2 className="text-xl font-bold text-white mb-2">Registered Devices & Anonymous Sessions</h2>
            <p className="text-sm text-slate-400 mb-6">Device-level subscription allocations via Keystore UUIDs</p>

            <div className="bg-[#0A122E] border border-slate-800 rounded-2xl p-6 space-y-4">
              <div className="flex items-center justify-between pb-4 border-b border-slate-800">
                <div className="flex items-center space-x-3">
                  <div className="w-9 h-9 rounded-xl bg-blue-500/20 text-blue-400 flex items-center justify-center font-bold">#1</div>
                  <div>
                    <div className="font-semibold text-white">Pixel 8 Pro (UUID: 7a8f...b29)</div>
                    <div className="text-xs text-slate-400">Connected to US East • 1.2 GB transferred</div>
                  </div>
                </div>
                <span className="px-3 py-1 bg-amber-500/20 text-amber-400 text-xs font-bold rounded-lg border border-amber-500/30">
                  VIP (Play Store Subscribed)
                </span>
              </div>

              <div className="flex items-center justify-between pb-4 border-b border-slate-800">
                <div className="flex items-center space-x-3">
                  <div className="w-9 h-9 rounded-xl bg-slate-800 text-slate-400 flex items-center justify-center font-bold">#2</div>
                  <div>
                    <div className="font-semibold text-white">Samsung Galaxy S24 (UUID: e91c...84a)</div>
                    <div className="text-xs text-slate-400">Connected to Germany • 420 MB transferred</div>
                  </div>
                </div>
                <span className="px-3 py-1 bg-slate-800 text-slate-300 text-xs font-bold rounded-lg">
                  FREE TIER
                </span>
              </div>
            </div>
          </div>
        )}

        {/* Tab 3: Remote Config & AdMob Controls */}
        {currentTab === 'config' && (
          <div className="max-w-2xl space-y-6">
            <div>
              <h2 className="text-xl font-bold text-white">Remote Config & Monetization</h2>
              <p className="text-sm text-slate-400">Real-time control pushed to Android app clients without re-releasing APK</p>
            </div>

            <div className="bg-[#0A122E] border border-slate-800 rounded-2xl p-6 space-y-6">
              <h3 className="font-semibold text-white text-sm uppercase tracking-wider text-slate-400">Google AdMob Toggles</h3>
              
              <div className="flex items-center justify-between">
                <div>
                  <div className="font-medium text-white text-sm">Banner Ads</div>
                  <div className="text-xs text-slate-400">Display banner at bottom of Server List & Settings for Free users</div>
                </div>
                <input
                  type="checkbox"
                  checked={adConfig.bannerEnabled}
                  onChange={e => setAdConfig({ ...adConfig, bannerEnabled: e.target.checked })}
                  className="w-5 h-5 accent-blue-600 rounded"
                />
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <div className="font-medium text-white text-sm">Interstitial Ads (After Disconnect)</div>
                  <div className="text-xs text-slate-400">Frequency capped at {adConfig.interstitialInterval}s interval</div>
                </div>
                <input
                  type="checkbox"
                  checked={adConfig.interstitialEnabled}
                  onChange={e => setAdConfig({ ...adConfig, interstitialEnabled: e.target.checked })}
                  className="w-5 h-5 accent-blue-600 rounded"
                />
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <div className="font-medium text-white text-sm">Rewarded Ads (30-Minute VIP Pass)</div>
                  <div className="text-xs text-slate-400">Grant temporary access to 10Gbps nodes upon watching video</div>
                </div>
                <input
                  type="checkbox"
                  checked={adConfig.rewardedEnabled}
                  onChange={e => setAdConfig({ ...adConfig, rewardedEnabled: e.target.checked })}
                  className="w-5 h-5 accent-blue-600 rounded"
                />
              </div>

              <div className="pt-4 border-t border-slate-800">
                <button
                  onClick={() => alert('Remote configuration saved and pushed to API!')}
                  className="w-full bg-[#2563EB] hover:bg-blue-600 py-2.5 rounded-xl text-sm font-semibold transition"
                >
                  Save & Apply Settings
                </button>
              </div>
            </div>
          </div>
        )}
      </main>

      {/* Modal: Add New Node */}
      {showAddServerModal && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
          <div className="bg-[#0A122E] border border-slate-800 w-full max-w-md rounded-2xl p-6">
            <h3 className="font-bold text-lg text-white mb-4">Add WireGuard Node</h3>
            <form onSubmit={handleAddServer} className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-400 mb-1 block">NODE NAME</label>
                <input
                  type="text"
                  placeholder="e.g. UK London 10G"
                  value={newServer.name}
                  onChange={e => setNewServer({ ...newServer, name: e.target.value })}
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  required
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-400 mb-1 block">ENDPOINT HOST / IP</label>
                <input
                  type="text"
                  placeholder="uk.nsecvpn.com:51820"
                  value={newServer.endpoint}
                  onChange={e => setNewServer({ ...newServer, endpoint: e.target.value })}
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-400 mb-1 block">COUNTRY CODE</label>
                  <input
                    type="text"
                    placeholder="GB"
                    value={newServer.countryCode}
                    onChange={e => setNewServer({ ...newServer, countryCode: e.target.value.toUpperCase() })}
                    className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-400 mb-1 block">TIER</label>
                  <select
                    value={newServer.tier}
                    onChange={e => setNewServer({ ...newServer, tier: e.target.value as 'FREE' | 'VIP' })}
                    className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-sm text-white focus:outline-none focus:border-blue-500"
                  >
                    <option value="FREE">FREE</option>
                    <option value="VIP">VIP ONLY</option>
                  </select>
                </div>
              </div>

              <div className="flex space-x-3 pt-3">
                <button
                  type="button"
                  onClick={() => setShowAddServerModal(false)}
                  className="flex-1 bg-slate-800 hover:bg-slate-700 py-2.5 rounded-xl text-sm font-semibold transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 bg-[#2563EB] hover:bg-blue-600 py-2.5 rounded-xl text-sm font-semibold transition"
                >
                  Save Node
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
