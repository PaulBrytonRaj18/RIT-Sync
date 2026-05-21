import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { CheckSquare, Eye, EyeOff, BookOpen, GraduationCap, Shield, Users } from 'lucide-react'
import { authApi } from '../../api/auth'
import useAuthStore from '../../store/authStore'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'
import toast from 'react-hot-toast'

const DEMO_ACCOUNTS = {
  student:   { email: 'student@ritsync.com',   password: 'demo1234', label: 'Student',   icon: GraduationCap },
  professor: { email: 'teacher@ritsync.com',   password: 'demo1234', label: 'Professor', icon: Users },
  admin:     { email: 'admin@ritsync.com',     password: 'demo1234', label: 'Admin',     icon: Shield },
}

const ROUTES = { STUDENT: '/student', TEACHER: '/teacher', ADMIN: '/admin' }

export default function LoginPage() {
  const navigate = useNavigate()
  const login    = useAuthStore(s => s.login)
  const [form, setForm]   = useState({ email: '', password: '' })
  const [errors, setErrors] = useState({})
  const [loading, setLoading] = useState(false)
  const [showPwd, setShowPwd] = useState(false)
  const [showProfModal, setShowProfModal] = useState(false)
  const [demoLoading, setDemoLoading] = useState(null)

  const validate = () => {
    const e = {}
    if (!form.email)    e.email    = 'Email is required'
    if (!form.password) e.password = 'Password is required'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const doLogin = async ({ email, password }, teacherRole) => {
    const data = await authApi.login({ email, password })
    login(data, teacherRole)
    toast.success(`Welcome back, ${data.user.name}!`)
    navigate(ROUTES[data.user.role] || '/student', { replace: true })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validate()) return
    setLoading(true)
    try {
      await doLogin(form)
    } catch (err) {
      const msg = err.response?.data?.message || 'Invalid credentials'
      toast.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const handleDemoLogin = async (key) => {
    if (key === 'professor') {
      setShowProfModal(true)
      return
    }
    setDemoLoading(key)
    try {
      await doLogin(DEMO_ACCOUNTS[key])
    } catch (err) {
      toast.error(err.response?.data?.message || 'Demo login failed')
    } finally {
      setDemoLoading(null)
    }
  }

  const handleProfRoleSelect = async (role) => {
    setShowProfModal(false)
    setDemoLoading('professor')
    try {
      await doLogin(DEMO_ACCOUNTS.professor, role)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Demo login failed')
    } finally {
      setDemoLoading(null)
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

          {/* ── Divider ── */}
          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-gray-200" /></div>
            <div className="relative flex justify-center"><span className="bg-white px-3 text-sm text-gray-400">Demo quick login</span></div>
          </div>

          {/* ── Demo Buttons ── */}
          <div className="space-y-2.5">
            {Object.entries(DEMO_ACCOUNTS).map(([key, acct]) => {
              const Icon = acct.icon
              const loadingRole = demoLoading === key
              return (
                <button key={key} type="button" disabled={demoLoading}
                  onClick={() => handleDemoLogin(key)}
                  className="w-full flex items-center gap-3 px-4 py-3 rounded-xl border border-gray-200
                    hover:border-primary-300 hover:bg-primary-50/50 transition-all
                    disabled:opacity-50 disabled:cursor-wait group">
                  <div className="w-10 h-10 rounded-lg bg-gray-100 flex items-center justify-center
                    group-hover:bg-primary-100 transition-colors">
                    <Icon size={20} className="text-gray-500 group-hover:text-primary-600 transition-colors" />
                  </div>
                  <div className="flex-1 text-left">
                    <p className="text-sm font-medium text-gray-900">{acct.label}</p>
                    <p className="text-xs text-gray-400">{acct.email}</p>
                  </div>
                  {loadingRole && (
                    <div className="w-5 h-5 border-2 border-primary-600 border-t-transparent rounded-full animate-spin" />
                  )}
                  {!loadingRole && (
                    <span className="text-xs font-medium text-primary-600 group-hover:underline">Quick login</span>
                  )}
                </button>
              )
            })}
          </div>

          <p className="text-center text-base text-gray-500 mt-6">
            New?{' '}
            <Link to="/register" className="text-primary-600 font-medium hover:underline">Create account</Link>
          </p>
        </div>
      </div>

      {/* ── Professor Role Selection Modal ── */}
      {showProfModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
          onClick={() => setShowProfModal(false)}>
          <div className="bg-white rounded-2xl shadow-2xl max-w-sm w-full p-6"
            onClick={e => e.stopPropagation()}>
            <h3 className="text-lg font-semibold text-gray-900 mb-1">Select Role</h3>
            <p className="text-sm text-gray-500 mb-5">Are you a Class Teacher or a Subject Teacher?</p>
            <div className="space-y-3">
              <button onClick={() => handleProfRoleSelect('subject_teacher')}
                className="w-full flex items-center gap-4 p-4 rounded-xl border-2 border-gray-200
                  hover:border-primary-400 hover:bg-primary-50/50 transition-all group">
                <div className="w-12 h-12 rounded-xl bg-blue-50 flex items-center justify-center
                  group-hover:bg-blue-100 transition-colors">
                  <BookOpen size={24} className="text-blue-600" />
                </div>
                <div className="text-left">
                  <p className="font-semibold text-gray-900">Subject Teacher</p>
                  <p className="text-sm text-gray-500">Handle subjects & mark attendance</p>
                </div>
              </button>
              <button onClick={() => handleProfRoleSelect('class_teacher')}
                className="w-full flex items-center gap-4 p-4 rounded-xl border-2 border-gray-200
                  hover:border-purple-400 hover:bg-purple-50/50 transition-all group">
                <div className="w-12 h-12 rounded-xl bg-purple-50 flex items-center justify-center
                  group-hover:bg-purple-100 transition-colors">
                  <Users size={24} className="text-purple-600" />
                </div>
                <div className="text-left">
                  <p className="font-semibold text-gray-900">Class Teacher</p>
                  <p className="text-sm text-gray-500">Monitor class & manage defaulters</p>
                </div>
              </button>
            </div>
            <button onClick={() => setShowProfModal(false)}
              className="w-full mt-4 py-2.5 text-sm text-gray-500 hover:text-gray-700 hover:bg-gray-50 rounded-xl transition-colors">
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
