import { useState, useMemo } from 'react';
import {
  FileText,
  Search,
  Filter,
  RefreshCw,
  Clock,
  Shield,
  Eye,
  X
} from 'lucide-react';
import { AuditLogEntry } from '../types';

interface AuditScreenProps {
  logs: AuditLogEntry[];
  loading: boolean;
  onRefresh: () => void;
}

export const AuditScreen = ({ logs, loading, onRefresh }: AuditScreenProps) => {
  const [search, setSearch] = useState('');
  const [actionFilter, setActionFilter] = useState<string>('ALL');
  const [inspectLog, setInspectLog] = useState<AuditLogEntry | null>(null);

  // Distinct action types
  const actionTypes = useMemo(() => {
    const set = new Set<string>();
    logs.forEach((l) => set.add(l.action));
    return ['ALL', ...Array.from(set)];
  }, [logs]);

  const filteredLogs = useMemo(() => {
    return logs.filter((log) => {
      const q = search.toLowerCase().trim();
      const matchesSearch =
        !q ||
        log.action.toLowerCase().includes(q) ||
        log.targetResource.toLowerCase().includes(q) ||
        (log.targetId && log.targetId.toLowerCase().includes(q)) ||
        (log.admin?.email && log.admin.email.toLowerCase().includes(q));

      if (!matchesSearch) return false;
      if (actionFilter !== 'ALL' && log.action !== actionFilter) return false;
      return true;
    });
  }, [logs, search, actionFilter]);

  return (
    <div className="space-y-6 max-w-7xl mx-auto">
      {/* Top Header Card */}
      <div className="bg-[#0B132B] border border-slate-800 p-6 rounded-2xl shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2 text-xs font-semibold text-cyan-400 mb-1">
            <Shield className="w-4 h-4" />
            <span>IMMUTABLE SYSTEM AUDIT TRAIL</span>
          </div>
          <h2 className="text-xl font-bold text-white tracking-tight">Administrative Audit Logs</h2>
          <p className="text-xs text-slate-400 mt-1">
            Security audit ledger tracking all node provisioning, config revisions, and client tier adjustments.
          </p>
        </div>

        <button
          onClick={onRefresh}
          disabled={loading}
          className="flex items-center space-x-2 px-4 py-2.5 rounded-xl text-xs font-semibold text-slate-300 bg-slate-900 border border-slate-800 hover:bg-slate-800 transition disabled:opacity-50 shrink-0"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin text-cyan-400' : ''}`} />
          <span>Refresh Ledger</span>
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-[#0B132B] border border-slate-800 p-4 rounded-2xl shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-500 absolute left-3.5 top-3" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by action, email, or target..."
            className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-10 pr-4 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-cyan-500"
          />
        </div>

        <div className="flex items-center space-x-2 overflow-x-auto pb-1 md:pb-0">
          <span className="text-[11px] font-semibold text-slate-500 flex items-center space-x-1 pl-1">
            <Filter className="w-3.5 h-3.5" />
            <span>Action:</span>
          </span>

          {actionTypes.slice(0, 6).map((type) => (
            <button
              key={type}
              onClick={() => setActionFilter(type)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition shrink-0 ${
                actionFilter === type
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                  : 'bg-slate-900 text-slate-400 hover:text-white hover:bg-slate-800 border border-slate-800/80'
              }`}
            >
              {type}
            </button>
          ))}
        </div>
      </div>

      {/* Logs Table */}
      <div className="bg-[#0B132B] border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-800 text-[11px] text-slate-400 font-bold bg-slate-900/60 uppercase tracking-wider">
                <th className="p-4">Timestamp</th>
                <th className="p-4">Administrator</th>
                <th className="p-4">Action</th>
                <th className="p-4">Resource</th>
                <th className="p-4">Details</th>
                <th className="p-4 text-right">Inspect</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-xs font-mono">
              {filteredLogs.map((log) => (
                <tr key={log.id} className="hover:bg-slate-900/40 transition">
                  <td className="p-4 text-slate-400 whitespace-nowrap">
                    <div className="flex items-center space-x-1.5">
                      <Clock className="w-3.5 h-3.5 text-slate-500" />
                      <span>{new Date(log.createdAt).toLocaleString()}</span>
                    </div>
                  </td>

                  <td className="p-4 font-sans text-white">
                    <div className="font-semibold">{log.admin?.email || log.adminId}</div>
                    <div className="text-[10px] text-cyan-400 font-bold uppercase">
                      {log.admin?.role || 'ADMIN'}
                    </div>
                  </td>

                  <td className="p-4">
                    <span className="px-2.5 py-1 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20 font-bold text-[11px]">
                      {log.action}
                    </span>
                  </td>

                  <td className="p-4 text-slate-300 font-sans">
                    {log.targetResource}
                    {log.targetId && (
                      <span className="text-[10px] text-slate-500 block font-mono">ID: {log.targetId.slice(0, 8)}...</span>
                    )}
                  </td>

                  <td className="p-4 text-slate-400 max-w-xs truncate" title={log.metadata || ''}>
                    {log.metadata || '-'}
                  </td>

                  <td className="p-4 text-right">
                    <button
                      onClick={() => setInspectLog(log)}
                      className="p-1.5 text-slate-400 hover:text-cyan-400 hover:bg-cyan-500/10 rounded-lg transition"
                      title="Inspect Metadata"
                    >
                      <Eye className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}

              {filteredLogs.length === 0 && (
                <tr>
                  <td colSpan={6} className="p-12 text-center text-slate-500 font-sans">
                    <FileText className="w-8 h-8 mx-auto text-slate-600 mb-2" />
                    <div className="text-sm font-semibold text-slate-400">No audit log entries recorded</div>
                    <p className="text-xs text-slate-500 mt-1">Actions performed by administrators will appear here.</p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Inspect Metadata Modal */}
      {inspectLog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-[#0B132B] border border-slate-800 rounded-2xl w-full max-w-lg p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div>
                <h3 className="font-bold text-white text-base">Audit Entry Details</h3>
                <p className="text-xs text-slate-400">{inspectLog.action} on {inspectLog.targetResource}</p>
              </div>
              <button
                onClick={() => setInspectLog(null)}
                className="text-slate-400 hover:text-white p-1 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-2 text-xs">
              <div className="flex justify-between py-1 border-b border-slate-800/60">
                <span className="text-slate-400">Timestamp:</span>
                <span className="text-white font-mono">{new Date(inspectLog.createdAt).toISOString()}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-slate-800/60">
                <span className="text-slate-400">Administrator:</span>
                <span className="text-white">{inspectLog.admin?.email || inspectLog.adminId}</span>
              </div>
              <div className="flex justify-between py-1 border-b border-slate-800/60">
                <span className="text-slate-400">Target ID:</span>
                <span className="text-white font-mono">{inspectLog.targetId || 'None'}</span>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-300 block mb-1">METADATA PAYLOAD</label>
              <pre className="p-3 bg-slate-950 border border-slate-800 rounded-xl text-xs font-mono text-cyan-300 overflow-x-auto max-h-60">
                {inspectLog.metadata
                  ? JSON.stringify(JSON.parse(inspectLog.metadata), null, 2)
                  : 'No extra metadata recorded'}
              </pre>
            </div>

            <div className="pt-2 flex justify-end">
              <button
                onClick={() => setInspectLog(null)}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-300 rounded-xl transition"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
