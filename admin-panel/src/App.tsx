import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Server, Activity, Plus, Trash2, RefreshCw, Download, Ban, Search, ChevronLeft, ChevronRight, CheckCircle, AlertTriangle, Shield, Radio } from 'lucide-react';
import { Sidebar } from './components/Sidebar';
import { ConfigScreen } from './components/ConfigScreen';

const API_BASE = '/api/v1/admin';

interface ServerNode {
  id: string;
  name: string;
  countryCode: string;
  countryName: string;
  city: string;
  flagEmoji: string;
  endpoint: string;
  publicKey: string;
  agentUrl: string;
  currentLoad: number;
  isPremiumOnly: boolean;
  isRecommended: boolean;
  active: boolean;
}

interface DeviceUser {
  id: string;
  deviceId: string;
  deviceModel: string | null;
  osVersion: string | null;
  appVersion: string | null;
  tier: 'FREE' | 'VIP' | 'REWARDED_PASS';
  passExpiresAt: string | null;
  isBlocked: boolean;
  createdAt: string;
}

interface StatsData {
  activeTunnels: number;
  totalDevices: number;
  vipSubscribers: number;
  totalServers: number;
  avgServerLoad: number;
  topCountries?: { countryCode: string; count: number }[];
}

interface AppConfigData {
  minAppVersionCode: number;
  latestAppVersionCode: number;
  forceUpdate: boolean;
  maintenanceMode: boolean;
  announcementActive: boolean;
  announcementTitle: string | null;
  announcementBody: string | null;
  bannerAdsEnabled: boolean;
  interstitialAdsEnabled: boolean;
  interstitialIntervalSeconds: number;
  rewardedAdsEnabled: boolean;
}

interface AuditLogEntry {
  id: string;
  adminId: string;
  action: string;
  targetResource: string;
  targetId: string | null;
  metadata: string | null;
  createdAt: string;
  admin?: { email: string; role: string };
}

