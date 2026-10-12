import { Server, Users, Settings, FileText, Activity, Shield, LogOut, X } from 'lucide-react';

interface SidebarProps {
  currentTab: 'servers' | 'devices' | 'config' | 'audit';
  setCurrentTab: (tab: 'servers' | 'devices' | 'config' | 'audit') => void;
  adminRole: string | null;
  serverCount: number;
  deviceCount: number | null;
  adminEmail?: string | null;
  onLogout?: () => void;
  mobileOpen?: boolean;
  onCloseMobile?: () => void;
}

export const Sidebar = ({
  currentTab,
  setCurrentTab,
  adminRole,
  serverCount,
  deviceCount,
  adminEmail,
  onLogout,
  mobileOpen = false,
  onCloseMobile
}: SidebarProps) => {
  const navItems = [
    {
      id: 'servers' as const,
      label: 'WireGuard Nodes',
      icon: Server,
      badge: serverCount > 0 ? serverCount : undefined
    },
    {
      id: 'devices' as const,
      label: 'Devices & VIP Tiers',
      icon: Users,
      badge: deviceCount !== null && deviceCount > 0 ? deviceCount : undefined
    },
    {
      id: 'config' as const,
      label: 'Remote Config & Ads',
      icon: Settings
    },
    ...(adminRole === 'SUPER_ADMIN'
      ? [
          {
            id: 'audit' as const,
            label: 'Audit Trail',
            icon: FileText
          }
        ]
      : [])
  ];

  const sidebarContent = (
    <div className="h-full flex flex-col justify-between">
      <div>
        {/* Brand Header */}
        <div className="flex items-center justify-between mb-8 px-2">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-[#2563EB] to-[#00E5FF] flex items-center justify-center font-black text-xl text-white shadow-lg shadow-cyan-500/20">
              N
            </div>
            <div>
              <h1 className="font-extrabold text-base text-white tracking-tight flex items-center space-x-1.5">
                <span>Nsec VPN</span>
                <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-400 border border-blue-500/30">
                  PRO
                </span>
              </h1>
              <p className="text-[11px] text-[#00E5FF] font-medium tracking-wide">nsecvpn.com Console</p>
            </div>
          </div>
          {onCloseMobile && (
            <button
              onClick={onCloseMobile}
              className="md:hidden text-slate-400 hover:text-white p-1 rounded-lg"
            >
              <X className="w-5 h-5" />
            </button>
          )}
        </div>

        {/* Navigation Links */}
        <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider px-3 mb-2">
          Management
        </div>

        <nav className="space-y-1.5">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = currentTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => {
                  setCurrentTab(item.id);
                  onCloseMobile?.();
                }}
                className={`w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold transition group ${
                  isActive
                    ? 'bg-gradient-to-r from-blue-600 to-blue-500 text-white shadow-lg shadow-blue-500/20'
                    : 'text-slate-400 hover:text-white hover:bg-slate-900/80'
                }`}
              >
                <div className="flex items-center space-x-3">
                  <Icon
                    className={`w-4 h-4 transition ${
                      isActive ? 'text-white' : 'text-slate-400 group-hover:text-cyan-400'
                    }`}
                  />
                  <span>{item.label}</span>
                </div>

                {item.badge !== undefined && (
                  <span
                    className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                      isActive ? 'bg-white/20 text-white' : 'bg-slate-800 text-slate-400'
                    }`}
                  >
                    {item.badge}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      </div>

      {/* System Status & Admin Profile Footer */}
      <div className="pt-4 border-t border-slate-800/80 space-y-3">
        <div className="p-3 bg-slate-900/60 border border-slate-800/60 rounded-xl">
          <div className="flex items-center justify-between text-[11px] text-slate-400 font-medium mb-1">
            <span className="flex items-center space-x-1.5">
              <Activity className="w-3.5 h-3.5 text-emerald-400" />
              <span>Core Engine</span>
            </span>
            <span className="text-emerald-400 font-bold text-[10px]">WireGuard® Go</span>
          </div>
          <div className="text-[10px] text-slate-500">Volatile In-Memory Routing • Zero-Logs</div>
        </div>

        {adminEmail && (
          <div className="flex items-center justify-between px-2 py-1 text-xs">
            <div className="flex items-center space-x-2 truncate">
              <Shield className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
              <span className="text-slate-300 truncate text-[11px]">{adminEmail}</span>
            </div>
            {onLogout && (
              <button
                onClick={onLogout}
                className="text-slate-400 hover:text-rose-400 p-1 rounded transition"
                title="Log Out"
              >
                <LogOut className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );

  return (
    <>
      {/* Desktop Persistent Sidebar */}
      <aside className="hidden md:flex w-64 bg-[#070D1E] border-r border-slate-800/80 p-5 flex-col shrink-0 min-h-screen">
        {sidebarContent}
      </aside>

      {/* Mobile Drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 md:hidden flex">
          <div
            className="fixed inset-0 bg-black/70 backdrop-blur-sm transition-opacity"
            onClick={onCloseMobile}
          />
          <aside className="relative w-72 max-w-[80vw] bg-[#070D1E] border-r border-slate-800 p-5 flex flex-col z-10 shadow-2xl">
            {sidebarContent}
          </aside>
        </div>
      )}
    </>
  );
};
