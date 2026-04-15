import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus, CalendarCheck, ChevronRight, Play, Square } from 'lucide-react'
import { sessionsApi } from '../../api/sessions'
import { coursesApi } from '../../api/courses'
import useAuthStore from '../../store/authStore'
import PageHeader from '../../components/shared/PageHeader'
import Button from '../../components/ui/Button'
import Modal from '../../components/ui/Modal'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import { fmt } from '../../utils/format'
import toast from 'react-hot-toast'

export default function TeacherSessionsPage() {
  const user = useAuthStore(s => s.user)
  const [sessions, setSessions] = useState([])
  const [courses,  setCourses]  = useState([])
  const [loading,  setLoading]  = useState(true)
  const [showNew,  setShowNew]  = useState(false)
  const [form,     setForm]     = useState({
    courseId: '', title: '', startTime: '', endTime: '',
    geofenceLat: '', geofenceLng: '', geofenceRadiusM: '100'
  })
  const [creating, setCreating] = useState(false)

  const load = async () => {
    const courses = await coursesApi.getMyCourses()
    setCourses(courses)
    if (courses.length > 0) {
      const sessionArrays = await Promise.all(
        courses.map(course => sessionsApi.getCourseSessions(course.id).catch(() => []))
      )
      setSessions(sessionArrays.flat().sort((a,b) => new Date(b.startTime) - new Date(a.startTime)))
    }
    setLoading(false)
  }

  useEffect(() => { load() }, [user.id])

  const handleCreate = async () => {
    if (!form.courseId || !form.startTime || !form.endTime || !form.geofenceLat || !form.geofenceLng) {
      toast.error('Please fill all required fields')
      return
    }
    setCreating(true)
    try {
      await sessionsApi.createSession({
        ...form,
        courseId: Number(form.courseId),
        geofenceRadiusM: Number(form.geofenceRadiusM),
      })
      toast.success('Session created!')
      setShowNew(false)
      load()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to create session')
    } finally {
      setCreating(false)
    }
  }

  const handleToggle = async (session) => {
    try {
      if (session.isOpen) {
        await sessionsApi.closeSession(session.id)
        toast.success('Session closed')
      } else {
        await sessionsApi.openSession(session.id)
        toast.success('Session is now live!')
      }
      load()
    } catch { toast.error('Failed to update session') }
  }

  const set = k => e => setForm(p => ({ ...p, [k]: e.target.value }))

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  return (
    <div className="animate-fade-in">
      <PageHeader title="Sessions" subtitle="Create and manage class sessions"
        action={
          <Button variant="primary" size="sm" onClick={() => setShowNew(true)}>
            <Plus size={15} /> New session
          </Button>
        }
      />

      {sessions.length === 0 ? (
        <EmptyState icon={CalendarCheck} title="No sessions yet"
          description="Create your first session to start taking attendance"
          action={<Button variant="primary" onClick={() => setShowNew(true)}><Plus size={14} /> New session</Button>}
        />
      ) : (
        <div className="space-y-3">
          {sessions.map(s => (
            <div key={s.id} className="card flex items-center gap-4">
              <Link to={`/teacher/attendance/${s.id}`} className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-0.5">
                  <p className="text-sm font-medium text-gray-900 truncate">{s.course?.name || '—'}</p>
                  {s.isOpen && <Badge variant="green">Live</Badge>}
                </div>
                <p className="text-xs text-gray-500">
                  {s.title || 'Session'} · {fmt.datetime(s.startTime)}
                </p>
              </Link>
              <button onClick={() => handleToggle(s)}
                className={`p-2 rounded-xl transition-colors shrink-0 ${
                  s.isOpen
                    ? 'bg-red-50 hover:bg-red-100 text-red-600'
                    : 'bg-green-50 hover:bg-green-100 text-green-600'
                }`}
                title={s.isOpen ? 'Close session' : 'Open session'}>
                {s.isOpen ? <Square size={15} /> : <Play size={15} />}
              </button>
            </div>
          ))}
        </div>
      )}

      <Modal open={showNew} onClose={() => setShowNew(false)} title="Create new session" size="md">
        <div className="space-y-4">
          <Select label="Course *" value={form.courseId} onChange={set('courseId')}>
            <option value="">Select a course…</option>
            {courses.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
          <Input label="Session title (optional)" placeholder="e.g. Week 3 — Lecture" value={form.title} onChange={set('title')} />
          <div className="grid grid-cols-2 gap-3">
            <Input label="Start time *" type="datetime-local" value={form.startTime} onChange={set('startTime')} />
            <Input label="End time *"   type="datetime-local" value={form.endTime}   onChange={set('endTime')} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Input label="Latitude *"  type="number" step="any" placeholder="12.9716" value={form.geofenceLat} onChange={set('geofenceLat')} />
            <Input label="Longitude *" type="number" step="any" placeholder="77.5946" value={form.geofenceLng} onChange={set('geofenceLng')} />
          </div>
          <Input label="Geofence radius (metres)" type="number" value={form.geofenceRadiusM} onChange={set('geofenceRadiusM')} />
          <div className="flex gap-3 pt-2">
            <Button variant="secondary" className="flex-1" onClick={() => setShowNew(false)}>Cancel</Button>
            <Button variant="primary"   className="flex-1" onClick={handleCreate} loading={creating}>Create session</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
