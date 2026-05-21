import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BookOpen, Users, CalendarCheck, Clock, AlertCircle, ChevronRight, ListChecks } from 'lucide-react'
import { teacherApi } from '../../api/teacher'
import { coursesApi } from '../../api/courses'
import useAuthStore from '../../store/authStore'
import PageHeader from '../../components/shared/PageHeader'
import Button from '../../components/ui/Button'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import EmptyState from '../../components/ui/EmptyState'
import { fmt } from '../../utils/format'

export default function SubjectTeacherDashboard() {
  const user = useAuthStore(s => s.user)
  const [data, setData] = useState(null)
  const [courses, setCourses] = useState([])
  const [selectedCourse, setSelectedCourse] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      teacherApi.getDashboard(),
      coursesApi.getMyCourses(),
    ]).then(([d, c]) => {
      setData(d)
      setCourses(c)
      if (c.length > 0) setSelectedCourse(String(c[0].id))
    }).finally(() => setLoading(false))
  }, [user.id])

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>
  if (!data) return null

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={`Welcome, ${user.name.split(' ')[0]}`}
        subtitle="Subject Teacher Dashboard"
        action={
          <Link to="/teacher/sessions/new">
            <Button variant="primary" size="sm"><CalendarCheck size={15} /> New session</Button>
          </Link>
        }
      />

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="stat-card">
          <BookOpen size={18} className="text-primary-500" />
          <p className="stat-value">{data.courses?.length || 0}</p>
          <p className="stat-label">Courses</p>
        </div>
        <div className="stat-card">
          <Users size={18} className="text-blue-500" />
          <p className="stat-value">{data.totalStudents || 0}</p>
          <p className="stat-label">Total students</p>
        </div>
        <div className="stat-card">
          <CalendarCheck size={18} className="text-green-500" />
          <p className="stat-value">{data.liveSessions || 0}</p>
          <p className="stat-label">Live sessions</p>
        </div>
        <div className="stat-card">
          <Clock size={18} className="text-amber-500" />
          <p className="stat-value">{data.todaySessions?.length || 0}</p>
          <p className="stat-label">Today's classes</p>
        </div>
      </div>

      {/* Class Switcher */}
      {courses.length > 1 && (
        <div className="card mb-5">
          <label className="label">Switch course</label>
          <select
            className="input"
            value={selectedCourse}
            onChange={e => setSelectedCourse(e.target.value)}
          >
            {courses.map(c => (
              <option key={c.id} value={c.id}>{c.name} ({c.code})</option>
            ))}
          </select>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Today's Classes */}
        <div className="card">
          <h2 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
            <Clock size={16} className="text-primary-500" />
            Today's classes
          </h2>
          {data.todaySessions?.length === 0 ? (
            <p className="text-sm text-gray-500 text-center py-6">No classes scheduled today</p>
          ) : (
            <div className="space-y-2">
              {data.todaySessions.map(s => (
                <Link key={s.id} to={`/teacher/attendance/${s.id}`}
                  className="flex items-center gap-3 p-3 bg-gray-50 rounded-xl hover:bg-primary-50 transition-colors">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900">{s.courseName}</p>
                    <p className="text-xs text-gray-500">
                      {s.title || 'Session'} · {fmt.time(s.startTime)} – {fmt.time(s.endTime)}
                    </p>
                  </div>
                  {s.isOpen && <Badge variant="green">Live</Badge>}
                  <ChevronRight size={14} className="text-gray-400 shrink-0" />
                </Link>
              ))}
            </div>
          )}
        </div>

        {/* Pending Attendance */}
        <div className="card">
          <h2 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
            <AlertCircle size={16} className="text-amber-500" />
            Pending attendance
          </h2>
          {data.pendingAttendance?.length === 0 ? (
            <p className="text-sm text-gray-500 text-center py-6">All caught up!</p>
          ) : (
            <div className="space-y-2">
              {data.pendingAttendance.map(s => (
                <Link key={s.id} to={`/teacher/attendance/${s.id}`}
                  className="flex items-center gap-3 p-3 bg-amber-50 border border-amber-100 rounded-xl hover:bg-amber-100 transition-colors">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900">{s.courseName}</p>
                    <p className="text-xs text-gray-500">
                      {s.title || 'Session'} · {fmt.time(s.startTime)}
                    </p>
                  </div>
                  <Badge variant="yellow">Pending</Badge>
                  <ChevronRight size={14} className="text-gray-400 shrink-0" />
                </Link>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Handled Sections */}
      <div className="card mb-8">
        <h2 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
          <ListChecks size={16} className="text-primary-500" />
          Handled sections
        </h2>
        {data.handledSections?.length === 0 ? (
          <p className="text-sm text-gray-500 text-center py-6">No sections assigned</p>
        ) : (
          <div className="divide-y divide-gray-50">
            {data.handledSections.map(s => (
              <Link key={s.courseId} to={`/teacher/subject-attendance/${s.courseId}`}
                className="flex items-center gap-4 py-3 hover:bg-gray-50 -mx-5 px-5 transition-colors">
                <div className="w-9 h-9 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
                  <span className="text-xs font-bold text-primary-700">{s.courseCode?.slice(0, 3)}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">{s.courseName}</p>
                  <p className="text-xs text-gray-500">{s.section} · {s.studentCount} students</p>
                </div>
                <ChevronRight size={14} className="text-gray-300 shrink-0" />
              </Link>
            ))}
          </div>
        )}
      </div>

      {/* All Courses */}
      <div className="card">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-semibold text-gray-900">Your courses</h2>
          <Link to="/teacher/courses" className="text-sm text-primary-600 hover:underline">View all</Link>
        </div>
        {data.courses?.length === 0 ? (
          <p className="text-sm text-gray-500 text-center py-8">No courses assigned yet</p>
        ) : (
          <div className="divide-y divide-gray-50">
            {data.courses.slice(0, 5).map(course => (
              <Link key={course.id} to={`/teacher/subject-attendance/${course.id}`}
                className="flex items-center gap-3 py-3.5 hover:bg-gray-50 -mx-5 px-5 transition-colors">
                <div className="w-9 h-9 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
                  <span className="text-xs font-bold text-primary-700">{course.code?.slice(0, 3)}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{course.name}</p>
                  <p className="text-xs text-gray-500">{course.code} · {course.department} · {course.enrolledCount} students</p>
                </div>
                <ChevronRight size={14} className="text-gray-300 shrink-0" />
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
