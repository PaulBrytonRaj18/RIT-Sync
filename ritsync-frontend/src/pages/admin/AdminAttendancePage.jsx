import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CheckSquare, ChevronRight } from 'lucide-react'
import { sessionsApi } from '../../api/sessions'
import PageHeader from '../../components/shared/PageHeader'
import Spinner from '../../components/ui/Spinner'
import EmptyState from '../../components/ui/EmptyState'
import { fmt } from '../../utils/format'
import toast from 'react-hot-toast'

/** Admins see every live session and can open its roster (and override records). */
export default function AdminAttendancePage() {
  const [sessions, setSessions] = useState([])
  const [loading,  setLoading]  = useState(true)

  useEffect(() => {
    sessionsApi.getOpenSessions()
      .then(setSessions)
      .catch(() => toast.error('Could not load sessions'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="animate-fade-in">
      <PageHeader title="Attendance" subtitle="Live sessions across all courses" />
      {loading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : sessions.length === 0 ? (
        <EmptyState icon={CheckSquare} title="No live sessions" description="Sessions appear here while they are open for attendance." />
      ) : (
        <div className="card divide-y divide-gray-50">
          {sessions.map(s => (
            <Link key={s.id} to={`/teacher/attendance/${s.id}`}
              className="flex items-center gap-3 py-3 hover:bg-gray-50 -mx-2 px-2 rounded-lg">
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900 truncate">{s.courseName} · {s.title || 'Class session'}</p>
                <p className="text-xs text-gray-500">{s.instructorName} · {fmt.datetime(s.startTime)}</p>
              </div>
              <span className="badge-green">Live</span>
              <ChevronRight size={16} className="text-gray-300" />
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
