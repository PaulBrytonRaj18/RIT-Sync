import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { CheckSquare, Eye, EyeOff } from 'lucide-react'
import { authApi } from '../../api/auth'
import useAuthStore from '../../store/authStore'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'
import toast from 'react-hot-toast'

export default function LoginPage() {
  const navigate = useNavigate()
  const login    = useAuthStore(s => s.login)
  const [form, setForm]   = useState({ email: '', password: '' })
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)
  const [showPwd, setShowPwd] = useState(false)

  const validate = () => {
    const e = {}
    if (!form.email)    e.email    = 'Email is required'
    if (!form.password) e.password = 'Password is required'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      const data = await authApi.login(form)
      login(data)
      toast.success(`Welcome back, ${data.user.name}!`)
      const routes = { STUDENT: '/student', TEACHER: '/teacher', ADMIN: '/admin' }
      navigate(routes[data.user.role] || '/student', { replace: true })
    } catch (err) {
      const msg = err.response?.data?.message || 'Invalid credentials'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface px-4 py-6 safe-top safe-bottom">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-primary-600 rounded-2xl mb-4 shadow-lg">
            <CheckSquare size={32} className="text-white" />
          </div>
          <h1 className="text-3xl font-bold text-gray-900">RIT-Sync</h1>
          <p className="text-gray-500 text-base mt-1">Smart Attendance</p>
        </div>

        <div className="card shadow-card-hover">
          <h2 className="text-xl font-semibold text-gray-900 mb-5">Sign in</h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <Input label="Email" type="email" placeholder="you@rit.edu"
              value={form.email} onChange={e => setForm(p => ({ ...p, email: e.target.value }))}
              error={errors.email} autoComplete="email" />

            <div>
              <label className="label">Password</label>
              <div className="relative">
                <input type={showPwd ? 'text' : 'password'}
                  className="input pr-12"
                  placeholder="Enter password"
                  value={form.password}
                  onChange={e => setForm(p => ({ ...p, password: e.target.value }))}
                  autoComplete="current-password" />
                <button type="button"
                  className="absolute right-3 top-1/2 -translate-y-1/2 p-2 text-gray-400 hover:text-gray-600 touch-manipulation"
                  onClick={() => setShowPwd(!showPwd)}>
                  {showPwd ? <EyeOff size={20} /> : <Eye size={20} />}
                </button>
              </div>
              {errors.password && <p className="mt-1 text-sm text-red-600">{errors.password}</p>}
            </div>

            <Button type="submit" variant="primary" className="w-full mt-4 h-12 text-base" loading={loading}>
              {loading ? 'Signing in...' : 'Sign in'}
            </Button>
          </form>

          <p className="text-center text-base text-gray-500 mt-6">
            New?{' '}
            <Link to="/register" className="text-primary-600 font-medium hover:underline">Create account</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
