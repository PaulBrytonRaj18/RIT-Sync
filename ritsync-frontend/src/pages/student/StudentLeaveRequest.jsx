import { useEffect, useState } from 'react'
import { CalendarDays, Send, Clock, CheckCircle, XCircle } from 'lucide-react'
import useAuthStore from '../../store/authStore'
import { coursesApi } from '../../api/courses'
import { teacherApi } from '../../api/teacher'
import PageHeader from '../../components/shared/PageHeader'
import Button from '../../components/ui/Button'
import Input from '../../components/ui/Input'
import Select from '../../components/ui/Select'
import Modal from '../../components/ui/Modal'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import EmptyState from '../../components/ui/EmptyState'
import toast from 'react-hot-toast'

const LEAVE_TYPES = ['MEDICAL', 'PERSONAL', 'FAMILY', 'OTHER']

export default function StudentLeaveRequest() {
  const user = useAuthStore(s => s.user)
  const [courses, setCourses] = useState([])
  const [myLeaves, setMyLeaves] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form, setForm] = useState({
    courseId: '',
    leaveType: 'MEDICAL',
    reason: '',
    fromDate: '',
    toDate: '',
  })

  useEffect(() => {
    Promise.all([
      coursesApi.getMyCourses(),
      teacherApi.getMyLeaves().catch(() => []),
    ]).then(([c, l]) => {
      setCourses(c)
      setMyLeaves(l)
    }).finally(() => setLoading(false))
  }, [])

  const handleSubmit = async () => {
    if (!form.courseId || !form.fromDate || !form.toDate || !form.reason.trim()) {
      toast.error('Please fill all required fields')
      return
    }
    if (new Date(form.fromDate) > new Date(form.toDate)) {
      toast.error('From date cannot be after To date')
      return
    }
    setSubmitting(true)
    try {
      const res = await teacherApi.requestLeave({
        courseId: Number(form.courseId),
        leaveType: form.leaveType,
        reason: form.reason,
        fromDate: form.fromDate,
        toDate: form.toDate,
      })
      toast.success('Leave request submitted')
      setShowForm(false)
      setForm({ courseId: '', leaveType: 'MEDICAL', reason: '', fromDate: '', toDate: '' })
      const leaves = await teacherApi.getMyLeaves().catch(() => [])
      setMyLeaves(leaves)
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to submit leave request')
    } finally {
      setSubmitting(false)
    }
  }

  const set = k => e => setForm(p => ({ ...p, [k]: e.target.value }))

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  return (
    <div className="animate-fade-in max-w-2xl mx-auto">
      <PageHeader
        title="Leave Request"
        subtitle="Submit leave of absence for upcoming classes"
        action={
          <Button variant="primary" size="sm" onClick={() => setShowForm(true)}>
            <Send size={14} /> New request
          </Button>
        }
      />

      {/* Previous leave requests */}
      {myLeaves.length === 0 ? (
        <EmptyState icon={CalendarDays} title="No leave requests"
          description="You haven't submitted any leave requests yet."
          action={<Button variant="primary" onClick={() => setShowForm(true)}><Send size={14} /> Request leave</Button>}
        />
      ) : (
        <div className="space-y-3">
          {myLeaves.map(lr => (
            <div key={lr.id} className="card">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
                  <CalendarDays size={18} className="text-primary-600" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">{lr.leaveType} Leave</p>
                  <p className="text-xs text-gray-500">
                    {lr.courseName || '—'} · {lr.fromDate} to {lr.toDate}
                  </p>
                </div>
                <Badge variant={
                  lr.status === 'APPROVED' ? 'green' : lr.status === 'REJECTED' ? 'red' : 'yellow'
                }>{lr.status}</Badge>
              </div>
              {lr.reason && (
                <p className="text-xs text-gray-500 mt-2 ml-[52px]">{lr.reason}</p>
              )}
              {lr.reviewNotes && (
                <p className="text-xs text-gray-400 mt-1 ml-[52px]">Review: {lr.reviewNotes}</p>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Leave request form */}
      <Modal open={showForm} onClose={() => setShowForm(false)} title="Request Leave" size="md">
        <div className="space-y-4">
          <Select label="Course *" value={form.courseId} onChange={set('courseId')}>
            <option value="">Select course…</option>
            {courses.map(c => <option key={c.id} value={c.id}>{c.name} ({c.code})</option>)}
          </Select>
          <Select label="Leave type *" value={form.leaveType} onChange={set('leaveType')}>
            {LEAVE_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
          </Select>
          <div className="grid grid-cols-2 gap-3">
            <Input label="From date *" type="date" value={form.fromDate} onChange={set('fromDate')} />
            <Input label="To date *" type="date" value={form.toDate} onChange={set('toDate')} />
          </div>
          <Input label="Reason *" placeholder="Describe the reason for your leave…"
            value={form.reason} onChange={set('reason')} />
          <div className="flex gap-3 pt-2">
            <Button variant="secondary" className="flex-1" onClick={() => setShowForm(false)}>Cancel</Button>
            <Button variant="primary" className="flex-1" onClick={handleSubmit} loading={submitting}>
              Submit
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
