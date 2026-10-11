import { useState, useMemo } from 'react';
import {
  Server,
  Plus,
  Trash2,
  Activity,
  Search,
  Filter,
  Shield,
  Globe,
  RefreshCw,
  Power
} from 'lucide-react';
import { ServerNode } from '../types';
import { ConfirmModal } from '../components/ui/ConfirmModal';

interface NodesScreenProps {
  servers: ServerNode[];
  adminRole: string | null;
  loading: boolean;
  onRefresh: () => void;
  onCreateServer: (serverData: any) => Promise<boolean>;
  onToggleServer: (server: ServerNode) => Promise<boolean>;
  onDeleteServer: (id: string) => Promise<boolean>;
  onPingServer: (server: ServerNode) => Promise<{ online: boolean; peerCount?: number; loadPercent?: number; error?: string }>;
}

export const NodesScreen = ({
  servers,
  adminRole,
  loading,
  onRefresh,
  onCreateServer,
  onToggleServer,
  onDeleteServer,
  onPingServer
}: NodesScreenProps) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [filterTier, setFilterTier] = useState<'ALL' | 'FREE' | 'VIP' | 'ACTIVE' | 'DISABLED'>('ALL');
  const [showAddModal, setShowAddModal] = useState(false);
  const [pingingId, setPingingId] = useState<string | null>(null);
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // Health ping feedback cache: serverId -> health status
  const [pingResults, setPingResults] = useState<Record<string, { online: boolean; peerCount: number; loadPercent: number; timestamp: number }>>({});

  // Add Server Form State
  const [newServer, setNewServer] = useState({
    name: '',
    countryCode: 'US',
    countryName: 'United States',
    city: 'New York',
    flagEmoji: '🇺🇸',
    endpoint: '',
    publicKey: '',
    agentUrl: 'http://',
    agentSecret: '',
    isPremiumOnly: false,
    isRecommended: false
  });
  const [formErrors, setFormErrors] = useState<string[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Stats
  const totalServers = servers.length;
  const activeServers = servers.filter((s) => s.active).length;
  const vipServers = servers.filter((s) => s.isPremiumOnly).length;
  const avgLoad =
    totalServers > 0
      ? Math.round(servers.reduce((acc, s) => acc + s.currentLoad, 0) / totalServers)
      : 0;

  // Filtered & Searched servers
  const filteredServers = useMemo(() => {
    return servers.filter((server) => {
      const q = searchQuery.toLowerCase().trim();
      const matchesSearch =
        !q ||
        server.name.toLowerCase().includes(q) ||
        server.city.toLowerCase().includes(q) ||
        server.countryName.toLowerCase().includes(q) ||
        server.endpoint.toLowerCase().includes(q) ||
        server.countryCode.toLowerCase().includes(q);

      if (!matchesSearch) return false;

      if (filterTier === 'VIP') return server.isPremiumOnly;
      if (filterTier === 'FREE') return !server.isPremiumOnly;
      if (filterTier === 'ACTIVE') return server.active;
      if (filterTier === 'DISABLED') return !server.active;

      return true;
    });
  }, [servers, searchQuery, filterTier]);

  const handlePing = async (server: ServerNode) => {
    setPingingId(server.id);
    try {
      const res = await onPingServer(server);
      setPingResults((prev) => ({
        ...prev,
        [server.id]: {
          online: Boolean(res.online),
          peerCount: res.peerCount ?? 0,
          loadPercent: res.loadPercent ?? server.currentLoad,
          timestamp: Date.now()
        }
      }));
    } finally {
      setPingingId(null);
    }
  };

  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const errors: string[] = [];

    if (!newServer.name.trim()) errors.push('Server name is required.');
    if (!/^([a-zA-Z0-9.-]+):(\d+)$/.test(newServer.endpoint.trim())) {
      errors.push('Endpoint must be in format host:port (e.g. us.nsecvpn.com:51820).');
    }
    if (!/^[A-Za-z0-9+/]{43}=$/.test(newServer.publicKey.trim())) {
      errors.push('Public key must be a valid 44-character Curve25519 base64 string.');
    }
    if (!newServer.agentUrl.startsWith('http://') && !newServer.agentUrl.startsWith('https://')) {
      errors.push('Agent URL must be a valid http:// or https:// URL.');
    }
    if (newServer.agentSecret.trim().length < 16) {
      errors.push('Agent secret must be at least 16 characters.');
    }

    if (errors.length > 0) {
      setFormErrors(errors);
      return;
    }

    setFormErrors([]);
    setIsSubmitting(true);
    try {
      const ok = await onCreateServer(newServer);
      if (ok) {
        setShowAddModal(false);
        setNewServer({
          name: '',
          countryCode: 'US',
          countryName: 'United States',
          city: 'New York',
          flagEmoji: '🇺🇸',
          endpoint: '',
          publicKey: '',
          agentUrl: 'http://',
          agentSecret: '',
          isPremiumOnly: false,
          isRecommended: false
        });
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleteTargetId) return;
    setIsDeleting(true);
    try {
      await onDeleteServer(deleteTargetId);
      setDeleteTargetId(null);
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Metric Stat Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-[#0B132B] border border-slate-800 p-5 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
            <span>CLUSTER NODES</span>
            <Server className="w-4 h-4 text-blue-400" />
          </div>
          <div className="text-2xl font-black text-white">{totalServers}</div>
          <div className="text-xs text-blue-400 mt-1">{activeServers} Online Clusters</div>
        </div>

        <div className="bg-[#0B132B] border border-slate-800 p-5 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
            <span>AVERAGE LOAD</span>
            <Activity className="w-4 h-4 text-cyan-400" />
          </div>
          <div className="text-2xl font-black text-white">{avgLoad}%</div>
          <div className="text-xs text-cyan-400 mt-1">Network Capacity</div>
        </div>

        <div className="bg-[#0B132B] border border-slate-800 p-5 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
            <span>VIP CLUSTERS</span>
            <Shield className="w-4 h-4 text-amber-400" />
          </div>
          <div className="text-2xl font-black text-white">{vipServers}</div>
          <div className="text-xs text-amber-400 mt-1">10Gbps Dedicated</div>
        </div>

        <div className="bg-[#0B132B] border border-slate-800 p-5 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
            <span>COMMUNITY NODES</span>
            <Globe className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-black text-white">{totalServers - vipServers}</div>
          <div className="text-xs text-emerald-400 mt-1">Free Tier Gateways</div>
        </div>
      </div>

      {/* Action Controls & Filters Bar */}
      <div className="bg-[#0B132B] border border-slate-800 p-4 rounded-2xl shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        {/* Search Input */}
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by name, city, country, or endpoint..."
            className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-10 pr-4 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
          />
        </div>

        {/* Filter Pills */}
        <div className="flex items-center space-x-2 overflow-x-auto pb-1 md:pb-0">
          <span className="text-[11px] font-semibold text-slate-500 flex items-center space-x-1 pl-1">
            <Filter className="w-3.5 h-3.5" />
            <span>Filter:</span>
          </span>

          {(['ALL', 'ACTIVE', 'VIP', 'FREE', 'DISABLED'] as const).map((tier) => (
            <button
              key={tier}
              onClick={() => setFilterTier(tier)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                filterTier === tier
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                  : 'bg-slate-900 text-slate-400 hover:text-white hover:bg-slate-800 border border-slate-800/80'
              }`}
            >
              {tier}
            </button>
          ))}

          <button
            onClick={onRefresh}
            disabled={loading}
            className="p-2 bg-slate-900 border border-slate-800 hover:border-slate-700 hover:bg-slate-800 rounded-xl text-slate-300 transition"
            title="Refresh Nodes"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-cyan-400' : ''}`} />
          </button>

          <button
            onClick={() => setShowAddModal(true)}
            className="flex items-center space-x-1.5 bg-gradient-to-r from-blue-600 to-cyan-500 hover:from-blue-500 hover:to-cyan-400 text-white font-bold px-4 py-2 rounded-xl text-xs transition shadow-lg shadow-cyan-500/20 shrink-0 ml-1"
          >
            <Plus className="w-4 h-4" />
            <span>Add Gateway Node</span>
          </button>
        </div>
      </div>

      {/* Nodes Table */}
      <div className="bg-[#0B132B] border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-800 text-[11px] text-slate-400 font-bold bg-slate-900/60 uppercase tracking-wider">
                <th className="p-4">Location & Cluster</th>
                <th className="p-4">WireGuard Endpoint</th>
                <th className="p-4">Server Load</th>
                <th className="p-4">Access Tier</th>
                <th className="p-4">Agent Health</th>
                <th className="p-4">Status</th>
                <th className="p-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-xs">
              {filteredServers.map((server) => {
                const ping = pingResults[server.id];
                const isPinging = pingingId === server.id;

                return (
                  <tr key={server.id} className="hover:bg-slate-900/40 transition">
                    {/* Location */}
                    <td className="p-4">
                      <div className="flex items-center space-x-3">
                        <span className="text-2xl select-none">{server.flagEmoji}</span>
                        <div>
                          <div className="font-bold text-white flex items-center space-x-1.5">
                            <span>{server.name}</span>
                            {server.isRecommended && (
                              <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                                RECOMMENDED
                              </span>
                            )}
                          </div>
                          <div className="text-[11px] text-slate-400">
                            {server.city}, {server.countryName} ({server.countryCode})
                          </div>
                        </div>
                      </div>
                    </td>

                    {/* Endpoint */}
                    <td className="p-4 font-mono text-slate-300">
                      <div className="text-xs">{server.endpoint}</div>
                      <div className="text-[10px] text-slate-500 truncate max-w-[150px]" title={server.publicKey}>
                        Key: {server.publicKey.slice(0, 16)}...
                      </div>
                    </td>

                    {/* Server Load Bar */}
                    <td className="p-4">
                      <div className="space-y-1">
                        <div className="flex items-center justify-between text-[11px]">
                          <span className="text-slate-400">Capacity</span>
                          <span
                            className={`font-mono font-bold ${
                              server.currentLoad > 80
                                ? 'text-rose-400'
                                : server.currentLoad > 50
                                ? 'text-amber-400'
                                : 'text-emerald-400'
                            }`}
                          >
                            {server.currentLoad}%
                          </span>
                        </div>
                        <div className="w-24 bg-slate-800 h-1.5 rounded-full overflow-hidden">
                          <div
                            className={`h-full transition-all duration-500 ${
                              server.currentLoad > 80
                                ? 'bg-rose-500'
                                : server.currentLoad > 50
                                ? 'bg-amber-400'
                                : 'bg-emerald-400'
                            }`}
                            style={{ width: `${Math.min(server.currentLoad, 100)}%` }}
                          />
                        </div>
                      </div>
                    </td>

                    {/* Access Tier */}
                    <td className="p-4">
                      {server.isPremiumOnly ? (
                        <span className="px-2.5 py-1 rounded-md text-[11px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30 flex items-center space-x-1 w-max">
                          <Shield className="w-3 h-3 text-amber-400" />
                          <span>VIP ONLY</span>
                        </span>
                      ) : (
                        <span className="px-2.5 py-1 rounded-md text-[11px] font-bold bg-slate-800 text-slate-300 border border-slate-700/60 w-max block">
                          FREE TIER
                        </span>
                      )}
                    </td>

                    {/* Live Agent Health */}
                    <td className="p-4">
                      {ping ? (
                        ping.online ? (
                          <div className="text-[11px] text-emerald-400 font-medium">
                            <div className="flex items-center space-x-1 font-bold">
                              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
                              <span>Daemon Online</span>
                            </div>
                            <div className="text-[10px] text-slate-400 font-mono">
                              {ping.peerCount} peers • {ping.loadPercent}% CPU
                            </div>
                          </div>
                        ) : (
                          <div className="text-[11px] text-rose-400 font-medium">
                            <div className="flex items-center space-x-1 font-bold">
                              <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />
                              <span>Unreachable</span>
                            </div>
                          </div>
                        )
                      ) : (
                        <span className="text-slate-500 text-[11px]">Not pinged</span>
                      )}
                    </td>

                    {/* Active Status Switch */}
                    <td className="p-4">
                      <button
                        onClick={() => onToggleServer(server)}
                        className={`px-3 py-1 rounded-full text-xs font-semibold transition flex items-center space-x-1.5 ${
                          server.active
                            ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 hover:bg-emerald-500/20'
                            : 'bg-rose-500/10 text-rose-400 border border-rose-500/30 hover:bg-rose-500/20'
                        }`}
                      >
                        <Power className="w-3 h-3" />
                        <span>{server.active ? 'Active' : 'Disabled'}</span>
                      </button>
                    </td>

                    {/* Actions */}
                    <td className="p-4 text-right space-x-2">
                      <button
                        onClick={() => handlePing(server)}
                        disabled={isPinging}
                        className="p-1.5 text-slate-400 hover:text-cyan-400 hover:bg-cyan-500/10 rounded-lg transition"
                        title="Query Node Health via Daemon"
                      >
                        <Activity className={`w-4 h-4 ${isPinging ? 'animate-spin text-cyan-400' : ''}`} />
                      </button>

                      {adminRole === 'SUPER_ADMIN' && (
                        <button
                          onClick={() => setDeleteTargetId(server.id)}
                          className="p-1.5 text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition"
                          title="Delete Gateway Node"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}

              {filteredServers.length === 0 && (
                <tr>
                  <td colSpan={7} className="p-12 text-center text-slate-500">
                    <Server className="w-8 h-8 mx-auto text-slate-600 mb-2" />
                    <div className="text-sm font-semibold text-slate-400">No WireGuard nodes found</div>
                    <p className="text-xs text-slate-500 mt-1">
                      {searchQuery
                        ? 'Try adjusting your search query or filter.'
                        : 'Click "Add Gateway Node" to register your first WireGuard server.'}
                    </p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      <ConfirmModal
        isOpen={Boolean(deleteTargetId)}
        title="Delete WireGuard Gateway Node"
        message="Are you sure you want to permanently delete this VPN server node? Any active peer leases associated with this node will be revoked."
        confirmLabel="Delete Node"
        cancelLabel="Keep Node"
        isDestructive={true}
        isLoading={isDeleting}
        onConfirm={confirmDelete}
        onCancel={() => setDeleteTargetId(null)}
      />

      {/* Add Server Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-[#0B132B] border border-slate-800 rounded-2xl w-full max-w-xl p-6 shadow-2xl space-y-5 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div>
                <h3 className="font-bold text-white text-base">Add WireGuard Gateway Node</h3>
                <p className="text-xs text-slate-400">Register endpoint parameters and agent credentials</p>
              </div>
              <button
                onClick={() => setShowAddModal(false)}
                className="text-slate-400 hover:text-white p-1 rounded-lg"
              >
                ✕
              </button>
            </div>

            {formErrors.length > 0 && (
              <div className="bg-rose-500/10 border border-rose-500/30 rounded-xl p-3 text-xs text-rose-300 space-y-1">
                {formErrors.map((err, i) => (
                  <div key={i}>• {err}</div>
                ))}
              </div>
            )}

            <form onSubmit={handleCreateSubmit} className="space-y-4">
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">NODE CLUSTER NAME</label>
                <input
                  type="text"
                  placeholder="e.g. US East (New York)"
                  value={newServer.name}
                  onChange={(e) => setNewServer({ ...newServer, name: e.target.value })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-cyan-500"
                />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">COUNTRY CODE</label>
                  <input
                    type="text"
                    maxLength={2}
                    placeholder="US"
                    value={newServer.countryCode}
                    onChange={(e) =>
                      setNewServer({ ...newServer, countryCode: e.target.value.toUpperCase() })
                    }
                    required
                    className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-cyan-500 font-mono"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">CITY</label>
                  <input
                    type="text"
                    placeholder="New York"
                    value={newServer.city}
                    onChange={(e) => setNewServer({ ...newServer, city: e.target.value })}
                    required
                    className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-cyan-500"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">FLAG EMOJI</label>
                  <input
                    type="text"
                    placeholder="🇺🇸"
                    value={newServer.flagEmoji}
                    onChange={(e) => setNewServer({ ...newServer, flagEmoji: e.target.value })}
                    required
                    className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white text-center focus:outline-none focus:border-cyan-500"
                  />
                </div>
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">
                  WIREGUARD ENDPOINT (HOST:PORT)
                </label>
                <input
                  type="text"
                  placeholder="us.nsecvpn.com:51820"
                  value={newServer.endpoint}
                  onChange={(e) => setNewServer({ ...newServer, endpoint: e.target.value.trim() })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-cyan-500 font-mono"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">
                  SERVER PUBLIC KEY (CURVE25519)
                </label>
                <input
                  type="text"
                  placeholder="8J5aOQv4rTqK3F6mE5nP7sL9wY2zU1xV4bN7mQ0kP8M="
                  value={newServer.publicKey}
                  onChange={(e) => setNewServer({ ...newServer, publicKey: e.target.value.trim() })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-cyan-500 font-mono"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">NODE AGENT URL</label>
                <input
                  type="text"
                  placeholder="http://198.51.100.24:51821"
                  value={newServer.agentUrl}
                  onChange={(e) => setNewServer({ ...newServer, agentUrl: e.target.value.trim() })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-cyan-500 font-mono"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">
                  AGENT SHARED HMAC SECRET
                </label>
                <input
                  type="password"
                  placeholder="Generated by install-wireguard-node.sh"
                  value={newServer.agentSecret}
                  onChange={(e) => setNewServer({ ...newServer, agentSecret: e.target.value.trim() })}
                  required
                  className="w-full bg-slate-900 border border-slate-800 rounded-xl px-3.5 py-2 text-xs text-white focus:outline-none focus:border-cyan-500 font-mono"
                />
              </div>

              <div className="grid grid-cols-2 gap-3 pt-2">
                <label className="flex items-center space-x-2 text-xs text-slate-300 p-3 bg-slate-900/60 rounded-xl border border-slate-800 cursor-pointer">
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

                <label className="flex items-center space-x-2 text-xs text-slate-300 p-3 bg-slate-900/60 rounded-xl border border-slate-800 cursor-pointer">
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

              <div className="flex items-center justify-end space-x-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-300 bg-slate-800 hover:bg-slate-700 transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-5 py-2 rounded-xl text-xs font-bold text-white bg-blue-600 hover:bg-blue-500 transition shadow-lg shadow-blue-600/20 disabled:opacity-50 flex items-center space-x-2"
                >
                  {isSubmitting && (
                    <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  )}
                  <span>Save & Register Node</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
