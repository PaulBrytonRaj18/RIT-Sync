import { useEffect, useState, useMemo } from 'react'
import useAuthStore from '../../store/authStore'
import { coursesApi } from '../../api/courses'
import { attendanceApi } from '../../api/attendance'
import PageHeader from '../../components/shared/PageHeader'
import AttendanceDonut from '../../components/shared/AttendanceDonut'
import Select from '../../components/ui/Select'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import EmptyState from '../../components/ui/EmptyState'
import { CheckSquare } from 'lucide-react'
import { fmt } from '../../utils/format'

const STATUS_FILTERS = ['ALL', 'PRESENT', 'ABSENT', 'LATE', 'EXCUSED']

export default function StudentAttendancePage() {
  const user = useAuthStore(s => s.user)
  const [courses, setCourses] = useState([])
  const [selectedId, setSelectedId] = useState('')
  const [stats, setStats] = useState(null)
  const [records, setRecords] = useState([])
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [loading, setLoading] = useState(true)
  const [statsLoading, setStatsLoading] = useState(false)
  const [recordsLoading, setRecordsLoading] = useState(true)

  useEffect(() => {
    coursesApi.getMyCourses().then(c => {
      setCourses(c)
      if (c.length > 0) setSelectedId(String(c[0].id))
      setLoading(false)
    })
  }, [])

  useEffect(() => {
    if (!selectedId) return
    setStatsLoading(true)
    attendanceApi.getStudentStats(user.id, selectedId)
      .then(setStats)
      .catch(() => setStats(null))
      .finally(() => setStatsLoading(false))
  }, [selectedId, user.id])

  useEffect(() => {
    setRecordsLoading(true)
    attendanceApi.getMyRecords()
      .then(setRecords)
      .catch(() => setRecords([]))
      .finally(() => setRecordsLoading(false))
  }, [])

  const filteredRecords = useMemo(() => {
    let list = records
    if (selectedId) {
      const selectedCourseId = Number(selectedId)
      const selectedCourse = courses.find(c => c.id === selectedCourseId)
      list = list.filter(r =>
        r.courseId === selectedCourseId ||
        r.courseCode === selectedCourse?.code
      )
    }
    if (statusFilter !== 'ALL') {
      list = list.filter(r => r.status === statusFilter)
    }
    return list.sort((a, b) => new Date(b.markedAt || b.createdAt) - new Date(a.markedAt || a.createdAt))
  }, [records, selectedId, statusFilter, courses])

  const pct = stats ? (stats.present / Math.max(1, stats.total)) * 100 : 0

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  return (
    <div className="animate-fade-in">
      <PageHeader title="Attendance history" subtitle="Complete audit trail of your attendance records" />

      {/* Filters row */}
      <div className="flex flex-col sm:flex-row gap-4 mb-6">
        <div className="flex-1">
          <Select label="Filter by course" value={selectedId} onChange={e => setSelectedId(e.target.value)}>
            <option value="">All courses</option>
            {courses.map(c => <option key={c.id} value={c.id}>{c.name} ({c.code})</option>)}
          </Select>
        </div>
        <div className="w-full sm:w-44">
          <label className="label">Filter by status</label>
          <div className="flex gap-1.5 mt-1">
            {STATUS_FILTERS.map(s => (
              <button key={s} onClick={() => setStatusFilter(s)}
                className={`px-3 py-1.5 text-xs font-medium rounded-lg transition-colors ${
                  statusFilter === s
                    ? s === 'ALL' ? 'bg-gray-900 text-white'
                      : s === 'PRESENT' ? 'bg-green-600 text-white'
                      : s === 'ABSENT' ? 'bg-red-600 text-white'
                      : s === 'LATE' ? 'bg-amber-500 text-white'
                      : 'bg-blue-600 text-white'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}>
                {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Stats summary for selected course */}
      {statsLoading ? (
        <div className="flex justify-center py-10"><Spinner size="lg" /></div>
      ) : stats ? (
        <div className="card flex flex-col lg:flex-row items-center gap-6 mb-6">
          <AttendanceDonut
            data={{ PRESENT: stats.present, ABSENT: stats.absent, LATE: stats.late || 0, EXCUSED: stats.excused || 0 }}
            size={160}
          />
          <div className="flex-1 grid grid-cols-2 sm:grid-cols-4 gap-3 w-full">
            {[
              { label: 'Present', value: stats.present, color: 'text-green-600' },
              { label: 'Absent',  value: stats.absent,  color: 'text-red-600' },
              { label: 'Late',    value: stats.late||0, color: 'text-amber-600' },
              { label: 'Excused', value: stats.excused||0, color: 'text-blue-600' },
            ].map(s => (
              <div key={s.label} className="p-3 bg-gray-50 rounded-xl text-center sm:text-left">
                <p className={`text-xl font-bold ${s.color}`}>{s.value}</p>
                <p className="text-xs text-gray-500">{s.label}</p>
              </div>
            ))}
          </div>
          {pct < 75 && (
            <div className="w-full lg:w-auto lg:min-w-[180px] p-3 bg-red-50 border border-red-100 rounded-xl">
              <p className="text-xs font-medium text-red-800">Below threshold</p>
              <p className="text-xs text-red-600 mt-0.5">
                Need {Math.ceil((0.75 * stats.total - stats.present) / 0.25)} more classes
              </p>
            </div>
          )}
        </div>
      ) : null}

      {/* Records table */}
      <div className="card">
        <h3 className="font-semibold text-gray-900 mb-4">
          Session records
          {filteredRecords.length > 0 && <span className="text-sm font-normal text-gray-400 ml-2">({filteredRecords.length})</span>}
        </h3>

        {recordsLoading ? (
          <div className="flex justify-center py-16"><Spinner size="lg" /></div>
        ) : filteredRecords.length === 0 ? (
          <EmptyState icon={CheckSquare} title="No records found"
            description={statusFilter !== 'ALL' ? 'Try changing the status filter.' : 'No attendance records yet for this course.'} />
        ) : (
          <div className="overflow-x-auto -mx-5">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100">
                  <th className="text-left py-3 px-5 text-xs font-medium text-gray-400 uppercase tracking-wider">Date</th>
                  <th className="text-left py-3 px-5 text-xs font-medium text-gray-400 uppercase tracking-wider">Course</th>
                  <th className="text-left py-3 px-5 text-xs font-medium text-gray-400 uppercase tracking-wider">Session</th>
                  <th className="text-left py-3 px-5 text-xs font-medium text-gray-400 uppercase tracking-wider">Status</th>
                  <th className="text-left py-3 px-5 text-xs font-medium text-gray-400 uppercase tracking-wider">Verification</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {filteredRecords.map(r => (
                  <tr key={r.id} className="hover:bg-gray-50 transition-colors">
                    <td className="py-3.5 px-5 text-gray-900 whitespace-nowrap">
                      {r.markedAt ? fmt.date(r.markedAt) : '-'}
                    </td>
                    <td className="py-3.5 px-5">
                      <span className="text-gray-900">{r.courseName || '-'}</span>
                      <span className="text-xs text-gray-400 ml-1.5">{r.courseCode}</span>
                    </td>
                    <td className="py-3.5 px-5 text-gray-600 max-w-[140px] truncate">
                      {r.sessionTitle || 'Session'}
                    </td>
                    <td className="py-3.5 px-5">
                      <Badge variant={
                        r.status === 'PRESENT' ? 'success'
                        : r.status === 'LATE' ? 'warning'
                        : r.status === 'EXCUSED' ? 'info'
                        : 'danger'
                      }>{r.status}</Badge>
                    </td>
                    <td className="py-3.5 px-5">
                      <span className="text-xs text-gray-500 font-mono">
                        {r.verificationMethod || '—'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
