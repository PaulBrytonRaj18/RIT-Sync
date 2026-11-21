import { useEffect, useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { BookOpen, Users, CalendarCheck, BarChart2, ChevronRight, Plus } from 'lucide-react'
import useAuthStore from '../../store/authStore'
import RoleSwitcher from '../../components/layout/RoleSwitcher'
import { coursesApi } from '../../api/courses'
import { teacherApi } from '../../api/teacher'
import { sessionsApi } from '../../api/sessions'
import PageHeader from '../../components/shared/PageHeader'
import Button from '../../components/ui/Button'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import { fmt } from '../../utils/format'

export default function TeacherDashboard() {
  const user = useAuthStore(s => s.user)
  const teacherRole = useAuthStore(s => s.teacherRole)
  const [courses,      setCourses]      = useState([])
  const [openSessions, setOpenSessions] = useState([])
  const [classData,    setClassData]    = useState(null)
  const [loading,      setLoading]      = useState(true)

  const redirect = teacherRole === 'subject_teacher' ? '/teacher/subject'
                 : teacherRole === 'class_teacher'   ? '/teacher/class'
                 : null

  useEffect(() => {
    if (redirect) return
    Promise.all([
      coursesApi.getMyCourses(),
      sessionsApi.getOpenSessions(),
      teacherApi.getClassDashboard().catch(() => null),
    ]).then(([c, s, cd]) => {
      setCourses(c)
      // The API already returns only this teacher's open sessions (all of them for admins)
      setOpenSessions(s)
      setClassData(cd)
    }).finally(() => setLoading(false))
  }, [redirect])

  // Hooks must run unconditionally; redirect only after them (rules of hooks)
  if (redirect) return <Navigate to={redirect} replace />
  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  const totalStudents = courses.reduce((s, c) => s + (c.enrolledCount || 0), 0)

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={`Welcome, ${user.name.split(' ')[0]}`}
        subtitle="Manage your courses and attendance sessions"
        action={
          <Link to="/teacher/sessions/new">
            <Button variant="primary" size="sm"><Plus size={15} /> New session</Button>
          </Link>
        }
      />

      <RoleSwitcher />

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="stat-card"><BookOpen size={18} className="text-primary-500" />
          <p className="stat-value">{courses.length}</p><p className="stat-label">Courses</p></div>
        <div className="stat-card"><Users size={18} className="text-blue-500" />
          <p className="stat-value">{totalStudents}</p><p className="stat-label">Students</p></div>
        <div className="stat-card"><CalendarCheck size={18} className="text-green-500" />
          <p className="stat-value">{openSessions.length}</p><p className="stat-label">Live sessions</p></div>
        <div className="stat-card">
          <Link to="/teacher/class" className="block">
            <BarChart2 size={18} className="text-amber-500" />
            <p className="stat-value">{classData ? classData.overallAttendancePct.toFixed(0) + '%' : '—'}</p>
            <p className="stat-label">Class attendance</p>
          </Link>
        </div>
      </div>

      {/* Live sessions */}
      {openSessions.length > 0 && (
        <div className="card mb-5">
          <h2 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
            <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse inline-block" />
            Live sessions
          </h2>
          <div className="space-y-2">
            {openSessions.map(s => (
              <Link key={s.id} to={`/teacher/sessions/${s.id}`}
                className="flex items-center gap-3 p-3 bg-green-50 border border-green-100 rounded-xl hover:bg-green-100 transition-colors">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">{s.courseName}</p>
                  <p className="text-xs text-gray-500">{s.title || 'Session'} · Started {fmt.relative(s.createdAt)}</p>
                </div>
                <Badge variant="green">Live</Badge>
                <ChevronRight size={14} className="text-gray-400" />
              </Link>
            ))}
          </div>
        </div>
      )}

      {/* Courses */}
      <div className="card">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-gray-900">Your courses</h2>
          <Link to="/teacher/courses" className="text-sm text-primary-600 hover:underline">View all</Link>
        </div>
        {courses.length === 0 ? (
          <p className="text-sm text-gray-500 text-center py-8">No courses assigned yet</p>
        ) : (
          <div className="divide-y divide-gray-50">
            {courses.slice(0, 5).map(course => (
              <Link key={course.id} to={`/teacher/subject-attendance/${course.id}`}
                className="flex items-center gap-3 py-3.5 hover:bg-gray-50 -mx-5 px-5 transition-colors">
                <div className="w-9 h-9 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
                  <span className="text-xs font-bold text-primary-700">{course.code.slice(0,3)}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{course.name}</p>
                  <p className="text-xs text-gray-500">{course.code} · {course.department} · {course.enrolledCount} students</p>
                </div>
                <ChevronRight size={14} className="text-gray-300" />
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
