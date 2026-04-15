import { useEffect, useState } from 'react'
import { Users, Search, UserPlus } from 'lucide-react'
import { usersApi } from '../../api/users'
import PageHeader from '../../components/shared/PageHeader'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import Spinner from '../../components/ui/Spinner'
import EmptyState from '../../components/ui/EmptyState'
import { fmt } from '../../utils/format'

export default function AdminUsersPage() {
  const [users,   setUsers]   = useState([])
  const [loading, setLoading] = useState(true)
  const [role,    setRole]    = useState('STUDENT')
  const [search,  setSearch]  = useState('')

  useEffect(() => {
    setLoading(true)
    usersApi.getByRole(role).then(u => { setUsers(u); setLoading(false) })
  }, [role])

  const filtered = users.filter(u =>
    u.name.toLowerCase().includes(search.toLowerCase()) ||
    u.email.toLowerCase().includes(search.toLowerCase()) ||
    (u.rollNumber || '').toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="animate-fade-in">
      <PageHeader title="Users" subtitle="Manage students, teachers and admins" />

      <div className="flex gap-3 mb-5">
        <div className="flex-1">
          <div className="relative">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input className="input pl-9" placeholder="Search by name, email, roll number…"
              value={search} onChange={e => setSearch(e.target.value)} />
          </div>
        </div>
        <Select value={role} onChange={e => setRole(e.target.value)} className="w-36">
          <option value="STUDENT">Students</option>
          <option value="TEACHER">Teachers</option>
          <option value="ADMIN">Admins</option>
        </Select>
      </div>

      {loading ? (
        <div className="flex justify-center py-20"><Spinner size="lg" /></div>
      ) : filtered.length === 0 ? (
        <EmptyState icon={Users} title="No users found" description="Try a different search or role." />
      ) : (
        <div className="card divide-y divide-gray-50">
          <div className="hidden sm:grid grid-cols-5 gap-4 pb-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">
            <span className="col-span-2">Name</span>
            <span>ID</span>
            <span>Department</span>
            <span>Face ID</span>
          </div>
          {filtered.map(u => (
            <div key={u.id} className="grid grid-cols-1 sm:grid-cols-5 gap-2 sm:gap-4 py-3 items-center">
              <div className="col-span-2 flex items-center gap-3">
                <div className="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center text-primary-700 text-xs font-semibold shrink-0">
                  {u.name[0].toUpperCase()}
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{u.name}</p>
                  <p className="text-xs text-gray-500 truncate">{u.email}</p>
                </div>
              </div>
              <span className="text-xs text-gray-600 font-mono">{u.rollNumber || u.employeeId || '—'}</span>
              <span className="text-xs text-gray-600">{u.department}</span>
              <span>{u.faceEnrolled
                ? <Badge variant="green">Enrolled</Badge>
                : <Badge variant="gray">Pending</Badge>
              }</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
