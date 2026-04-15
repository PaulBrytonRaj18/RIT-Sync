import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { BookOpen, ChevronRight, Wifi } from 'lucide-react'
import { coursesApi } from '../../api/courses'
import { getCachedCourses } from '../../offline/db'
import useOfflineStore from '../../store/offlineStore'
import PageHeader from '../../components/shared/PageHeader'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'

export default function StudentCoursesPage() {
  const isOnline = useOfflineStore(s => s.isOnline)
  const [courses, setCourses] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    (async () => {
      try {
        const c = isOnline ? await coursesApi.getMyCourses() : await getCachedCourses()
        setCourses(c)
      } finally { setLoading(false) }
    })()
  }, [isOnline])

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  return (
    <div className="animate-fade-in">
      <PageHeader title="My courses" subtitle={`${courses.length} enrolled`} />
      {!isOnline && (
        <div className="mb-4 flex items-center gap-2 text-xs text-amber-700 bg-amber-50 border border-amber-100 rounded-xl p-3">
          <Wifi size={13} /> Showing cached data
        </div>
      )}
      {courses.length === 0 ? (
        <EmptyState icon={BookOpen} title="No courses yet" description="You haven't been enrolled in any courses." />
      ) : (
        <div className="grid gap-3">
          {courses.map(course => (
            <Link key={course.id} to={`/student/courses/${course.id}`}
              className="card hover:shadow-card-hover transition-shadow flex items-center gap-4">
              <div className="w-12 h-12 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
                <span className="text-sm font-bold text-primary-700">{course.code.slice(0, 3)}</span>
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-medium text-gray-900">{course.name}</p>
                <p className="text-sm text-gray-500">{course.code} · {course.department} · {course.creditHours} credits</p>
                {course.semester && <p className="text-xs text-gray-400 mt-0.5">{course.semester} {course.academicYear}</p>}
              </div>
              <ChevronRight size={16} className="text-gray-300 shrink-0" />
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
