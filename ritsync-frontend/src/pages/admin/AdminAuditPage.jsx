import { useEffect, useState } from 'react'
import { ShieldCheck, Search, ChevronLeft, ChevronRight, Clock } from 'lucide-react'
import { auditApi } from '../../api/audit'
import PageHeader from '../../components/shared/PageHeader'
import Input from '../../components/ui/Input'
import Spinner from '../../components/ui/Spinner'
import EmptyState from '../../components/ui/EmptyState'
import Badge from '../../components/ui/Badge'
import { fmt } from '../../utils/format'

export default function AdminAuditPage() {
  const [logs, setLogs] = useState([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [search, setSearch] = useState('')

  const load = async (p) => {
    setLoading(true)
    try {
      const data = await auditApi.getAuditLogs(p, 50)
      setLogs(data.content || [])
      setTotalPages(data.totalPages || 0)
      setTotalElements(data.totalElements || 0)
      setPage(data.number || 0)
    } catch {
      setLogs([])
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load(0) }, [])

  const filtered = logs.filter(l =>
    l.action?.toLowerCase().includes(search.toLowerCase()) ||
    l.actorEmail?.toLowerCase().includes(search.toLowerCase()) ||
    l.targetTable?.toLowerCase().includes(search.toLowerCase())
  )

  const actionColor = (action) => {
    if (action?.includes('ATTENDANCE_MARKED')) return 'green'
    if (action?.includes('MANUAL_OVERRIDE') || action?.includes('FAIL')) return 'red'
    if (action?.includes('SESSION_OPENED') || action?.includes('SESSION_CREATED')) return 'blue'
    if (action?.includes('SESSION_CLOSED')) return 'yellow'
    if (action?.includes('LEAVE')) return 'purple'
    return 'gray'
  }

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Audit Log"
        subtitle={`${totalElements} total events — complete system activity trail`}
      />

      <div className="mb-4">
        <div className="relative">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input className="input pl-9" placeholder="Search by action, email, table…"
            value={search} onChange={e => setSearch(e.target.value)} />
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : filtered.length === 0 ? (
        <EmptyState icon={ShieldCheck} title="No audit logs found"
          description={search ? 'Try a different search term.' : 'No system activity recorded yet.'} />
      ) : (
        <>
          <div className="card overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100 bg-gray-50">
                    <th className="text-left py-3 px-4 text-xs font-medium text-gray-500 uppercase tracking-wider">Time</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-gray-500 uppercase tracking-wider">Action</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-gray-500 uppercase tracking-wider">Actor</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-gray-500 uppercase tracking-wider">Target</th>
                    <th className="text-left py-3 px-4 text-xs font-medium text-gray-500 uppercase tracking-wider">Details</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {filtered.map(log => (
                    <tr key={log.id} className="hover:bg-gray-50 transition-colors">
                      <td className="py-3 px-4 whitespace-nowrap">
                        <div className="flex items-center gap-1.5 text-xs text-gray-500">
                          <Clock size={12} />
                          {log.createdAt ? fmt.datetime(log.createdAt) : '—'}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <Badge variant={actionColor(log.action)}>{log.action}</Badge>
                      </td>
                      <td className="py-3 px-4">
                        <span className="text-xs text-gray-700 font-mono">{log.actorEmail || '—'}</span>
                        {log.actorId && <span className="text-xs text-gray-400 ml-1">(#{log.actorId})</span>}
                      </td>
                      <td className="py-3 px-4">
                        <span className="text-xs text-gray-600">
                          {log.targetTable || '—'}
                          {log.targetId ? ` #${log.targetId}` : ''}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        {log.payload ? (
                          <span className="text-xs text-gray-500 font-mono max-w-[200px] truncate block"
                            title={JSON.stringify(log.payload)}>
                            {JSON.stringify(log.payload).slice(0, 60)}…
                          </span>
                        ) : <span className="text-xs text-gray-400">—</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between mt-4 text-sm">
              <span className="text-gray-500">
                Page {page + 1} of {totalPages}
              </span>
              <div className="flex gap-2">
                <button disabled={page === 0} onClick={() => load(page - 1)}
                  className="p-2 rounded-lg border border-gray-200 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed">
                  <ChevronLeft size={16} />
                </button>
                <button disabled={page >= totalPages - 1} onClick={() => load(page + 1)}
                  className="p-2 rounded-lg border border-gray-200 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed">
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
