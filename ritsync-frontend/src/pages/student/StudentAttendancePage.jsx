import { useEffect, useState } from 'react'
import useAuthStore from '../../store/authStore'
import { coursesApi } from '../../api/courses'
import { attendanceApi } from '../../api/attendance'
import PageHeader from '../../components/shared/PageHeader'
import AttendanceDonut from '../../components/shared/AttendanceDonut'
import Select from '../../components/ui/Select'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import { fmt } from '../../utils/format'

export default function StudentAttendancePage() {
  const user = useAuthStore(s => s.user)
  const [courses,    setCourses]    = useState([])
  const [selectedId, setSelectedId] = useState('')
  const [stats,      setStats]      = useState(null)
  const [loading,    setLoading]    = useState(true)
  const [statsLoading, setStatsLoading] = useState(false)

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

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  const pct = stats ? (stats.present / Math.max(1, stats.total)) * 100 : 0

  return (
    <div className="animate-fade-in">
      <PageHeader title="My attendance" subtitle="Track your attendance across courses" />

      <div className="mb-6">
        <Select label="Select course" value={selectedId} onChange={e => setSelectedId(e.target.value)}>
          {courses.map(c => <option key={c.id} value={c.id}>{c.name} ({c.code})</option>)}
        </Select>
      </div>

      {statsLoading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : stats ? (
        <div className="space-y-5">
          {/* Summary */}
          <div className="card flex flex-col lg:flex-row items-center gap-6">
            <AttendanceDonut
              data={{ PRESENT: stats.present, ABSENT: stats.absent, LATE: stats.late || 0, EXCUSED: stats.excused || 0 }}
              size={180}
            />
            <div className="flex-1 grid grid-cols-2 gap-4">
              {[
                { label: 'Present',  value: stats.present,  color: 'text-green-600' },
                { label: 'Absent',   value: stats.absent,   color: 'text-red-600'   },
                { label: 'Late',     value: stats.late||0,  color: 'text-amber-600' },
                { label: 'Excused',  value: stats.excused||0, color: 'text-blue-600'},
              ].map(s => (
                <div key={s.label} className="p-3 bg-gray-50 rounded-xl">
                  <p className={`text-2xl font-bold ${s.color}`}>{s.value}</p>
                  <p className="text-xs text-gray-500">{s.label}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Threshold indicator */}
          {pct < 75 && (
            <div className="p-4 bg-red-50 border border-red-100 rounded-2xl">
              <p className="text-sm font-medium text-red-800">⚠ Below minimum attendance threshold</p>
              <p className="text-xs text-red-600 mt-1">
                You need to attend {Math.ceil((0.75 * stats.total - stats.present) / 0.25)} more classes to reach 75%.
              </p>
            </div>
          )}
          {pct >= 75 && pct < 90 && (
            <div className="p-4 bg-amber-50 border border-amber-100 rounded-2xl">
              <p className="text-sm font-medium text-amber-800">Almost there — maintain consistent attendance</p>
            </div>
          )}
        </div>
      ) : (
        <p className="text-center text-gray-500 py-16">No attendance data for this course yet.</p>
      )}
    </div>
  )
}
