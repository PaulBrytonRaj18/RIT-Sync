import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Users, AlertTriangle, TrendingUp, BookOpen, ChevronRight, Percent, BarChart2 } from 'lucide-react'
import { PieChart, Pie, Cell, ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid } from 'recharts'
import { teacherApi } from '../../api/teacher'
import useAuthStore from '../../store/authStore'
import PageHeader from '../../components/shared/PageHeader'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import { fmt } from '../../utils/format'

const PIE_COLORS = { healthy: '#22c55e', atRisk: '#f59e0b', critical: '#ef4444' }

export default function ClassTeacherDashboard() {
  const user = useAuthStore(s => s.user)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    teacherApi.getClassDashboard()
      .then(setData)
      .finally(() => setLoading(false))
  }, [user.id])

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>
  if (!data) return null

  const donutData = [
    { name: 'Healthy (≥75%)', value: data.totalStudents - data.defaulterCount,
      color: data.totalStudents > 0 ? PIE_COLORS.healthy : '#e5e7eb' },
    { name: 'Defaulters (<75%)', value: data.defaulterCount,
      color: data.defaulterCount > 0 ? PIE_COLORS.critical : '#e5e7eb' },
  ]

  const barData = data.courseSummaries?.map(c => ({
    name: c.courseCode,
    attendance: Math.round(c.avgAttendancePct),
    students: c.totalStudents,
  })) || []

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={`Class Overview, ${user.name.split(' ')[0]}`}
        subtitle="Monitor your class attendance across all subjects"
      />

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4 mb-8">
        <div className="stat-card">
          <Users size={18} className="text-blue-500" />
          <p className="stat-value">{data.totalStudents}</p>
          <p className="stat-label">Students</p>
        </div>
        <div className="stat-card">
          <TrendingUp size={18} className="text-green-500" />
          <p className="stat-value">{data.overallAttendancePct.toFixed(0)}%</p>
          <p className="stat-label">Overall attendance</p>
        </div>
        <div className="stat-card">
          <BookOpen size={18} className="text-primary-500" />
          <p className="stat-value">{data.courses}</p>
          <p className="stat-label">Courses</p>
        </div>
        <div className="stat-card">
          <AlertTriangle size={18} className="text-amber-500" />
          <p className="stat-value">{data.todayAbsentees}</p>
          <p className="stat-label">Today absent</p>
        </div>
        <div className="stat-card">
          <AlertTriangle size={18} className="text-red-500" />
          <p className="stat-value text-red-600">{data.defaulterCount}</p>
          <p className="stat-label">Defaulters (&lt;75%)</p>
        </div>
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6 mb-8">
        {/* Donut - student health */}
        <div className="card lg:col-span-2">
          <h2 className="font-semibold text-gray-900 mb-4">Class health</h2>
          <div className="flex items-center gap-4">
            <div className="relative w-36 h-36 shrink-0">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={donutData} cx="50%" cy="50%" innerRadius="60%" outerRadius="78%"
                       dataKey="value" paddingAngle={3}>
                    {donutData.map((entry, i) => (
                      <Cell key={i} fill={entry.color} />
                    ))}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span className="text-lg font-bold text-gray-900">{data.totalStudents}</span>
                <span className="text-[10px] text-gray-400">students</span>
              </div>
            </div>
            <div className="space-y-2 text-xs">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-green-500" />
                <span className="text-gray-600">Healthy</span>
                <span className="font-medium ml-auto">{data.totalStudents - data.defaulterCount}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-red-500" />
                <span className="text-gray-600">Defaulters</span>
                <span className="font-medium ml-auto">{data.defaulterCount}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Course attendance bar chart */}
        <div className="card lg:col-span-3">
          <h2 className="font-semibold text-gray-900 mb-4">Course attendance</h2>
          {barData.length === 0 ? (
            <p className="text-sm text-gray-500 text-center py-6">No data</p>
          ) : (
            <ResponsiveContainer width="100%" height={180}>
              <BarChart data={barData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f3f4f6" />
                <XAxis dataKey="name" tick={{ fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 11 }} unit="%" axisLine={false} tickLine={false} />
                <Tooltip formatter={(v) => `${v}%`} />
                <Bar dataKey="attendance" radius={[6, 6, 0, 0]}>
                  {barData.map((entry, i) => (
                    <Cell key={i} fill={entry.attendance >= 75 ? '#22c55e' : '#ef4444'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Course summaries */}
      <div className="card mb-8">
        <h2 className="font-semibold text-gray-900 mb-4">Course-wise attendance</h2>
        {data.courseSummaries?.length === 0 ? (
          <p className="text-sm text-gray-500 text-center py-6">No courses</p>
        ) : (
          <div className="divide-y divide-gray-50">
            {data.courseSummaries.map(c => (
              <Link key={c.courseId} to={`/teacher/subject-attendance/${c.courseId}`}
                className="flex items-center gap-4 py-3.5 hover:bg-gray-50 -mx-5 px-5 transition-colors">
                <div className="w-9 h-9 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
                  <span className="text-xs font-bold text-primary-700">{c.courseCode?.slice(0, 3)}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">{c.courseName}</p>
                  <p className="text-xs text-gray-500">{c.totalStudents} students</p>
                </div>
                <div className="text-right">
                  <p className={`text-sm font-semibold ${c.avgAttendancePct >= 75 ? 'text-green-600' : 'text-red-500'}`}>
                    {c.avgAttendancePct.toFixed(0)}%
                  </p>
                </div>
                <ChevronRight size={14} className="text-gray-300 shrink-0" />
              </Link>
            ))}
          </div>
        )}
      </div>

      {/* Defaulter list preview */}
      {data.defaulters?.length > 0 && (
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold text-gray-900 flex items-center gap-2">
              <AlertTriangle size={16} className="text-red-500" />
              Defaulters ({data.defaulterCount})
            </h2>
            <Link to="/teacher/defaulters" className="text-sm text-primary-600 hover:underline">View all</Link>
          </div>
          <div className="divide-y divide-gray-50">
            {data.defaulters.slice(0, 5).map(d => (
              <div key={d.studentId} className="flex items-center gap-3 py-2.5">
                <div className="w-7 h-7 rounded-full bg-red-50 flex items-center justify-center text-red-600 text-xs font-semibold shrink-0">
                  {d.studentName[0]}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">{d.studentName}</p>
                  <p className="text-xs text-gray-400">{d.rollNumber || '—'}</p>
                </div>
                <span className="text-sm font-semibold text-red-500">{d.overallPct.toFixed(0)}%</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
