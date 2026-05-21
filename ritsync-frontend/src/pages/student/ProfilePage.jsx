import { CheckSquare, Mail, Building, BookOpen, Shield, Hash } from 'lucide-react'
import useAuthStore from '../../store/authStore'
import PageHeader from '../../components/shared/PageHeader'

export default function ProfilePage() {
  const user = useAuthStore(s => s.user)

  const details = [
    { icon: Mail,     label: 'Email',      value: user?.email },
    { icon: Building, label: 'Department', value: user?.department || '—' },
    { icon: BookOpen, label: 'Semester',   value: user?.semester || '—' },
    { icon: Shield,   label: 'Role',       value: user?.role },
    { icon: Hash,     label: user?.role === 'STUDENT' ? 'Roll number' : 'Employee ID',
                     value: user?.rollNumber || user?.employeeId || '—' },
  ]

  return (
    <div className="animate-fade-in max-w-lg mx-auto">
      <PageHeader title="My profile" subtitle="Your institutional account details" />

      <div className="card">
        {/* Avatar & name */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-20 h-20 rounded-full bg-primary-100 flex items-center justify-center text-primary-700 text-3xl font-bold mb-4">
            {user?.name?.[0]?.toUpperCase() || 'U'}
          </div>
          <h2 className="text-xl font-bold text-gray-900">{user?.name}</h2>
          <p className="text-sm text-gray-500 mt-0.5">{user?.department}</p>
        </div>

        {/* Details */}
        <div className="space-y-4">
          {details.map(({ icon: Icon, label, value }) => (
            <div key={label} className="flex items-center gap-4 p-3 bg-gray-50 rounded-xl">
              <div className="w-10 h-10 rounded-lg bg-white flex items-center justify-center">
                <Icon size={18} className="text-gray-500" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-xs text-gray-400">{label}</p>
                <p className="text-sm font-medium text-gray-900 truncate">{value}</p>
              </div>
            </div>
          ))}
        </div>

        {/* Face enrollment status */}
        <div className="mt-6 p-4 rounded-xl border border-gray-100">
          <div className="flex items-center gap-3">
            <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
              user?.faceEnrolled ? 'bg-green-100' : 'bg-amber-100'
            }`}>
              <CheckSquare size={18} className={user?.faceEnrolled ? 'text-green-600' : 'text-amber-600'} />
            </div>
            <div>
              <p className="text-sm font-medium text-gray-900">
                Face ID {user?.faceEnrolled ? 'enrolled' : 'not enrolled'}
              </p>
              <p className="text-xs text-gray-500 mt-0.5">
                {user?.faceEnrolled
                  ? 'Your face is registered for attendance verification'
                  : 'Enroll your face to mark attendance'}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}