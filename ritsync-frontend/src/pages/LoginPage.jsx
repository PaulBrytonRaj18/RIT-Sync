import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Loader2, ShieldCheck } from 'lucide-react'
import { authAPI } from '../../services/api'
import { useAuthStore } from '../../store/authStore'

export default function LoginPage() {
  const [form,    setForm]    = useState({ email: '', password: '' })
  const [show,    setShow]    = useState(false)
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState('')
  const navigate = useNavigate()
  const login    = useAuthStore(s => s.login)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')

    try {
      const deviceId = useAuthStore.getState().deviceId
      const { data } = await authAPI.login({ ...form, deviceId })
      login({ user: data.user, token: data.token, refreshToken: data.refreshToken })

      // Route by role
      const role = data.user.role
      if (role === 'STUDENT') navigate('/student/dashboard')
      else if (role === 'TEACHER') navigate('/teacher/dashboard')
      else if (role === 'ADMIN')   navigate('/admin/dashboard')
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-dvh bg-surface flex flex-col items-center justify-center px-4 py-10">
      {/* Brand */}
      <div className="mb-10 text-center animate-fade-in">
        <div className="flex items-center justify-center mb-4">
          <div className="relative">
            <div className="w-16 h-16 rounded-2xl bg-brand-600 flex items-center justify-center shadow-glow">
              <ShieldCheck size={32} className="text-white" />
            </div>
            <span className="absolute -top-1 -right-1 w-4 h-4 bg-green-400 rounded-full border-2 border-surface" />
          </div>
        </div>
        <h1 className="text-3xl font-bold tracking-tight text-white">RIT-Sync</h1>
        <p className="text-slate-400 mt-1 text-sm">Smart Attendance Management</p>
      </div>

      {/* Card */}
      <div className="w-full max-w-sm animate-slide-up">
        <div className="card p-7">
          <h2 className="text-xl font-semibold text-white mb-6">Sign in</h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-slate-400 mb-1.5">
                Email address
              </label>
              <input
                type="email"
                className="input"
                placeholder="you@rit.edu"
                value={form.email}
                onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                required
                autoComplete="email"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-400 mb-1.5">
                Password
              </label>
              <div className="relative">
                <input
                  type={show ? 'text' : 'password'}
                  className="input pr-12"
                  placeholder="••••••••"
                  value={form.password}
                  onChange={e => setForm(f => ({ ...f, password: e.target.value }))}
                  required
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShow(s => !s)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-500 hover:text-slate-300 transition-colors"
                >
                  {show ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
            </div>

            {error && (
              <div className="bg-red-500/10 border border-red-500/30 text-red-400 text-sm px-4 py-3 rounded-xl">
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full flex items-center justify-center gap-2 mt-2"
            >
              {loading && <Loader2 size={18} className="animate-spin" />}
              {loading ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <div className="mt-6 pt-5 border-t border-surface-border">
            <div className="flex flex-col gap-2 text-sm text-slate-500">
              <div className="flex items-center gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-brand-400" />
                Triple-verified attendance
              </div>
              <div className="flex items-center gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-green-400" />
                Works offline — syncs automatically
              </div>
              <div className="flex items-center gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
                GPS + Face + Device bound
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Demo accounts hint */}
      <div className="mt-6 text-xs text-slate-600 text-center animate-fade-in">
        Demo: student@rit.edu / teacher@rit.edu / admin@rit.edu (password: demo1234)
      </div>
    </div>
  )
}
