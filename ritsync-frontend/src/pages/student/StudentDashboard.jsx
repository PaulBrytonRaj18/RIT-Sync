import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BookOpen, CheckSquare, AlertTriangle, TrendingUp, ChevronRight } from 'lucide-react'
import useAuthStore from '../../store/authStore'
import { coursesApi } from '../../api/courses'
import { attendanceApi } from '../../api/attendance'
import { getCachedCourses } from '../../offline/db'
import useOfflineStore from '../../store/offlineStore'
import PageHeader from '../../components/shared/PageHeader'
import AttendanceDonut from '../../components/shared/AttendanceDonut'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import { fmt, attendanceBadgeClass } from '../../utils/format'

export default function StudentDashboard() {
  const user    = useAuthStore(s => s.user)
  const isOnline = useOfflineStore(s => s.isOnline)
  const [courses,  setCourses]  = useState([])
  const [stats,    setStats]    = useState({})
  const [loading,  setLoading]  = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const c = isOnline ? await coursesApi.getMyCourses() : await getCachedCourses()
        setCourses(c)
        const statsMap = {}
        if (isOnline) {
          await Promise.all(
            c.map(async (course) => {
              try {
                statsMap[course.id] = await attendanceApi.getStudentStats(user.id, course.id)
              } catch {}
            })
          )
        }
        setStats(statsMap)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [user.id, isOnline])

  const overallPct = Object.values(stats).length
    ? (Object.values(stats).reduce((s, st) => s + (st?.percentage || 0), 0) / Object.values(stats).length)
    : 0

  const atRisk = courses.filter(c => {
    const pct = stats[c.id]?.percentage
    return pct !== undefined && pct < 75
  })

  if (loading) return (
    <div className="flex items-center justify-center h-64"><Spinner size="lg" /></div>
  )

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={`Good ${greeting()}, ${user.name.split(' ')[0]} 👋`}
        subtitle="Here's your attendance overview"
      />

      {/* Alert for at-risk courses */}
      {atRisk.length > 0 && (
        <div className="mb-6 p-4 bg-red-50 border border-red-100 rounded-2xl flex gap-3">
          <AlertTriangle size={18} className="text-red-500 shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-medium text-red-800">Attendance alert</p>
            <p className="text-sm text-red-700 mt-0.5">
              {atRisk.map(c => c.name).join(', ')} — below 75% threshold
            </p>
          </div>
        </div>
      )}

      {/* Summary stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="stat-card">
          <BookOpen size={18} className="text-primary-500" />
          <p className="stat-value">{courses.length}</p>
          <p className="stat-label">Enrolled courses</p>
        </div>
        <div className="stat-card">
          <TrendingUp size={18} className="text-green-500" />
          <p className="stat-value">{overallPct.toFixed(0)}%</p>
          <p className="stat-label">Overall attendance</p>
        </div>
        <div className="stat-card">
          <CheckSquare size={18} className="text-blue-500" />
          <p className="stat-value">{Object.values(stats).reduce((s, st) => s + (st?.present || 0), 0)}</p>
          <p className="stat-label">Classes attended</p>
        </div>
        <div className="stat-card">
          <AlertTriangle size={18} className="text-red-400" />
          <p className="stat-value">{atRisk.length}</p>
          <p className="stat-label">At-risk courses</p>
        </div>
      </div>

      {/* Courses with attendance */}
      <div className="card">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-gray-900">Course attendance</h2>
          <Link to="/student/courses" className="text-sm text-primary-600 hover:underline">View all</Link>
        </div>

        {courses.length === 0 ? (
          <p className="text-sm text-gray-500 text-center py-8">No courses enrolled yet</p>
        ) : (
          <div className="divide-y divide-gray-50">
            {courses.map(course => {
              const st  = stats[course.id]
              const pct = st?.percentage ?? null
              return (
                <Link key={course.id} to={`/student/courses/${course.id}`}
                  className="flex items-center gap-4 py-3.5 hover:bg-gray-50 -mx-5 px-5 transition-colors">
                  <div className="w-10 h-10 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
                    <span className="text-xs font-bold text-primary-700">{course.code.slice(0, 3)}</span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{course.name}</p>
                    <p className="text-xs text-gray-500">{course.code} · {course.department}</p>
                  </div>
                  {pct !== null ? (
                    <div className="flex items-center gap-2">
                      <AttendanceBar pct={pct} />
                      <span className={`text-xs font-semibold w-10 text-right ${pct >= 75 ? 'text-green-600' : 'text-red-600'}`}>
                        {pct.toFixed(0)}%
                      </span>
                    </div>
                  ) : <span className="text-xs text-gray-400">No data</span>}
                  <ChevronRight size={14} className="text-gray-300 shrink-0" />
                </Link>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}

function AttendanceBar({ pct }) {
  const color = pct >= 90 ? 'bg-green-500' : pct >= 75 ? 'bg-amber-400' : 'bg-red-500'
  return (
    <div className="w-20 h-1.5 bg-gray-100 rounded-full overflow-hidden">
      <div className={`h-full rounded-full ${color}`} style={{ width: `${Math.min(100, pct)}%` }} />
    </div>
  )
}

function greeting() {
  const h = new Date().getHours()
  if (h < 12) return 'morning'
  if (h < 17) return 'afternoon'
  return 'evening'
}
