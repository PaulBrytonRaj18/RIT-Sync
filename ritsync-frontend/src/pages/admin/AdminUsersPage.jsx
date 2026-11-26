import { useCallback, useEffect, useState } from 'react'
import { Users, Search, UserPlus, ScanFace, Smartphone } from 'lucide-react'
import { usersApi } from '../../api/users'
import { adminApi } from '../../api/admin'
import PageHeader from '../../components/shared/PageHeader'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Badge from '../../components/ui/Badge'
import Button from '../../components/ui/Button'
import Modal from '../../components/ui/Modal'
import Spinner from '../../components/ui/Spinner'
import EmptyState from '../../components/ui/EmptyState'
import toast from 'react-hot-toast'

const EMPTY_FORM = {
  name: '', email: '', password: '', role: 'TEACHER',
  department: '', rollNumber: '', employeeId: '', phone: '',
}

export default function AdminUsersPage() {
  const [users,   setUsers]   = useState([])
  const [loading, setLoading] = useState(true)
  const [role,    setRole]    = useState('STUDENT')
  const [search,  setSearch]  = useState('')
  const [showNew, setShowNew] = useState(false)
  const [form,    setForm]    = useState(EMPTY_FORM)
  const [saving,  setSaving]  = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    usersApi.getByRole(role)
      .then(setUsers)
      .catch(() => toast.error('Could not load users'))
      .finally(() => setLoading(false))
  }, [role])

  useEffect(() => { load() }, [load])

  const set = (k) => (e) => setForm(p => ({ ...p, [k]: e.target.value }))

  const q = search.toLowerCase()
  const filtered = users.filter(u =>
    u.name.toLowerCase().includes(q) ||
    u.email.toLowerCase().includes(q) ||
    (u.rollNumber || '').toLowerCase().includes(q)
  )

  const handleCreate = async () => {
    setSaving(true)
    try {
      const created = await adminApi.createUser(form)
      toast.success(`${created.role.toLowerCase()} account created`)
      setShowNew(false)
      setForm(EMPTY_FORM)
      if (created.role === role) load()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not create user')
    } finally {
      setSaving(false)
    }
  }

  const handleReset = async (u, kind) => {
    const what = kind === 'face' ? 'Face ID' : 'trusted devices'
    if (!window.confirm(`Reset ${what} for ${u.name}?`)) return
    try {
      if (kind === 'face') await adminApi.resetFace(u.id)
      else await adminApi.resetDevices(u.id)
      toast.success(`${what} reset for ${u.name}`)
      if (kind === 'face') load()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Reset failed')
    }
  }

  return (
    <div className="animate-fade-in">
      <PageHeader title="Users" subtitle="Manage students, teachers and admins"
        action={<Button variant="primary" onClick={() => setShowNew(true)}><UserPlus size={15} /> New user</Button>} />

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
          <div className="hidden sm:grid grid-cols-6 gap-4 pb-2 text-xs font-semibold text-gray-500 uppercase tracking-wide">
            <span className="col-span-2">Name</span>
            <span>ID</span>
            <span>Department</span>
            <span>Face ID</span>
            <span className="text-right">Actions</span>
          </div>
          {filtered.map(u => (
            <div key={u.id} className="grid grid-cols-1 sm:grid-cols-6 gap-2 sm:gap-4 py-3 items-center">
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
              <span>{u.role !== 'STUDENT'
                ? <span className="text-xs text-gray-400">—</span>
                : u.faceEnrolled
                  ? <Badge variant="green">Enrolled</Badge>
                  : <Badge variant="gray">Pending</Badge>}
              </span>
              <div className="flex sm:justify-end gap-1">
                {u.role === 'STUDENT' && u.faceEnrolled && (
                  <button title="Reset Face ID" onClick={() => handleReset(u, 'face')}
                    className="p-1.5 rounded-lg text-gray-400 hover:text-amber-600 hover:bg-amber-50">
                    <ScanFace size={15} />
                  </button>
                )}
                <button title="Reset trusted devices" onClick={() => handleReset(u, 'devices')}
                  className="p-1.5 rounded-lg text-gray-400 hover:text-amber-600 hover:bg-amber-50">
                  <Smartphone size={15} />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={showNew} onClose={() => setShowNew(false)} title="Create user" size="md">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <Select label="Role *" value={form.role} onChange={set('role')}>
              <option value="TEACHER">Teacher</option>
              <option value="ADMIN">Admin</option>
              <option value="STUDENT">Student</option>
            </Select>
            <Input label="Department *" value={form.department} onChange={set('department')} placeholder="CSE" />
          </div>
          <Input label="Full name *" value={form.name} onChange={set('name')} />
          <Input label="Email *" type="email" value={form.email} onChange={set('email')} />
          <Input label="Temporary password *" type="password" value={form.password} onChange={set('password')}
            placeholder="Min 8 characters" minLength={8} />
          {form.role === 'STUDENT'
            ? <Input label="Roll number *" value={form.rollNumber} onChange={set('rollNumber')} />
            : <Input label={`Employee ID${form.role === 'TEACHER' ? ' *' : ''}`} value={form.employeeId} onChange={set('employeeId')} />}
          <Input label="Phone (optional)" type="tel" value={form.phone} onChange={set('phone')} />
          <div className="flex gap-3 pt-2">
            <Button variant="secondary" className="flex-1" onClick={() => setShowNew(false)}>Cancel</Button>
            <Button variant="primary" className="flex-1" onClick={handleCreate} loading={saving}>Create user</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
