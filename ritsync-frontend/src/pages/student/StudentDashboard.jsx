import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BookOpen, CheckSquare, AlertTriangle, TrendingUp, ChevronRight, Clock } from 'lucide-react'
import { PieChart, Pie, Cell, ResponsiveContainer } from 'recharts'
import useAuthStore from '../../store/authStore'
import { coursesApi } from '../../api/courses'
import { attendanceApi } from '../../api/attendance'
import { getCachedCourses } from '../../offline/db'
import useOfflineStore from '../../store/offlineStore'
import PageHeader from '../../components/shared/PageHeader'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import { fmt } from '../../utils/format'

const DONUT_COLORS = { healthy: '#22c55e', atRisk: '#f59e0b', critical: '#ef4444' }

export default function StudentDashboard() {
  const user     = useAuthStore(s => s.user)
  const isOnline = useOfflineStore(s => s.isOnline)
  const [courses, setCourses] = useState([])
  const [stats,   setStats]   = useState({})
  const [records, setRecords] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function load() {
      try {
        const c = isOnline ? await coursesApi.getMyCourses() : await getCachedCourses()
        setCourses(c)

        if (!isOnline) { setLoading(false); return }

        const [statsMap, myRecords] = await Promise.all([
          Promise.all(c.map(async (course) => {
            try {
              return { id: course.id, data: await attendanceApi.getStudentStats(user.id, course.id) }
            } catch { return { id: course.id, data: null } }
          })),
          attendanceApi.getMyRecords().catch(() => []),
        ])

        const grouped = {}
        statsMap.forEach(({ id, data }) => { if (data) grouped[id] = data })
        setStats(grouped)
        setRecords(myRecords.slice(0, 5))
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

  const donutData = courses
    .filter(c => stats[c.id]?.percentage !== undefined)
    .map(c => ({
      name: c.code,
      pct: stats[c.id].percentage,
      color: stats[c.id].percentage >= 80 ? DONUT_COLORS.healthy
           : stats[c.id].percentage >= 65 ? DONUT_COLORS.atRisk
           : DONUT_COLORS.critical,
    }))

  if (loading) return (
    <div className="flex items-center justify-center h-64"><Spinner size="lg" /></div>
  )

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={`Good ${greeting()}, ${user.name.split(' ')[0]}!`}
        subtitle="Here's your attendance overview"
      />

      {/* Attendance Alert Banner */}
      {atRisk.length > 0 && (
        <div className="mb-6 p-4 bg-red-50 border border-red-100 rounded-2xl">
          <div className="flex gap-3">
            <AlertTriangle size={18} className="text-red-500 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-medium text-red-800">Attendance Alert</p>
              <div className="mt-1 space-y-1">
                {atRisk.map(c => (
                  <p key={c.id} className="text-sm text-red-700">
                    {c.name} ({c.code}) — {stats[c.id]?.percentage.toFixed(0)}% — below 75% threshold
                  </p>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Summary stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="stat-card">
          <BookOpen size={18} className="text-primary-500" />
          <p className="stat-value">{courses.length}</p>
          <p className="stat-label">Active courses</p>
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

      {/* Two-column: Donut chart + Recent Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6 mb-8">
        {/* Per-course donut chart */}
        <div className="card lg:col-span-3">
          <h2 className="font-semibold text-gray-900 mb-4">Course breakdown</h2>
          {donutData.length === 0 ? (
            <p className="text-sm text-gray-500 text-center py-6">No attendance data yet</p>
          ) : (
            <div className="flex flex-col sm:flex-row items-center gap-6">
              <div className="relative w-44 h-44 shrink-0">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={donutData} cx="50%" cy="50%" innerRadius="60%" outerRadius="78%"
                         dataKey="pct" paddingAngle={3}>
                      {donutData.map((entry, i) => (
                        <Cell key={i} fill={entry.color} />
                      ))}
                    </Pie>
                  </PieChart>
                </ResponsiveContainer>
                <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                  <span className="text-xl font-bold text-gray-900">{overallPct.toFixed(0)}%</span>
                  <span className="text-[10px] text-gray-400">overall</span>
                </div>
              </div>
              <div className="flex-1 w-full space-y-2">
                {donutData.map((d, i) => (
                  <div key={i} className="flex items-center gap-3">
                    <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: d.color }} />
                    <span className="text-sm text-gray-600 flex-1">{d.name}</span>
                    <span className={`text-xs font-semibold ${
                      d.pct >= 80 ? 'text-green-600' : d.pct >= 65 ? 'text-amber-600' : 'text-red-600'
                    }`}>{d.pct.toFixed(0)}%</span>
                  </div>
                ))}
                <div className="flex items-center gap-3 pt-2 text-xs text-gray-400">
                  <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-green-500" /> Healthy</span>
                  <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-400" /> At risk</span>
                  <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-red-500" /> Critical</span>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Recent Activity */}
        <div className="card lg:col-span-2">
          <div className="flex items-center gap-2 mb-4">
            <Clock size={15} className="text-gray-400" />
            <h2 className="font-semibold text-gray-900">Recent activity</h2>
          </div>
          {records.length === 0 ? (
            <p className="text-sm text-gray-500 text-center py-6">No recent activity</p>
          ) : (
            <div className="space-y-2.5">
              {records.map(r => (
                <div key={r.id} className="flex items-center gap-3 py-2 border-b border-gray-50 last:border-0">
                  <div className="w-8 h-8 rounded-lg bg-gray-100 flex items-center justify-center shrink-0">
                    <span className="text-[10px] font-bold text-gray-600">{r.courseCode?.slice(0, 2)}</span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-medium text-gray-900 truncate">{r.courseName || r.sessionTitle}</p>
                    <p className="text-[10px] text-gray-400">{r.markedAt ? fmt.date(r.markedAt) : ''}</p>
                  </div>
                  <Badge variant={
                    r.status === 'PRESENT' ? 'success' : r.status === 'LATE' ? 'warning' : 'danger'
                  }>{r.status}</Badge>
                </div>
              ))}
            </div>
          )}
          {records.length > 0 && (
            <Link to="/student/attendance" className="block text-center text-xs text-primary-600 hover:underline mt-3">
              View full history
            </Link>
          )}
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
