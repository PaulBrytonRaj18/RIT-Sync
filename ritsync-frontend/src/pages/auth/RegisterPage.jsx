import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { CheckSquare } from 'lucide-react'
import { authApi } from '../../api/auth'
import useAuthStore from '../../store/authStore'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Button from '../../components/ui/Button'
import toast from 'react-hot-toast'

const DEPARTMENTS = ['CSE','ECE','EEE','MECH','CIVIL','IT','AIDS','AIML']

export default function RegisterPage() {
  const navigate = useNavigate()
  const loginStore = useAuthStore(s => s.login)
  const [form, setForm] = useState({
    name: '', email: '', password: '', role: 'STUDENT',
    department: 'CSE', rollNumber: '', employeeId: '', phone: ''
  })
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)

  const set = (k) => (e) => setForm(p => ({ ...p, [k]: e.target.value }))

  const validate = () => {
    const e = {}
    if (!form.name.trim()) e.name = 'Name is required'
    if (!form.email.trim()) e.email = 'Email is required'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) e.email = 'Invalid email format'
    if (!form.password) e.password = 'Password is required'
    else if (form.password.length < 8) e.password = 'Password must be at least 8 characters'
    if (form.role === 'STUDENT' && !form.rollNumber.trim()) e.rollNumber = 'Roll number is required for students'
    if (form.role === 'TEACHER' && !form.employeeId.trim()) e.employeeId = 'Employee ID is required for teachers'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      const data = await authApi.register(form)
      loginStore(data)
      toast.success('Account created!')
      const routes = { STUDENT: '/student', TEACHER: '/teacher' }
      navigate(routes[data.user.role] || '/student', { replace: true })
    } catch (err) {
      toast.error(err.response?.data?.message || 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 bg-primary-600 rounded-2xl mb-4 shadow-lg">
            <CheckSquare size={28} className="text-white" />
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Create your account</h1>
          <p className="text-gray-500 text-sm mt-1">Join RIT-Sync</p>
        </div>

        <div className="card">
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input label="Full name" placeholder="Your name" value={form.name} onChange={set('name')} error={errors.name} required />
            <Input label="Email" type="email" placeholder="you@rit.edu" value={form.email} onChange={set('email')} error={errors.email} required />
            <Input label="Password" type="password" placeholder="Min 8 characters" value={form.password} onChange={set('password')} error={errors.password} required minLength={8} />

            <div className="grid grid-cols-2 gap-3">
              <Select label="Role" value={form.role} onChange={set('role')}>
                <option value="STUDENT">Student</option>
                <option value="TEACHER">Teacher</option>
              </Select>
              <Select label="Department" value={form.department} onChange={set('department')}>
                {DEPARTMENTS.map(d => <option key={d}>{d}</option>)}
              </Select>
            </div>

            {form.role === 'STUDENT' ? (
              <Input label="Roll number" placeholder="21CSE001" value={form.rollNumber} onChange={set('rollNumber')} error={errors.rollNumber} required />
            ) : (
              <Input label="Employee ID" placeholder="EMP001" value={form.employeeId} onChange={set('employeeId')} error={errors.employeeId} required />
            )}

            <Input label="Phone (optional)" type="tel" placeholder="+91 9876543210" value={form.phone} onChange={set('phone')} />

            <Button type="submit" variant="primary" className="w-full mt-2" loading={loading}>
              {loading ? 'Creating account…' : 'Create account'}
            </Button>
          </form>

          <p className="text-center text-sm text-gray-500 mt-4">
            Already have an account?{' '}
            <Link to="/login" className="text-primary-600 font-medium hover:underline">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
