import { useEffect, useState } from 'react'
import { BarChart2, TrendingUp, Users, BookOpen } from 'lucide-react'
import { adminApi } from '../../api/admin'
import PageHeader from '../../components/shared/PageHeader'
import Spinner from '../../components/ui/Spinner'
import EmptyState from '../../components/ui/EmptyState'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, Cell
} from 'recharts'

export default function AdminAnalyticsPage() {
  const [dailyData, setDailyData] = useState([])
  const [deptData, setDeptData] = useState([])
  const [dashboard, setDashboard] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    Promise.all([
      adminApi.getDashboard(),
      adminApi.getDailyAnalytics(),
      adminApi.getDepartmentTrends(),
    ]).then(([dash, daily, dept]) => {
      setDashboard(dash)
      setDailyData(daily || [])
      setDeptData(dept || [])
    }).catch(e => {
      setError('Failed to load analytics data')
    }).finally(() => setLoading(false))
  }, [])

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>
  if (error) return (
    <div className="animate-fade-in">
      <PageHeader title="Analytics" subtitle="Department-wide attendance trends and insights" />
      <div className="card text-center py-16 text-gray-500">{error}</div>
    </div>
  )

  return (
    <div className="animate-fade-in">
      <PageHeader title="Analytics" subtitle="Department-wide attendance trends and insights" />

      {dashboard && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          <div className="stat-card">
            <Users size={18} className="text-blue-500" />
            <p className="stat-value">{dashboard.totalStudents}</p>
            <p className="stat-label">Students</p>
          </div>
          <div className="stat-card">
            <BookOpen size={18} className="text-primary-500" />
            <p className="stat-value">{dashboard.totalCourses}</p>
            <p className="stat-label">Courses</p>
          </div>
          <div className="stat-card">
            <TrendingUp size={18} className="text-green-500" />
            <p className="stat-value">{dashboard.avgAttendance?.toFixed(1) || '0'}%</p>
            <p className="stat-label">Avg attendance</p>
          </div>
          <div className="stat-card">
            <Users size={18} className="text-purple-500" />
            <p className="stat-value">{dashboard.totalTeachers}</p>
            <p className="stat-label">Teachers</p>
          </div>
        </div>
      )}

      <div className="grid gap-5">
        {/* Weekly attendance */}
        <div className="card">
          <h2 className="font-semibold text-gray-900 mb-1">Weekly attendance rate</h2>
          <p className="text-xs text-gray-500 mb-4">Institution average this week</p>
          {dailyData.length === 0 ? (
            <p className="text-center text-sm text-gray-400 py-10">No attendance data this week</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={dailyData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="day" tick={{ fontSize: 12 }} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 12 }} unit="%" />
                <Tooltip formatter={v => [v + '%', 'Attendance']} />
                <Bar dataKey="attendance" radius={[4,4,0,0]}>
                  {dailyData.map((d, i) => <Cell key={i} fill={d.attendance >= 85 ? '#22c55e' : d.attendance >= 75 ? '#f59e0b' : '#ef4444'} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* Department trends */}
        <div className="card">
          <h2 className="font-semibold text-gray-900 mb-1">Department attendance</h2>
          <p className="text-xs text-gray-500 mb-4">Current attendance rate by department</p>
          {deptData.length === 0 ? (
            <p className="text-center text-sm text-gray-400 py-10">No department data available</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={deptData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="department" tick={{ fontSize: 12 }} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 12 }} unit="%" />
                <Tooltip formatter={v => [v + '%', 'Attendance']} />
                <Bar dataKey="attendance" radius={[4,4,0,0]}>
                  {deptData.map((d, i) => <Cell key={i} fill={d.attendance >= 85 ? '#22c55e' : d.attendance >= 75 ? '#f59e0b' : '#ef4444'} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>
    </div>
  )
}
