import React, { useState, useEffect, useCallback } from 'react';
import { AlertTriangle, Lock, Mail, ArrowRight } from 'lucide-react';
import { Sidebar } from './components/Sidebar';
import { TopBar } from './components/ui/TopBar';
import { ToastContainer } from './components/ui/Toast';
import { NodesScreen } from './screens/NodesScreen';
import { DevicesScreen } from './screens/DevicesScreen';
import { RemoteConfigScreen } from './screens/RemoteConfigScreen';
import { AuditScreen } from './screens/AuditScreen';
import {
  ServerNode,
  DeviceUser,
  StatsData,
  AppConfigData,
  AuditLogEntry,
  ToastMessage
} from './types';

const API_BASE = '/api/v1/admin';

export default function App() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('nsec_admin_token'));
  const [adminEmail, setAdminEmail] = useState<string | null>(() => localStorage.getItem('nsec_admin_email'));
  const [adminRole, setAdminRole] = useState<string | null>(() => localStorage.getItem('nsec_admin_role') || 'ADMIN');

  // Auth Form State
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginError, setLoginError] = useState<string | null>(null);
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  // App Navigation & Mobile Drawer
  const [currentTab, setCurrentTab] = useState<'servers' | 'devices' | 'config' | 'audit'>('servers');
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [loading, setLoading] = useState(false);

  // Toast System
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const showToast = useCallback((type: 'success' | 'error' | 'info' | 'warning', message: string, title?: string) => {
    const id = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
    const newToast: ToastMessage = { id, type, title, message };
    setToasts((prev) => [...prev, newToast]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

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

  // Authenticated API Fetcher
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
        setAdminEmail(null);
        setAdminRole(null);
        showToast('error', 'Your administrative session has expired. Please sign in again.', 'Session Expired');
        throw new Error('Session expired');
      }

      if (!res.ok) {
        const errorJson = await res.json().catch(() => ({}));
        throw new Error(errorJson.message || `Request failed with status ${res.status}`);
      }

      return res.json();
    },
    [token, showToast]
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
        throw new Error(data.message || 'Invalid administrator credentials');
      }

      localStorage.setItem('nsec_admin_token', data.token);
      localStorage.setItem('nsec_admin_email', data.admin.email);
      localStorage.setItem('nsec_admin_role', data.admin.role);
      setToken(data.token);
      setAdminEmail(data.admin.email);
      setAdminRole(data.admin.role);
      showToast('success', `Signed in as ${data.admin.email}`, 'Welcome');
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
    showToast('info', 'You have been signed out safely.', 'Signed Out');
  };

  // Fetch KPI Stats, Servers, & Remote Config
  const refreshData = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    try {
      const [statsRes, serversRes, configRes] = await Promise.all([
        apiFetch('/stats'),
        apiFetch('/servers'),
        apiFetch('/config')
      ]);

      if (statsRes.success) setStats(statsRes.data);
      if (serversRes.success) setServers(serversRes.servers);
      if (configRes.success) setConfig(configRes.data || configRes.config);
    } catch (err: any) {
      showToast('error', err.message, 'Data Fetch Error');
    } finally {
      setLoading(false);
    }
  }, [apiFetch, token, showToast]);

  // Fetch Devices Page
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
        setDeviceTotalPages(res.pagination?.totalPages || 1);
      }
    } catch (err: any) {
      showToast('error', err.message, 'Device Fetch Error');
    }
  }, [apiFetch, devicePage, deviceSearch, token, showToast]);

  // Fetch Audit Trail
  const fetchAuditLogs = useCallback(async () => {
    if (!token || adminRole !== 'SUPER_ADMIN') return;
    try {
      const res = await apiFetch('/audit-logs');
      if (res.success) {
        setAuditLogs(res.logs);
      }
    } catch (err: any) {
      showToast('error', err.message, 'Audit Trail Error');
    }
  }, [apiFetch, token, adminRole, showToast]);

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

  // 1. Server Actions
  const handleCreateServer = async (serverData: any): Promise<boolean> => {
    try {
      await apiFetch('/servers', {
        method: 'POST',
        body: JSON.stringify(serverData)
      });
      showToast('success', `WireGuard node "${serverData.name}" registered successfully.`, 'Node Created');
      refreshData();
      return true;
    } catch (err: any) {
      showToast('error', err.message, 'Creation Failed');
      return false;
    }
  };

  const handleToggleServer = async (server: ServerNode): Promise<boolean> => {
    try {
      await apiFetch(`/servers/${server.id}`, {
        method: 'PUT',
        body: JSON.stringify({ active: !server.active })
      });
      showToast(
        'success',
        `Node "${server.name}" marked as ${!server.active ? 'Active' : 'Disabled'}.`,
        'Status Updated'
      );
      refreshData();
      return true;
    } catch (err: any) {
      showToast('error', err.message, 'Status Update Failed');
      return false;
    }
  };

  const handleDeleteServer = async (id: string): Promise<boolean> => {
    try {
      await apiFetch(`/servers/${id}`, { method: 'DELETE' });
      showToast('success', 'WireGuard gateway cluster removed.', 'Node Deleted');
      refreshData();
      return true;
    } catch (err: any) {
      showToast('error', err.message, 'Deletion Failed');
      return false;
    }
  };

  const handlePingServer = async (server: ServerNode) => {
    try {
      const res = await apiFetch(`/servers/${server.id}/ping`, { method: 'POST' });
      if (res.health?.online) {
        showToast(
          'success',
          `Daemon online: ${res.health.peerCount} peers active, ${res.health.loadPercent}% CPU.`,
          server.name
        );
      } else {
        showToast(
          'warning',
          `Agent unreachable at ${server.agentUrl}: ${res.health?.error || 'Timed out'}`,
          server.name
        );
      }
      refreshData();
      return res.health || { online: false };
    } catch (err: any) {
      showToast('error', err.message, 'Daemon Check Failed');
      return { online: false, error: err.message };
    }
  };

  // 2. Device Actions
  const handleUpdateDeviceTier = async (deviceId: string, tier: 'FREE' | 'VIP'): Promise<boolean> => {
    try {
      await apiFetch(`/devices/${deviceId}/tier`, {
        method: 'PUT',
        body: JSON.stringify({ tier })
      });
      showToast('success', `Device tier updated to ${tier}.`, 'Tier Changed');
      fetchDevices();
      refreshData();
      return true;
    } catch (err: any) {
      showToast('error', err.message, 'Tier Update Failed');
      return false;
    }
  };

  const handleToggleDeviceBlock = async (device: DeviceUser): Promise<boolean> => {
    try {
      await apiFetch(`/devices/${device.id}/block`, {
        method: 'PUT',
        body: JSON.stringify({ isBlocked: !device.isBlocked })
      });
      showToast(
        device.isBlocked ? 'success' : 'warning',
        `Device ${device.deviceId.slice(0, 12)}... is now ${!device.isBlocked ? 'Blocked' : 'Unblocked'}.`,
        'Access Control'
      );
      fetchDevices();
      return true;
    } catch (err: any) {
      showToast('error', err.message, 'Block Action Failed');
      return false;
    }
  };

  const handleExportCsv = async () => {
    try {
      const res = await fetch(`${API_BASE}/export/stats`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (!res.ok) throw new Error('Failed to export CSV');
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `nsecvpn_stats_${new Date().toISOString().split('T')[0]}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      showToast('success', 'Aggregated anonymous statistics downloaded.', 'CSV Exported');
    } catch (err: any) {
      showToast('error', err.message, 'Export Error');
    }
  };

  // 3. Remote Config & Ads Action
  const handleSaveConfig = async (updatedConfig: AppConfigData): Promise<boolean> => {
    try {
      const res = await apiFetch('/config', {
        method: 'PUT',
        body: JSON.stringify(updatedConfig)
      });
      setConfig(res.config || res.data || updatedConfig);
      showToast(
        'success',
        'Remote Configuration & AdMob placements published to live cloud API!',
        'Published Live'
      );
      return true;
    } catch (err: any) {
      showToast('error', err.message, 'Publish Failed');
      return false;
    }
  };

  // If unauthenticated, render Modern Secure Login Screen
  if (!token) {
    return (
      <div className="min-h-screen bg-[#00081D] flex items-center justify-center p-4 selection:bg-cyan-500 selection:text-white">
        <div className="w-full max-w-md">
          {/* Brand header */}
          <div className="flex flex-col items-center text-center mb-8">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-blue-600 via-indigo-500 to-cyan-400 flex items-center justify-center font-black text-2xl text-white shadow-xl shadow-cyan-500/25 mb-4">
              N
            </div>
            <h1 className="text-2xl font-black text-white tracking-tight">Nsec VPN Console</h1>
            <p className="text-xs text-cyan-400 font-semibold tracking-wider uppercase mt-1">
              Zero-Logs Core Administration
            </p>
          </div>

          <div className="bg-[#070E24] border border-slate-800/90 rounded-3xl p-8 shadow-2xl relative overflow-hidden backdrop-blur-xl">
            <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-blue-600 via-cyan-400 to-blue-500" />

            <div className="mb-6">
              <h2 className="text-base font-bold text-white">Administrator Access</h2>
              <p className="text-xs text-slate-400 mt-0.5">Authenticate with your private backend credentials.</p>
            </div>

            {loginError && (
              <div className="bg-rose-500/10 border border-rose-500/30 rounded-xl p-3.5 text-rose-300 text-xs mb-5 flex items-start space-x-2.5 animate-in fade-in">
                <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0 mt-0.5" />
                <span>{loginError}</span>
              </div>
            )}

            <form onSubmit={handleLogin} className="space-y-4">
              <div>
                <label className="text-xs font-bold text-slate-400 block mb-1.5 uppercase tracking-wider">
                  Admin Email
                </label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
                  <input
                    type="email"
                    value={loginEmail}
                    onChange={(e) => setLoginEmail(e.target.value)}
                    placeholder="admin@nsecvpn.com"
                    required
                    className="w-full bg-slate-900/90 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 transition font-medium"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-bold text-slate-400 block mb-1.5 uppercase tracking-wider">
                  Password
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-slate-500 absolute left-3.5 top-3.5" />
                  <input
                    type="password"
                    value={loginPassword}
                    onChange={(e) => setLoginPassword(e.target.value)}
                    placeholder="••••••••••••"
                    required
                    className="w-full bg-slate-900/90 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 transition font-mono"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={isLoggingIn}
                className="w-full mt-2 bg-gradient-to-r from-blue-600 to-cyan-500 hover:from-blue-500 hover:to-cyan-400 disabled:opacity-50 py-3 rounded-xl text-xs font-bold text-white transition shadow-lg shadow-cyan-500/20 flex items-center justify-center space-x-2"
              >
                {isLoggingIn ? (
                  <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                ) : (
                  <>
                    <span>Sign In to Console</span>
                    <ArrowRight className="w-4 h-4" />
                  </>
                )}
              </button>
            </form>

            <div className="mt-6 pt-6 border-t border-slate-800/80 text-center">
              <span className="text-[11px] text-slate-500">
                Encrypted tunnel sessions protected by Curve25519 & ChaCha20-Poly1305.
              </span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#00081D] text-slate-100 flex flex-col md:flex-row selection:bg-cyan-500 selection:text-white">
      {/* Toast Notification Container */}
      <ToastContainer toasts={toasts} onDismiss={dismissToast} />

      {/* Responsive Navigation Sidebar */}
      <Sidebar
        currentTab={currentTab}
        setCurrentTab={setCurrentTab}
        adminRole={adminRole}
        serverCount={servers.length}
        deviceCount={stats.totalDevices}
        adminEmail={adminEmail}
        onLogout={handleLogout}
        mobileOpen={mobileMenuOpen}
        onCloseMobile={() => setMobileMenuOpen(false)}
      />

      {/* Main Administrative Viewport */}
      <div className="flex-1 flex flex-col min-w-0">
        <TopBar
          currentTab={currentTab}
          adminEmail={adminEmail}
          adminRole={adminRole}
          loading={loading}
          activeTunnels={stats.activeTunnels}
          totalServers={servers.length}
          onRefresh={refreshData}
          onLogout={handleLogout}
          onToggleMobileMenu={() => setMobileMenuOpen(true)}
        />

        <main className="flex-1 p-4 md:p-8 overflow-y-auto">
          {/* TAB 1: WIREGUARD NODES */}
          {currentTab === 'servers' && (
            <NodesScreen
              servers={servers}
              adminRole={adminRole}
              loading={loading}
              onRefresh={refreshData}
              onCreateServer={handleCreateServer}
              onToggleServer={handleToggleServer}
              onDeleteServer={handleDeleteServer}
              onPingServer={handlePingServer}
            />
          )}

          {/* TAB 2: CLIENT DEVICES */}
          {currentTab === 'devices' && (
            <DevicesScreen
              devices={devices}
              totalDevices={stats.totalDevices}
              vipCount={stats.vipSubscribers}
              loading={loading}
              page={devicePage}
              totalPages={deviceTotalPages}
              search={deviceSearch}
              onSearchChange={(val) => {
                setDeviceSearch(val);
                setDevicePage(1);
              }}
              onPageChange={(p) => setDevicePage(p)}
              onRefresh={fetchDevices}
              onUpdateTier={handleUpdateDeviceTier}
              onToggleBlock={handleToggleDeviceBlock}
              onExportCsv={handleExportCsv}
            />
          )}

          {/* TAB 3: REMOTE CONFIG & ADMOB */}
          {currentTab === 'config' && (
            <RemoteConfigScreen
              config={config}
              onSaveConfig={handleSaveConfig}
              onRefreshConfig={refreshData}
            />
          )}

          {/* TAB 4: AUDIT TRAIL */}
          {currentTab === 'audit' && adminRole === 'SUPER_ADMIN' && (
            <AuditScreen
              logs={auditLogs}
              loading={loading}
              onRefresh={fetchAuditLogs}
            />
          )}
        </main>
      </div>
    </div>
  );
}
