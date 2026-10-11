import { Server, Users, Settings, FileText, LogOut } from 'lucide-react';

export const Sidebar = ({ currentTab, setCurrentTab, adminRole, onLogout, email }: any) => {
  const navItems = [
    { id: 'servers', label: 'WireGuard Nodes', icon: Server },
    { id: 'devices', label: 'Devices & VIP Tiers', icon: Users },
    { id: 'config', label: 'Remote Config & Ads', icon: Settings },
    ...(adminRole === 'SUPER_ADMIN' ? [{ id: 'audit', label: 'Audit Logs', icon: FileText }] : [])
  ];

  return (
    <aside className="w-64 bg-[#0A122E] border-r border-slate-800 p-6 flex flex-col justify-between h-screen">
      <div>
        <div className="flex items-center space-x-3 mb-8">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-[#2563EB] to-[#00E5FF] flex items-center justify-center font-black text-xl text-white shadow-lg shadow-cyan-500/20">
            N
          </div>
          <div>
            <h1 className="font-bold text-lg text-white">Nsec VPN</h1>
            <p className="text-xs text-[#00E5FF] font-medium tracking-wide">Admin Console</p>
          </div>
        </div>

        <nav className="space-y-1.5">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                onClick={() => setCurrentTab(item.id)}
                className={`w-full flex items-center space-x-3 px-3.5 py-2.5 rounded-lg text-sm font-medium transition ${
                  currentTab === item.id
                    ? 'bg-[#2563EB] text-white shadow-sm'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
                }`}
              >
                <Icon className="w-4 h-4" />
                <span>{item.label}</span>
              </button>
            );
          })}
        </nav>
      </div>

      <div className="pt-6 border-t border-slate-800 space-y-3">
        <div className="text-xs text-slate-400">
          Logged in as: <br />
          <strong className="text-white">{email}</strong>
        </div>
        <button
          onClick={onLogout}
          className="w-full flex items-center space-x-2 text-xs font-semibold text-rose-400 hover:text-rose-300 transition"
        >
          <LogOut className="w-4 h-4" />
          <span>Sign Out</span>
        </button>
      </div>
    </aside>
  );
};
