import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Users, BookOpen, TrendingUp, ShieldCheck, ChevronRight, UserCheck } from 'lucide-react'
import { usersApi } from '../../api/users'
import { adminApi } from '../../api/admin'
import PageHeader from '../../components/shared/PageHeader'
import Spinner from '../../components/ui/Spinner'
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell } from 'recharts'

export default function AdminDashboard() {
  const [users,   setUsers]   = useState({ students: [], teachers: [], admins: [] })
  const [dashboard, setDashboard] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      usersApi.getByRole('STUDENT'),
      usersApi.getByRole('TEACHER'),
      adminApi.getDashboard(),
    ]).then(([students, teachers, dash]) => {
      setUsers({ students, teachers })
      setDashboard(dash)
    }).finally(() => setLoading(false))
  }, [])

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  const deptData = buildDeptData(users.students)

  return (
    <div className="animate-fade-in">
      <PageHeader title="Admin dashboard" subtitle="Institution-wide overview" />

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard icon={Users}      label="Students" value={dashboard?.totalStudents ?? users.students.length} color="text-blue-500" />
        <StatCard icon={UserCheck}  label="Teachers" value={dashboard?.totalTeachers ?? users.teachers.length} color="text-green-500" />
        <StatCard icon={BookOpen}   label="Courses"  value={dashboard?.totalCourses ?? '—'}                     color="text-purple-500" />
        <StatCard icon={TrendingUp} label="Avg attendance" value={dashboard?.avgAttendance != null ? `${dashboard.avgAttendance.toFixed(1)}%` : '—'} color="text-amber-500" />
      </div>

      <div className="grid lg:grid-cols-2 gap-5 mb-5">
        {/* Students by dept */}
        <div className="card">
          <h2 className="font-semibold text-gray-900 mb-4">Students by department</h2>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={deptData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
              <XAxis dataKey="dept" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                {deptData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Quick links */}
        <div className="card">
          <h2 className="font-semibold text-gray-900 mb-4">Quick actions</h2>
          <div className="space-y-2">
            {[
              { to: '/admin/users',      icon: Users,      label: 'Manage users',       desc: `${users.students.length + users.teachers.length} total` },
              { to: '/admin/courses',    icon: BookOpen,   label: 'Manage courses',      desc: 'View all courses' },
              { to: '/admin/analytics',  icon: TrendingUp, label: 'Analytics',           desc: 'Attendance trends' },
              { to: '/admin/audit',      icon: ShieldCheck,label: 'Audit log',           desc: 'System activity' },
            ].map(({ to, icon: Icon, label, desc }) => (
              <Link key={to} to={to}
                className="flex items-center gap-3 p-3 hover:bg-gray-50 rounded-xl transition-colors group">
                <div className="w-9 h-9 bg-primary-50 rounded-xl flex items-center justify-center shrink-0">
                  <Icon size={16} className="text-primary-600" />
                </div>
                <div className="flex-1">
                  <p className="text-sm font-medium text-gray-900">{label}</p>
                  <p className="text-xs text-gray-500">{desc}</p>
                </div>
                <ChevronRight size={14} className="text-gray-300 group-hover:text-gray-500 transition-colors" />
              </Link>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

const COLORS = ['#6366f1','#22c55e','#f59e0b','#ef4444','#3b82f6','#8b5cf6','#14b8a6','#f97316']

function buildDeptData(students) {
  const map = {}
  students.forEach(s => { map[s.department] = (map[s.department] || 0) + 1 })
  return Object.entries(map).map(([dept, count]) => ({ dept, count })).sort((a,b) => b.count - a.count)
}

function StatCard({ icon: Icon, label, value, color }) {
  return (
    <div className="stat-card">
      <Icon size={18} className={color} />
      <p className="stat-value">{value}</p>
      <p className="stat-label">{label}</p>
    </div>
  )
}
