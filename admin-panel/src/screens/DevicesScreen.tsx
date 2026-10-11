import { useState, useMemo } from 'react';
import {
  Users,
  Search,
  Download,
  Ban,
  CheckCircle,
  ChevronLeft,
  ChevronRight,
  Shield,
  Filter,
  RefreshCw,
  Smartphone
} from 'lucide-react';
import { DeviceUser } from '../types';
import { ConfirmModal } from '../components/ui/ConfirmModal';

interface DevicesScreenProps {
  devices: DeviceUser[];
  totalDevices: number;
  vipCount: number;
  loading: boolean;
  page: number;
  totalPages: number;
  search: string;
  onSearchChange: (search: string) => void;
  onPageChange: (page: number) => void;
  onRefresh: () => void;
  onUpdateTier: (deviceId: string, tier: 'FREE' | 'VIP') => Promise<boolean>;
  onToggleBlock: (device: DeviceUser) => Promise<boolean>;
  onExportCsv: () => void;
}

export const DevicesScreen = ({
  devices,
  totalDevices,
  vipCount,
  loading,
  page,
  totalPages,
  search,
  onSearchChange,
  onPageChange,
  onRefresh,
  onUpdateTier,
  onToggleBlock,
  onExportCsv
}: DevicesScreenProps) => {
  const [tierFilter, setTierFilter] = useState<'ALL' | 'VIP' | 'FREE' | 'REWARDED_PASS'>('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'BLOCKED'>('ALL');

  // Confirmation modal states
  const [tierTarget, setTierTarget] = useState<{ id: string; targetTier: 'FREE' | 'VIP' } | null>(null);
  const [blockTarget, setBlockTarget] = useState<DeviceUser | null>(null);
  const [isProcessingAction, setIsProcessingAction] = useState(false);

  // Filter devices locally if tier or status is selected
  const filteredDevices = useMemo(() => {
    return devices.filter((d) => {
      if (tierFilter !== 'ALL' && d.tier !== tierFilter) return false;
      if (statusFilter === 'ACTIVE' && d.isBlocked) return false;
      if (statusFilter === 'BLOCKED' && !d.isBlocked) return false;
      return true;
    });
  }, [devices, tierFilter, statusFilter]);

  const confirmTierChange = async () => {
    if (!tierTarget) return;
    setIsProcessingAction(true);
    try {
      await onUpdateTier(tierTarget.id, tierTarget.targetTier);
      setTierTarget(null);
    } finally {
      setIsProcessingAction(false);
    }
  };

  const confirmToggleBlock = async () => {
    if (!blockTarget) return;
    setIsProcessingAction(true);
    try {
      await onToggleBlock(blockTarget);
      setBlockTarget(null);
    } finally {
      setIsProcessingAction(false);
    }
  };

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Metric Stat Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-[#0B132B] border border-slate-800 p-5 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
            <span>REGISTERED HARDWARE</span>
            <Users className="w-4 h-4 text-cyan-400" />
          </div>
          <div className="text-2xl font-black text-white">{totalDevices}</div>
          <div className="text-xs text-cyan-400 mt-1">Unique Keystore UUIDs</div>
        </div>

        <div className="bg-[#0B132B] border border-slate-800 p-5 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
            <span>VIP SUBSCRIBERS</span>
            <Shield className="w-4 h-4 text-amber-400" />
          </div>
          <div className="text-2xl font-black text-white">{vipCount}</div>
          <div className="text-xs text-amber-400 mt-1">Active Paid Licenses</div>
        </div>

        <div className="bg-[#0B132B] border border-slate-800 p-5 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
            <span>COMMUNITY USERS</span>
            <Smartphone className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-black text-white">{Math.max(0, totalDevices - vipCount)}</div>
          <div className="text-xs text-emerald-400 mt-1">Free Tier Fleet</div>
        </div>

        <div className="bg-[#0B132B] border border-slate-800 p-5 rounded-2xl shadow-lg">
          <div className="flex items-center justify-between text-slate-400 text-xs font-semibold mb-2">
            <span>PAGE WINDOW</span>
            <Filter className="w-4 h-4 text-blue-400" />
          </div>
          <div className="text-2xl font-black text-white">Page {page}</div>
          <div className="text-xs text-blue-400 mt-1">of {totalPages} pages</div>
        </div>
      </div>

      {/* Action Controls & Filters Bar */}
      <div className="bg-[#0B132B] border border-slate-800 p-4 rounded-2xl shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        {/* Search Input */}
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
          <input
            type="text"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search by Device UUID..."
            className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-10 pr-4 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500 font-mono"
          />
        </div>

        {/* Filter Pills & Actions */}
        <div className="flex items-center space-x-2 overflow-x-auto pb-1 md:pb-0">
          <span className="text-[11px] font-semibold text-slate-500 flex items-center space-x-1 pl-1">
            <Filter className="w-3.5 h-3.5" />
            <span>Tier:</span>
          </span>

          {(['ALL', 'VIP', 'FREE', 'REWARDED_PASS'] as const).map((tier) => (
            <button
              key={tier}
              onClick={() => setTierFilter(tier)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                tierFilter === tier
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                  : 'bg-slate-900 text-slate-400 hover:text-white hover:bg-slate-800 border border-slate-800/80'
              }`}
            >
              {tier}
            </button>
          ))}

          <span className="text-[11px] font-semibold text-slate-500 pl-2">Status:</span>
          {(['ALL', 'ACTIVE', 'BLOCKED'] as const).map((status) => (
            <button
              key={status}
              onClick={() => setStatusFilter(status)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition ${
                statusFilter === status
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                  : 'bg-slate-900 text-slate-400 hover:text-white hover:bg-slate-800 border border-slate-800/80'
              }`}
            >
              {status}
            </button>
          ))}

          <button
            onClick={onRefresh}
            disabled={loading}
            className="p-2 bg-slate-900 border border-slate-800 hover:border-slate-700 hover:bg-slate-800 rounded-xl text-slate-300 transition"
            title="Refresh Devices"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-cyan-400' : ''}`} />
          </button>

          <button
            onClick={onExportCsv}
            className="flex items-center space-x-1.5 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-200 px-3.5 py-2 rounded-xl text-xs font-semibold transition shrink-0"
          >
            <Download className="w-3.5 h-3.5 text-cyan-400" />
            <span>Export CSV</span>
          </button>
        </div>
      </div>

      {/* Devices Table */}
      <div className="bg-[#0B132B] border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-800 text-[11px] text-slate-400 font-bold bg-slate-900/60 uppercase tracking-wider">
                <th className="p-4">Device UUID</th>
                <th className="p-4">Hardware Model & OS</th>
                <th className="p-4">Subscription Tier</th>
                <th className="p-4">Registration Date</th>
                <th className="p-4">Status</th>
                <th className="p-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-xs">
              {filteredDevices.map((device) => (
                <tr key={device.id} className="hover:bg-slate-900/40 transition">
                  {/* UUID */}
                  <td className="p-4 font-mono">
                    <div className="flex items-center space-x-2">
                      <span
                        className={`w-2 h-2 rounded-full shrink-0 ${
                          device.isBlocked ? 'bg-rose-500' : 'bg-emerald-500'
                        }`}
                      />
                      <span className="text-white font-medium select-all" title={device.deviceId}>
                        {device.deviceId.length > 24 ? `${device.deviceId.slice(0, 24)}...` : device.deviceId}
                      </span>
                    </div>
                  </td>

                  {/* Hardware */}
                  <td className="p-4 text-slate-300">
                    <div>{device.deviceModel || 'Android Device'}</div>
                    <div className="text-[11px] text-slate-500">{device.osVersion || 'Android OS'}</div>
                  </td>

                  {/* Tier */}
                  <td className="p-4">
                    {device.tier === 'VIP' ? (
                      <span className="px-2.5 py-1 rounded-md text-[11px] font-bold bg-amber-500/15 text-amber-300 border border-amber-500/30 inline-flex items-center space-x-1">
                        <Shield className="w-3 h-3 text-amber-400" />
                        <span>VIP</span>
                      </span>
                    ) : device.tier === 'REWARDED_PASS' ? (
                      <span className="px-2.5 py-1 rounded-md text-[11px] font-bold bg-blue-500/15 text-blue-300 border border-blue-500/30">
                        PASS
                      </span>
                    ) : (
                      <span className="px-2.5 py-1 rounded-md text-[11px] font-bold bg-slate-800 text-slate-400">
                        FREE
                      </span>
                    )}
                  </td>

                  {/* Date */}
                  <td className="p-4 text-slate-400">
                    {new Date(device.createdAt).toLocaleDateString()}
                  </td>

                  {/* Block Status */}
                  <td className="p-4">
                    <span
                      className={`px-2.5 py-0.5 rounded-full text-[11px] font-medium ${
                        device.isBlocked
                          ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                          : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                      }`}
                    >
                      {device.isBlocked ? 'Blocked' : 'Active'}
                    </span>
                  </td>

                  {/* Actions */}
                  <td className="p-4 text-right space-x-2">
                    <button
                      onClick={() =>
                        setTierTarget({
                          id: device.id,
                          targetTier: device.tier === 'VIP' ? 'FREE' : 'VIP'
                        })
                      }
                      className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-xs font-semibold rounded-lg text-slate-300 transition"
                    >
                      {device.tier === 'VIP' ? 'Set Free' : 'Grant VIP'}
                    </button>

                    <button
                      onClick={() => setBlockTarget(device)}
                      className={`p-1.5 rounded-lg transition ${
                        device.isBlocked
                          ? 'text-emerald-400 hover:bg-emerald-500/10'
                          : 'text-rose-400 hover:bg-rose-500/10'
                      }`}
                      title={device.isBlocked ? 'Unblock Client' : 'Block Client'}
                    >
                      {device.isBlocked ? <CheckCircle className="w-4 h-4" /> : <Ban className="w-4 h-4" />}
                    </button>
                  </td>
                </tr>
              ))}

              {filteredDevices.length === 0 && (
                <tr>
                  <td colSpan={6} className="p-12 text-center text-slate-500">
                    <Users className="w-8 h-8 mx-auto text-slate-600 mb-2" />
                    <div className="text-sm font-semibold text-slate-400">No client devices found</div>
                    <p className="text-xs text-slate-500 mt-1">
                      {search ? 'Try adjusting your search query.' : 'Waiting for clients to register with the network.'}
                    </p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        <div className="p-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
          <span>
            Showing {filteredDevices.length} of {totalDevices} total registered devices
          </span>
          <div className="flex items-center space-x-2">
            <button
              disabled={page <= 1 || loading}
              onClick={() => onPageChange(page - 1)}
              className="p-1.5 bg-slate-900 border border-slate-800 hover:bg-slate-800 disabled:opacity-40 rounded-lg text-white"
            >
              <ChevronLeft className="w-4 h-4" />
            </button>
            <span className="font-mono px-2 text-slate-300">
              {page} / {totalPages || 1}
            </span>
            <button
              disabled={page >= totalPages || loading}
              onClick={() => onPageChange(page + 1)}
              className="p-1.5 bg-slate-900 border border-slate-800 hover:bg-slate-800 disabled:opacity-40 rounded-lg text-white"
            >
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>

      {/* Tier Override Confirmation */}
      <ConfirmModal
        isOpen={Boolean(tierTarget)}
        title="Update Subscription Tier"
        message={`Are you sure you want to change this client's tier to ${tierTarget?.targetTier}? VIP grants unrestricted access to high-speed dedicated gateway nodes.`}
        confirmLabel="Update Tier"
        cancelLabel="Cancel"
        isLoading={isProcessingAction}
        onConfirm={confirmTierChange}
        onCancel={() => setTierTarget(null)}
      />

      {/* Block Device Confirmation */}
      <ConfirmModal
        isOpen={Boolean(blockTarget)}
        title={blockTarget?.isBlocked ? 'Unblock Client Device' : 'Block Client Device'}
        message={
          blockTarget?.isBlocked
            ? 'Are you sure you want to restore access for this device? It will be permitted to authenticate and tunnel traffic again.'
            : 'Are you sure you want to block this device? It will be rejected from registering WireGuard peer sessions.'
        }
        confirmLabel={blockTarget?.isBlocked ? 'Unblock' : 'Block Device'}
        cancelLabel="Cancel"
        isDestructive={!blockTarget?.isBlocked}
        isLoading={isProcessingAction}
        onConfirm={confirmToggleBlock}
        onCancel={() => setBlockTarget(null)}
      />
    </div>
  );
};