export default function App() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('nsec_admin_token'));
  const [adminEmail, setAdminEmail] = useState<string | null>(() => localStorage.getItem('nsec_admin_email'));
  const [adminRole, setAdminRole] = useState<string | null>(() => localStorage.getItem('nsec_admin_role') || 'ADMIN');

  // Auth Form State
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginError, setLoginError] = useState<string | null>(null);
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  // App Tabs
  const [currentTab, setCurrentTab] = useState<'servers' | 'devices' | 'config' | 'audit'>('servers');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Data States
  const [stats, setStats] = useState<StatsData>({
    activeTunnels: 0,
    totalDevices: 0,
    vipSubscribers: 0,
    totalServers: 0,
    avgServerLoad: 0,
    topCountries: []
  });
  const [servers, setServers] = useState<ServerNode[]>([]);
  const [devices, setDevices] = useState<DeviceUser[]>([]);
  const [deviceSearch, setDeviceSearch] = useState('');
  const [devicePage, setDevicePage] = useState(1);
  const [deviceTotalPages, setDeviceTotalPages] = useState(1);
  const [config, setConfig] = useState<AppConfigData | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);

  // Add Server Modal State
  const [showAddServerModal, setShowAddServerModal] = useState(false);
  const [pingingServerId, setPingingServerId] = useState<string | null>(null);
  const [newServer, setNewServer] = useState({
    name: '',
    countryCode: 'US',
    countryName: 'United States',
    city: '',
    flagEmoji: '🇺🇸',
    endpoint: '',
    publicKey: '',
    agentUrl: 'http://',
    agentSecret: '',
    isPremiumOnly: false,
    isRecommended: false
  });

  // API Client with Auth Header
  const apiFetch = useCallback(
    async (endpoint: string, options: RequestInit = {}) => {
      if (!token) throw new Error('Not authenticated');
      const res = await fetch(`${API_BASE}${endpoint}`, {
        ...options,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          ...options.headers
        }
      });

      if (res.status === 401) {
        localStorage.removeItem('nsec_admin_token');
        localStorage.removeItem('nsec_admin_email');
        localStorage.removeItem('nsec_admin_role');
        setToken(null);
        throw new Error('Session expired');
      }

      if (!res.ok) {
        const errorJson = await res.json().catch(() => ({}));
        throw new Error(errorJson.message || `Request failed with HTTP ${res.status}`);
      }

      return res.json();
    },
    [token]
  );

  // Handle Login
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoginError(null);
    setIsLoggingIn(true);

    try {
      const res = await fetch(`${API_BASE}/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: loginEmail, password: loginPassword })
      });

      const data = await res.json();
      if (!res.ok || !data.success) {
        throw new Error(data.message || 'Invalid credentials');
      }

      localStorage.setItem('nsec_admin_token', data.token);
      localStorage.setItem('nsec_admin_email', data.admin.email);
      localStorage.setItem('nsec_admin_role', data.admin.role);
      setToken(data.token);
      setAdminEmail(data.admin.email);
      setAdminRole(data.admin.role);
    } catch (err: any) {
      setLoginError(err.message);
    } finally {
      setIsLoggingIn(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('nsec_admin_token');
    localStorage.removeItem('nsec_admin_email');
    localStorage.removeItem('nsec_admin_role');
    setToken(null);
    setAdminEmail(null);
    setAdminRole(null);
  };

  // Fetch Stats & Primary Data
  const refreshData = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setErrorMsg(null);

    try {
      const [statsRes, serversRes, configRes] = await Promise.all([
        apiFetch('/stats'),
        apiFetch('/servers'),
        apiFetch('/config')
      ]);

      if (statsRes.success) setStats(statsRes.data);
      if (serversRes.success) setServers(serversRes.servers);
      if (configRes.success) setConfig(configRes.data);
    } catch (err: any) {
      setErrorMsg(err.message);
    } finally {
      setLoading(false);
    }
  }, [apiFetch, token]);

  const fetchDevices = useCallback(async () => {
    if (!token) return;
    try {
      const query = new URLSearchParams({
        page: devicePage.toString(),
        limit: '15',
        search: deviceSearch
      });
      const res = await apiFetch(`/devices?${query.toString()}`);
      if (res.success) {
        setDevices(res.devices);
        setDeviceTotalPages(res.pagination.totalPages || 1);
      }
    } catch (err: any) {
      setErrorMsg(err.message);
    }
  }, [apiFetch, devicePage, deviceSearch, token]);

  const fetchAuditLogs = useCallback(async () => {
    if (!token || adminRole !== 'SUPER_ADMIN') return;
    try {
      const res = await apiFetch('/audit-logs');
      if (res.success) {
        setAuditLogs(res.logs);
      }
    } catch (err: any) {
      setErrorMsg(err.message);
    }
  }, [apiFetch, token, adminRole]);

  useEffect(() => {
    if (token) {
      refreshData();
    }
  }, [token, refreshData]);

  useEffect(() => {
    if (token && currentTab === 'devices') {
      fetchDevices();
    }
  }, [token, currentTab, fetchDevices]);

  useEffect(() => {
    if (token && currentTab === 'audit') {
      fetchAuditLogs();
    }
  }, [token, currentTab, fetchAuditLogs]);

  // Server Actions
  const handleCreateServer = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await apiFetch('/servers', {
        method: 'POST',
        body: JSON.stringify(newServer)
      });
      setShowAddServerModal(false);
      setNewServer({
        name: '',
        countryCode: 'US',
        countryName: 'United States',
        city: '',
        flagEmoji: '🇺🇸',
        endpoint: '',
        publicKey: '',
        agentUrl: 'http://',
        agentSecret: '',
        isPremiumOnly: false,
        isRecommended: false
      });
      setSuccessMsg('WireGuard gateway node registered successfully.');
      setTimeout(() => setSuccessMsg(null), 3000);
      refreshData();
    } catch (err: any) {
      alert(`Failed to add server: ${err.message}`);
    }
  };

  const handlePingServer = async (server: ServerNode) => {
    setPingingServerId(server.id);
    try {
      const res = await apiFetch(`/servers/${server.id}/ping`, { method: 'POST' });
      if (res.health?.online) {
        setSuccessMsg(`Node ${server.name} online: ${res.health.peerCount} active peers, ${res.health.loadPercent}% CPU.`);
      } else {
        setErrorMsg(`Node ${server.name} agent unreachable at ${server.agentUrl}`);
      }
      refreshData();
    } catch (err: any) {
      setErrorMsg(`Health ping failed: ${err.message}`);
    } finally {
      setPingingServerId(null);
      setTimeout(() => {
        setSuccessMsg(null);
        setErrorMsg(null);
      }, 4000);
    }
  };

  const handleToggleServer = async (server: ServerNode) => {
    try {
      await apiFetch(`/servers/${server.id}`, {
        method: 'PUT',
        body: JSON.stringify({ active: !server.active })
      });
      refreshData();
    } catch (err: any) {
      alert(`Update failed: ${err.message}`);
    }
  };

  const handleDeleteServer = async (id: string) => {
    if (!confirm('Are you sure you want to permanently delete this VPN node?')) return;
    try {
      await apiFetch(`/servers/${id}`, { method: 'DELETE' });
      refreshData();
    } catch (err: any) {
      alert(`Deletion failed: ${err.message}`);
    }
  };

  // Device Actions
  const handleUpdateDeviceTier = async (deviceId: string, tier: 'FREE' | 'VIP') => {
    try {
      await apiFetch(`/devices/${deviceId}/tier`, {
        method: 'PUT',
        body: JSON.stringify({ tier })
      });
      fetchDevices();
      refreshData();
    } catch (err: any) {
      alert(`Failed to update tier: ${err.message}`);
    }
  };

  const handleToggleDeviceBlock = async (device: DeviceUser) => {
    try {
      await apiFetch(`/devices/${device.id}/block`, {
        method: 'PUT',
        body: JSON.stringify({ isBlocked: !device.isBlocked })
      });
      fetchDevices();
    } catch (err: any) {
      alert(`Failed to toggle block status: ${err.message}`);
    }
  };

  // Save Configuration
  const handleSaveConfig = async () => {
    if (!config) return;
    try {
      await apiFetch('/config', {
        method: 'PUT',
        body: JSON.stringify(config)
      });
      setSuccessMsg('Remote Configuration & AdMob Toggles published successfully!');
      setTimeout(() => setSuccessMsg(null), 3000);
    } catch (err: any) {
      alert(`Save failed: ${err.message}`);
    }
  };

  // Download CSV
  const handleDownloadCsv = () => {
    window.open(`${API_BASE}/export/stats?token=${token}`, '_blank');
  };

  // Debounced search memo
  const debouncedSearchHandler = useMemo(
    () => (val: string) => {
      setDeviceSearch(val);
      setDevicePage(1);
    },
    []
  );

  // If not logged in, render Secure Login Portal
  if (!token) {
    return (
      <div className="min-h-screen bg-[#000B29] flex items-center justify-center p-4">
        <div className="bg-[#0A122E] border border-slate-800 rounded-2xl w-full max-w-md p-8 shadow-2xl">
          <div className="flex items-center space-x-3 mb-6">
            <div className="w-12 h-12 rounded-xl bg-gradient-to-tr from-[#2563EB] to-[#00E5FF] flex items-center justify-center font-black text-2xl text-white shadow-lg shadow-cyan-500/20">
              N
            </div>
            <div>
              <h1 className="font-bold text-xl text-white">Nsec VPN Portal</h1>
              <p className="text-xs text-[#00E5FF] font-medium tracking-wide">SECURE MANAGEMENT ACCESS</p>
            </div>
          </div>

          {loginError && (
            <div className="bg-rose-500/10 border border-rose-500/30 rounded-xl p-3 text-rose-400 text-xs mb-4 flex items-center space-x-2">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              <span>{loginError}</span>
            </div>
          )}

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">ADMIN EMAIL</label>
              <input
                type="email"
                value={loginEmail}
                onChange={(e) => setLoginEmail(e.target.value)}
                placeholder="admin@nsecvpn.com"
                required
                className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">PASSWORD</label>
              <input
                type="password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="••••••••••••"
                required
                className="w-full bg-slate-900 border border-slate-800 rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-blue-500"
              />
            </div>

            <button
              type="submit"
              disabled={isLoggingIn}
              className="w-full bg-[#2563EB] hover:bg-blue-600 disabled:opacity-50 py-3 rounded-xl text-sm font-semibold text-white transition shadow-lg shadow-blue-500/20"
            >
              {isLoggingIn ? 'Verifying...' : 'Sign In to Management Console'}
            </button>
          </form>

          <p className="text-center text-xs text-slate-500 mt-6">
            Authorized administrator access only • nsecvpn.com
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#000B29] text-slate-100 flex flex-col md:flex-row">
      <Sidebar 
        currentTab={currentTab} 
        setCurrentTab={setCurrentTab} 
        adminRole={adminRole} 
        onLogout={handleLogout} 
        email={adminEmail} 
      />

      {/* Main Content */}
      <main className="flex-1 p-6 md:p-10 overflow-y-auto">
        {/* Top KPI Metrics Bar */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          <div className="bg-[#0A122E] border border-slate-800 p-5 rounded-2xl">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
              <span>ACTIVE TUNNELS</span>
              <Activity className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="text-2xl font-bold text-white">{stats.activeTunnels}</div>
            <div className="text-xs text-emerald-400 mt-1">Live Encrypted Sessions</div>
          </div>

          <div className="bg-[#0A122E] border border-slate-800 p-5 rounded-2xl">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
              <span>GLOBAL NODES</span>
              <Server className="w-4 h-4 text-blue-400" />
            </div>
            <div className="text-2xl font-bold text-white">{stats.totalServers}</div>
            <div className="text-xs text-blue-400 mt-1">Active Clusters</div>
          </div>

          <div className="bg-[#0A122E] border border-slate-800 p-5 rounded-2xl">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
              <span>VIP SUBSCRIBERS</span>
              <Shield className="w-4 h-4 text-amber-400" />
            </div>
            <div className="text-2xl font-bold text-white">{stats.vipSubscribers}</div>
            <div className="text-xs text-amber-400 mt-1">Active Play Store Licenses</div>
          </div>

          <div className="bg-[#0A122E] border border-slate-800 p-5 rounded-2xl">
            <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
              <span>AVERAGE LOAD</span>
              <Radio className="w-4 h-4 text-[#00E5FF]" />
            </div>
            <div className="text-2xl font-bold text-white">{stats.avgServerLoad}%</div>
            <div className="text-xs text-[#00E5FF] mt-1">Network Capacity</div>
          </div>
        </div>

        {errorMsg && (
          <div className="bg-rose-500/10 border border-rose-500/30 rounded-xl p-4 text-rose-400 text-sm mb-6 flex items-center justify-between">
            <span>{errorMsg}</span>
            <button onClick={refreshData} className="underline text-xs">
              Retry
            </button>
          </div>
        )}

        {successMsg && (
          <div className="bg-emerald-500/10 border border-emerald-500/30 rounded-xl p-4 text-emerald-400 text-sm mb-6 flex items-center space-x-2">
            <CheckCircle className="w-4 h-4 shrink-0" />
            <span>{successMsg}</span>
          </div>
        )}

        {/* Tab 1: Server Management */}
        {currentTab === 'servers' && (
          <div>
            <div className="flex items-center justify-between mb-6">
              <div>
                <h2 className="text-xl font-bold text-white">WireGuard Server Nodes</h2>
                <p className="text-sm text-slate-400">
                  Manage gateway endpoints, public keys, and daemon hooks
                </p>
              </div>
              <div className="flex items-center space-x-3">
                <button
                  onClick={refreshData}
                  className="p-2 bg-slate-800 hover:bg-slate-700 rounded-xl text-slate-300 transition"
                  title="Refresh"
                >
                  <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
                </button>
                <button
                  onClick={() => setShowAddServerModal(true)}
                  className="flex items-center space-x-2 bg-[#2563EB] hover:bg-blue-600 px-4 py-2 rounded-xl text-sm font-semibold transition shadow-lg shadow-blue-500/20"
                >
                  <Plus className="w-4 h-4" />
                  <span>Add New Node</span>
                </button>
              </div>
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
                  {servers.map((server) => (
                    <tr key={server.id} className="hover:bg-slate-800/20 transition">
                      <td className="p-4 font-medium flex items-center space-x-3">
                        <span className="text-xl">{server.flagEmoji}</span>
                        <div>
                          <div className="text-white font-semibold flex items-center space-x-1.5">
                            <span>{server.name}</span>
                            {server.isRecommended && (
                              <span className="px-1.5 py-0.2 bg-emerald-500/20 text-emerald-400 text-[10px] rounded font-bold">
                                FAST
                              </span>
                            )}
                          </div>
                          <div className="text-xs text-slate-400">
                            {server.city}, {server.countryName}
                          </div>
                        </div>
                      </td>
                      <td className="p-4 font-mono text-xs text-slate-300">{server.endpoint}</td>
                      <td className="p-4">
                        <div className="flex items-center space-x-2">
                          <div className="w-16 bg-slate-800 h-2 rounded-full overflow-hidden">
                            <div
                              className={`h-full ${
                                server.currentLoad > 70 ? 'bg-rose-500' : 'bg-emerald-500'
                              }`}
                              style={{ width: `${server.currentLoad}%` }}
                            />
                          </div>
                          <span className="text-xs text-slate-400">{server.currentLoad}%</span>
                        </div>
                      </td>
                      <td className="p-4">
                        <span
                          className={`px-2.5 py-1 rounded-md text-xs font-bold ${
                            server.isPremiumOnly
                              ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                              : 'bg-slate-800 text-slate-300'
                          }`}
                        >
                          {server.isPremiumOnly ? 'VIP' : 'FREE'}
                        </span>
                      </td>
                      <td className="p-4">
                        <button
                          onClick={() => handleToggleServer(server)}
                          className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
                            server.active
                              ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                              : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                          }`}
                        >
                          {server.active ? 'Active' : 'Disabled'}
                        </button>
                      </td>
                      <td className="p-4 text-right space-x-2">
                        <button
                          onClick={() => handlePingServer(server)}
                          disabled={pingingServerId === server.id}
                          className="p-1.5 text-slate-400 hover:text-cyan-400 hover:bg-cyan-500/10 rounded-lg transition"
                          title="Check Agent Status"
                        >
                          <Activity className={`w-4 h-4 ${pingingServerId === server.id ? 'animate-spin' : ''}`} />
                        </button>
                        {adminRole === 'SUPER_ADMIN' && (
                          <button
                            onClick={() => handleDeleteServer(server.id)}
                            className="p-1.5 text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition"
                            title="Delete Node"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  {servers.length === 0 && (
                    <tr>
                      <td colSpan={6} className="p-8 text-center text-slate-500 text-sm">
                        No VPN nodes configured. Click "Add New Node" to deploy your first WireGuard
                        cluster.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Tab 2: Devices & Users */}
        {currentTab === 'devices' && (
          <div>
            <div className="flex items-center justify-between mb-6">
              <div>
                <h2 className="text-xl font-bold text-white">Registered Client Devices</h2>
                <p className="text-sm text-slate-400">
                  Anonymous Keystore UUID allocations & VIP subscription controls
                </p>
              </div>
              <div className="flex items-center space-x-3">
                <div className="relative">
                  <Search className="w-4 h-4 text-slate-500 absolute left-3 top-2.5" />
                  <input
                    type="text"
                    value={deviceSearch}
                    onChange={(e) => debouncedSearchHandler(e.target.value)}
                    placeholder="Search Device UUID..."
                    className="bg-slate-900 border border-slate-800 rounded-xl pl-9 pr-4 py-1.5 text-xs text-white focus:outline-none focus:border-blue-500"
                  />
                </div>
                <button
                  onClick={handleDownloadCsv}
                  className="flex items-center space-x-1.5 bg-slate-800 hover:bg-slate-700 px-3 py-1.5 rounded-xl text-xs font-semibold text-slate-300 transition"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Export CSV</span>
                </button>
              </div>
            </div>

            <div className="bg-[#0A122E] border border-slate-800 rounded-2xl overflow-hidden">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 text-xs text-slate-400 font-semibold bg-slate-900/40">
                    <th className="p-4">DEVICE ID</th>
                    <th className="p-4">MODEL / OS</th>
                    <th className="p-4">TIER</th>
                    <th className="p-4">REGISTERED</th>
                    <th className="p-4 text-right">ACTIONS</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-sm">
                  {devices.map((device) => (
                    <tr key={device.id} className="hover:bg-slate-800/20 transition">
                      <td className="p-4 font-mono text-xs text-white">
                        <div className="flex items-center space-x-2">
                          <span
                            className={`w-2 h-2 rounded-full ${
                              device.isBlocked ? 'bg-rose-500' : 'bg-emerald-500'
                            }`}
                          />
                          <span title={device.deviceId}>{device.deviceId.slice(0, 16)}...</span>
                        </div>
                      </td>
                      <td className="p-4 text-xs text-slate-300">
                        {device.deviceModel || 'Android'} • {device.osVersion || 'Android OS'}
                      </td>
                      <td className="p-4">
                        <span
                          className={`px-2 py-0.5 rounded text-xs font-bold ${
                            device.tier === 'VIP'
                              ? 'bg-amber-500/20 text-amber-400'
                              : 'bg-slate-800 text-slate-400'
                          }`}
                        >
                          {device.tier}
                        </span>
                      </td>
                      <td className="p-4 text-xs text-slate-400">
                        {new Date(device.createdAt).toLocaleDateString()}
                      </td>
                      <td className="p-4 text-right space-x-2">
                        <button
                          onClick={() =>
                            handleUpdateDeviceTier(
                              device.id,
                              device.tier === 'VIP' ? 'FREE' : 'VIP'
                            )
                          }
                          className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-xs font-semibold rounded-lg text-slate-300 transition"
                        >
                          {device.tier === 'VIP' ? 'Set Free' : 'Grant VIP'}
                        </button>
                        <button
                          onClick={() => handleToggleDeviceBlock(device)}
                          className={`p-1.5 rounded-lg transition ${
                            device.isBlocked
                              ? 'text-emerald-400 hover:bg-emerald-500/10'
                              : 'text-rose-400 hover:bg-rose-500/10'
                          }`}
                          title={device.isBlocked ? 'Unblock Device' : 'Block Device'}
                        >
                          <Ban className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                  {devices.length === 0 && (
                    <tr>
                      <td colSpan={5} className="p-8 text-center text-slate-500 text-sm">
                        No registered devices matching search criteria.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>

              {/* Pagination */}
              <div className="p-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
                <span>
                  Page {devicePage} of {deviceTotalPages}
                </span>
                <div className="flex space-x-2">
                  <button
                    disabled={devicePage <= 1}
                    onClick={() => setDevicePage((p) => p - 1)}
                    className="p-1 bg-slate-800 disabled:opacity-40 rounded"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <button
                    disabled={devicePage >= deviceTotalPages}
                    onClick={() => setDevicePage((p) => p + 1)}
                    className="p-1 bg-slate-800 disabled:opacity-40 rounded"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 3: Remote Config & AdMob */}
        {currentTab === 'config' && config && (
          <ConfigScreen config={config} onSave={handleSaveConfig} />
        )}

        {/* Tab 4: Audit Logs (Super Admin only) */}
        {currentTab === 'audit' && adminRole === 'SUPER_ADMIN' && (
          <div>
            <div className="flex items-center justify-between mb-6">
              <div>
                <h2 className="text-xl font-bold text-white">System Audit Trail</h2>
                <p className="text-sm text-slate-400">
                  Immutable administrative action log for security compliance
                </p>
              </div>
              <button
                onClick={fetchAuditLogs}
                className="p-2 bg-slate-800 hover:bg-slate-700 rounded-xl text-slate-300 transition"
              >
                <RefreshCw className="w-4 h-4" />
              </button>
            </div>

            <div className="bg-[#0A122E] border border-slate-800 rounded-2xl overflow-hidden">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-slate-800 text-xs text-slate-400 font-semibold bg-slate-900/40">
                    <th className="p-4">TIMESTAMP</th>
                    <th className="p-4">ADMINISTRATOR</th>
                    <th className="p-4">ACTION</th>
                    <th className="p-4">RESOURCE</th>
                    <th className="p-4">METADATA</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-xs font-mono">
                  {auditLogs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-800/20 transition">
                      <td className="p-4 text-slate-400">
                        {new Date(log.createdAt).toLocaleString()}
                      </td>
                      <td className="p-4 text-white font-sans">
                        {log.admin?.email || log.adminId}
                      </td>
                      <td className="p-4">
                        <span className="px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20">
                          {log.action}
                        </span>
                      </td>
                      <td className="p-4 text-slate-300">{log.targetResource}</td>
                      <td className="p-4 text-slate-400 max-w-xs truncate" title={log.metadata || ''}>
                        {log.metadata || '-'}
                      </td>
                    </tr>
                  ))}
                  {auditLogs.length === 0 && (
                    <tr>
                      <td colSpan={5} className="p-8 text-center text-slate-500 font-sans text-sm">
                        No audit log entries recorded yet.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </main>

      {/* Add Server Modal */}
      {showAddServerModal && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
          <div className="bg-[#0A122E] border border-slate-800 w-full max-w-lg rounded-2xl p-6 max-h-[90vh] overflow-y-auto">
            <h3 className="font-bold text-lg text-white mb-4">Add WireGuard Gateway Node</h3>
            <form onSubmit={handleCreateServer} className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-1">NODE NAME</label>
                <input
                  type="text"
                  placeholder="e.g. US East (New York)"
                  value={newServer.name}
                  onChange={(e) => setNewServer({ ...newServer, name: e.target.value })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white"
                />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-400 block mb-1">
                    COUNTRY CODE
                  </label>
                  <input
                    type="text"
                    placeholder="US"
                    maxLength={2}
                    value={newServer.countryCode}
                    onChange={(e) =>
                      setNewServer({ ...newServer, countryCode: e.target.value.toUpperCase() })
                    }
                    required
                    className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-400 block mb-1">CITY</label>
                  <input
                    type="text"
                    placeholder="New York"
                    value={newServer.city}
                    onChange={(e) => setNewServer({ ...newServer, city: e.target.value })}
                    required
                    className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-400 block mb-1">
                    FLAG EMOJI
                  </label>
                  <input
                    type="text"
                    placeholder="🇺🇸"
                    value={newServer.flagEmoji}
                    onChange={(e) => setNewServer({ ...newServer, flagEmoji: e.target.value })}
                    required
                    className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-1">
                  WIREGUARD ENDPOINT (HOST:PORT)
                </label>
                <input
                  type="text"
                  placeholder="us.nsecvpn.com:51820"
                  value={newServer.endpoint}
                  onChange={(e) => setNewServer({ ...newServer, endpoint: e.target.value })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white font-mono"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-1">
                  SERVER PUBLIC KEY (CURVE25519)
                </label>
                <input
                  type="text"
                  placeholder="8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M="
                  value={newServer.publicKey}
                  onChange={(e) => setNewServer({ ...newServer, publicKey: e.target.value })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white font-mono"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-1">
                  GATEWAY AGENT URL
                </label>
                <input
                  type="text"
                  placeholder="http://198.51.100.24:51821"
                  value={newServer.agentUrl}
                  onChange={(e) => setNewServer({ ...newServer, agentUrl: e.target.value })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white font-mono"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-1">
                  AGENT SHARED HMAC SECRET
                </label>
                <input
                  type="password"
                  placeholder="Secret generated by installer"
                  value={newServer.agentSecret}
                  onChange={(e) => setNewServer({ ...newServer, agentSecret: e.target.value })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3 py-2 text-sm text-white font-mono"
                />
              </div>

              <div className="grid grid-cols-2 gap-3 pt-2">
                <label className="flex items-center space-x-2 text-xs text-slate-300">
                  <input
                    type="checkbox"
                    checked={newServer.isPremiumOnly}
                    onChange={(e) =>
                      setNewServer({ ...newServer, isPremiumOnly: e.target.checked })
                    }
                    className="w-4 h-4 accent-amber-500"
                  />
                  <span>VIP Subscribers Only</span>
                </label>
                <label className="flex items-center space-x-2 text-xs text-slate-300">
                  <input
                    type="checkbox"
                    checked={newServer.isRecommended}
                    onChange={(e) =>
                      setNewServer({ ...newServer, isRecommended: e.target.checked })
                    }
                    className="w-4 h-4 accent-emerald-500"
                  />
                  <span>Mark as Recommended</span>
                </label>
              </div>

              <div className="flex space-x-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowAddServerModal(false)}
                  className="flex-1 bg-slate-800 hover:bg-slate-700 py-2.5 rounded-xl text-sm font-semibold text-slate-300 transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 bg-[#2563EB] hover:bg-blue-600 py-2.5 rounded-xl text-sm font-semibold text-white transition"
                >
                  Save & Register Node
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
