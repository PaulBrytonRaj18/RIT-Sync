import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { attendanceApi } from '../../api/attendance'
import { sessionsApi } from '../../api/sessions'
import PageHeader from '../../components/shared/PageHeader'
import Button from '../../components/ui/Button'
import Modal from '../../components/ui/Modal'
import Badge from '../../components/ui/Badge'
import Input from '../../components/ui/Input'
import Spinner from '../../components/ui/Spinner'
import { statusBadgeClass, fmt } from '../../utils/format'
import toast from 'react-hot-toast'

export default function TeacherAttendancePage() {
  const { sessionId } = useParams()
  const [records,   setRecords]   = useState([])
  const [session,   setSession]   = useState(null)
  const [loading,   setLoading]   = useState(true)
  const [override,  setOverride]  = useState(null)   // { record }
  const [reason,    setReason]    = useState('')
  const [saving,    setSaving]    = useState(false)

  const load = async () => {
    const [rec, sessions] = await Promise.all([
      attendanceApi.getSessionAttendance(sessionId),
      sessionsApi.getOpenSessions(),
    ])
    setRecords(rec)
    setSession(sessions.find(s => s.id === Number(sessionId)) || null)
    setLoading(false)
  }

  useEffect(() => { load() }, [sessionId])

  const handleOverride = async (status) => {
    if (!reason.trim()) { toast.error('Please provide a reason'); return }
    setSaving(true)
    try {
      await attendanceApi.manualOverride(sessionId, override.student.id, { status, reason })
      toast.success('Attendance updated')
      setOverride(null)
      setReason('')
      load()
    } catch { toast.error('Override failed') }
    finally { setSaving(false) }
  }

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  const present = records.filter(r => r.status === 'PRESENT').length
  const total   = records.length

  return (
    <div className="animate-fade-in">
      <PageHeader title="Session attendance"
        subtitle={session ? `${session.course?.name} · ${fmt.datetime(session.startTime)}` : ''}
        action={
          session && session.isOpen ? (
            <Link to={`/teacher/mark/${sessionId}`}>
              <Button variant="primary" size="sm">Quick mark</Button>
            </Link>
          ) : null
        }
      />

      <div className="flex gap-4 mb-5 text-sm">
        <span className="text-gray-500">Total: <b className="text-gray-900">{total}</b></span>
        <span className="text-green-600">Present: <b>{present}</b></span>
        <span className="text-red-500">Absent: <b>{total - present}</b></span>
        <span className="text-gray-500 ml-auto">{total > 0 ? ((present/total)*100).toFixed(0) : 0}% attendance</span>
      </div>

      <div className="card divide-y divide-gray-50">
        {records.length === 0 && (
          <p className="text-center text-sm text-gray-500 py-10">No students found for this session</p>
        )}
        {records.map(rec => (
          <div key={rec.id} className="flex items-center gap-3 py-3">
            <div className="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center text-primary-700 text-xs font-semibold shrink-0">
              {rec.student?.name?.[0]}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-gray-900 truncate">{rec.student?.name}</p>
              <div className="flex items-center gap-2 mt-0.5">
                <VerifyDot ok={rec.faceVerified}   label="Face" />
                <VerifyDot ok={rec.gpsVerified}    label="GPS"  />
                <VerifyDot ok={rec.deviceVerified} label="Device" />
                {rec.manuallyOverridden && <span className="text-[10px] text-amber-600 bg-amber-50 px-1.5 py-0.5 rounded">Manual</span>}
              </div>
            </div>
            <Badge variant={rec.status === 'PRESENT' ? 'green' : rec.status === 'LATE' ? 'yellow' : 'red'}>
              {rec.status}
            </Badge>
            <button onClick={() => setOverride(rec)}
              className="text-xs text-primary-600 hover:underline ml-2 shrink-0">
              Override
            </button>
          </div>
        ))}
      </div>

      <Modal open={!!override} onClose={() => setOverride(null)} title="Manual attendance override">
        <div className="space-y-4">
          <p className="text-sm text-gray-600">
            Override attendance for <b>{override?.student?.name}</b>.
            Current status: <Badge variant={override?.status === 'PRESENT' ? 'green' : 'red'}>{override?.status}</Badge>
          </p>
          <Input label="Reason for override *" placeholder="e.g. Student submitted medical certificate"
            value={reason} onChange={e => setReason(e.target.value)} />
          <div className="grid grid-cols-3 gap-2">
            {['PRESENT','LATE','EXCUSED'].map(s => (
              <Button key={s} variant="secondary" onClick={() => handleOverride(s)} loading={saving}>
                {s}
              </Button>
            ))}
          </div>
        </div>
      </Modal>
    </div>
  )
}

function VerifyDot({ ok, label }) {
  return (
    <span className={`flex items-center gap-0.5 text-[10px] ${ok ? 'text-green-600' : 'text-gray-300'}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${ok ? 'bg-green-500' : 'bg-gray-300'}`} />
      {label}
    </span>
  )
}
