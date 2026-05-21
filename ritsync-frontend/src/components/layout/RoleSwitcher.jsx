import { BookOpen, Users } from 'lucide-react'
import useAuthStore from '../../store/authStore'

export default function RoleSwitcher() {
  const { teacherRole, setTeacherRole } = useAuthStore()

  if (!teacherRole) return null

  return (
    <div className="flex bg-gray-100 rounded-xl p-1 mb-4">
      <button
        onClick={() => setTeacherRole('subject_teacher')}
        className={`flex-1 flex items-center justify-center gap-2 px-3 py-2 text-xs font-medium rounded-lg transition-all ${
          teacherRole === 'subject_teacher'
            ? 'bg-white text-primary-700 shadow-sm'
            : 'text-gray-500 hover:text-gray-700'
        }`}
      >
        <BookOpen size={14} />
        Subject
      </button>
      <button
        onClick={() => setTeacherRole('class_teacher')}
        className={`flex-1 flex items-center justify-center gap-2 px-3 py-2 text-xs font-medium rounded-lg transition-all ${
          teacherRole === 'class_teacher'
            ? 'bg-white text-purple-700 shadow-sm'
            : 'text-gray-500 hover:text-gray-700'
        }`}
      >
        <Users size={14} />
        Class
      </button>
    </div>
  )
}
