import { useEffect, useState, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, CheckSquare, XSquare, Clock, Users } from 'lucide-react'
import { attendanceApi } from '../../api/attendance'
import { sessionsApi } from '../../api/sessions'
import { coursesApi } from '../../api/courses'
import { teacherApi } from '../../api/teacher'
import useAuthStore from '../../store/authStore'
import PageHeader from '../../components/shared/PageHeader'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import Spinner from '../../components/ui/Spinner'
import toast from 'react-hot-toast'

const STATUS_ORDER = ['PRESENT', 'ABSENT', 'LATE']

export default function AttendanceMarkingPage() {
  const { sessionId } = useParams()
  const user = useAuthStore(s => s.user)
  const [records, setRecords] = useState([])
  const [session, setSession] = useState(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const load = async () => {
    try {
      const [rec, s] = await Promise.all([
        attendanceApi.getSessionAttendance(sessionId),
        sessionsApi.getOpenSessions(),
      ])
      setRecords(rec)
      setSession(s.find(s => s.id === Number(sessionId)) || null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [sessionId])

  const cycleStatus = useCallback((recordId) => {
    setRecords(prev => prev.map(r => {
      if (r.id !== recordId) return r
      const idx = STATUS_ORDER.indexOf(r.status)
      const nextStatus = STATUS_ORDER[(idx + 1) % STATUS_ORDER.length]
      return { ...r, status: nextStatus }
    }))
  }, [])

  const markAllPresent = () => {
    setRecords(prev => prev.map(r => ({ ...r, status: 'PRESENT' })))
  }

  const handleSubmit = async () => {
    setSaving(true)
    try {
      const results = await Promise.all(
        records.map(rec =>
          attendanceApi.manualOverride(sessionId, rec.studentId, {
            status: rec.status,
            reason: rec.status === 'PRESENT' ? 'Marked by teacher' :
                    rec.status === 'LATE' ? 'Late arrival' : 'Marked absent'
          }).catch(() => null)
        )
      )
      const succeeded = results.filter(Boolean).length
      toast.success(`${succeeded}/${records.length} records updated`)
    } catch {
      toast.error('Failed to save attendance')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  const present = records.filter(r => r.status === 'PRESENT').length
  const absent = records.filter(r => r.status === 'ABSENT').length
  const late = records.filter(r => r.status === 'LATE').length

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Mark attendance"
        subtitle={session ? `${session.courseName} · ${session.title || 'Session'}` : ''}
      />

      {/* Stats bar */}
      <div className="flex items-center gap-4 mb-5 text-sm flex-wrap">
        <span className="text-gray-500"><Users size={14} className="inline mr-1" />{records.length} students</span>
        <span className="text-green-600"><CheckSquare size={14} className="inline mr-1" />{present} Present</span>
        <span className="text-red-500"><XSquare size={14} className="inline mr-1" />{absent} Absent</span>
        <span className="text-amber-600"><Clock size={14} className="inline mr-1" />{late} Late</span>
        <div className="ml-auto">
          <Button variant="secondary" size="sm" onClick={markAllPresent}>
            <CheckSquare size={13} /> Present All
          </Button>
        </div>
      </div>

      {/* Roster */}
      <div className="card divide-y divide-gray-50">
        {records.length === 0 ? (
          <p className="text-center text-sm text-gray-500 py-10">No students in this session</p>
        ) : (
          records.map(rec => (
            <div key={rec.id} className="flex items-center gap-3 py-2.5">
              <div className="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center text-primary-700 text-xs font-semibold shrink-0">
                {rec.studentName?.[0] || '?'}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-gray-900">{rec.studentName}</p>
                <p className="text-xs text-gray-400">{rec.studentRollNumber || '—'}</p>
              </div>
              <div className="flex gap-1">
                {STATUS_ORDER.map(status => {
                  const isActive = rec.status === status
                  return (
                    <button
                      key={status}
                      onClick={() => {
                        setRecords(prev => prev.map(r =>
                          r.id === rec.id ? { ...r, status } : r
                        ))
                      }}
                      className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition-all ${
                        isActive
                          ? status === 'PRESENT' ? 'bg-green-50 border-green-300 text-green-700'
                            : status === 'ABSENT' ? 'bg-red-50 border-red-300 text-red-700'
                            : 'bg-amber-50 border-amber-300 text-amber-700'
                          : 'bg-white border-gray-200 text-gray-400 hover:border-gray-300'
                      }`}
                    >
                      {status === 'PRESENT' ? 'P' : status === 'ABSENT' ? 'A' : 'L'}
                    </button>
                  )
                })}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Submit */}
      {records.length > 0 && (
        <div className="mt-6 flex justify-end">
          <Button variant="primary" onClick={handleSubmit} loading={saving}>
            {saving ? 'Saving...' : 'Submit attendance'}
          </Button>
        </div>
      )}
    </div>
  )
}
