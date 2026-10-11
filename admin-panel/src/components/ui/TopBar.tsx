import { RefreshCw, LogOut, ShieldCheck, Activity, Menu } from 'lucide-react';

interface TopBarProps {
  currentTab: string;
  adminEmail: string | null;
  adminRole: string | null;
  loading: boolean;
  activeTunnels: number;
  totalServers: number;
  onRefresh: () => void;
  onLogout: () => void;
  onToggleMobileMenu?: () => void;
}

export const TopBar = ({
  currentTab,
  adminEmail,
  adminRole,
  loading,
  activeTunnels,
  totalServers,
  onRefresh,
  onLogout,
  onToggleMobileMenu
}: TopBarProps) => {
  const getTabLabel = () => {
    switch (currentTab) {
      case 'servers':
        return 'WireGuard Gateway Nodes';
      case 'devices':
        return 'Client Devices & Subscriptions';
      case 'config':
        return 'Remote Config & AdMob Engine';
      case 'audit':
        return 'Administrative Audit Trail';
      default:
        return 'Dashboard Overview';
    }
  };

  return (
    <header className="sticky top-0 z-20 bg-[#070D1E]/90 backdrop-blur-md border-b border-slate-800/80 px-4 md:px-8 py-3.5 flex items-center justify-between">
      <div className="flex items-center space-x-3">
        {onToggleMobileMenu && (
          <button
            onClick={onToggleMobileMenu}
            className="md:hidden p-2 rounded-xl bg-slate-900 border border-slate-800 text-slate-300 hover:text-white"
            title="Open Menu"
          >
            <Menu className="w-5 h-5" />
          </button>
        )}
        <div>
          <div className="flex items-center space-x-2 text-[11px] text-slate-400">
            <span>Console</span>
            <span>/</span>
            <span className="text-[#00E5FF] font-medium capitalize">{currentTab}</span>
          </div>
          <h2 className="text-base md:text-lg font-bold text-white tracking-tight">{getTabLabel()}</h2>
        </div>
      </div>

      <div className="flex items-center space-x-2.5 md:space-x-3">
        {/* Live Network status indicator */}
        <div className="hidden lg:flex items-center space-x-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-medium">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
          <span>{totalServers} Nodes Active</span>
          <span className="text-emerald-500/60">•</span>
          <span className="flex items-center space-x-1">
            <Activity className="w-3 h-3 text-emerald-400" />
            <span>{activeTunnels} Live Tunnels</span>
          </span>
        </div>

        {/* Global Refresh Button */}
        <button
          onClick={onRefresh}
          disabled={loading}
          className="p-2 md:px-3 md:py-2 bg-slate-900 border border-slate-800 hover:border-slate-700 hover:bg-slate-800 rounded-xl text-slate-300 transition text-xs font-semibold flex items-center space-x-1.5 disabled:opacity-50"
          title="Refresh All System Data"
        >
          <RefreshCw className={`w-3.5 h-3.5 text-slate-300 ${loading ? 'animate-spin text-cyan-400' : ''}`} />
          <span className="hidden sm:inline">Sync Data</span>
        </button>

        {/* Admin profile pill */}
        <div className="hidden sm:flex items-center space-x-2.5 px-3 py-1.5 bg-slate-900/90 border border-slate-800 rounded-xl">
          <div className="w-6 h-6 rounded-lg bg-gradient-to-tr from-blue-600 to-cyan-500 flex items-center justify-center font-bold text-white text-[11px] shadow-sm">
            {adminEmail ? adminEmail[0].toUpperCase() : 'A'}
          </div>
          <div className="text-left text-xs">
            <div className="font-semibold text-white leading-none max-w-[130px] truncate">{adminEmail}</div>
            <div className="flex items-center space-x-1 mt-0.5">
              <ShieldCheck className="w-3 h-3 text-cyan-400" />
              <span className="text-[9px] text-cyan-400 font-bold uppercase">{adminRole || 'ADMIN'}</span>
            </div>
          </div>
        </div>

        {/* Sign Out Button */}
        <button
          onClick={onLogout}
          className="p-2 md:px-3 md:py-2 bg-rose-500/10 border border-rose-500/20 hover:bg-rose-500/20 text-rose-400 rounded-xl transition text-xs font-semibold flex items-center space-x-1.5"
          title="Sign Out"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span className="hidden md:inline">Sign Out</span>
        </button>
      </div>
    </header>
  );
};
