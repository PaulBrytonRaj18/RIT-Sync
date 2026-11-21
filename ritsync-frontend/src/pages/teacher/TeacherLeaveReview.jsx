import { useEffect, useState } from 'react'
import { CheckCircle, XCircle, CalendarDays } from 'lucide-react'
import { teacherApi } from '../../api/teacher'
import PageHeader from '../../components/shared/PageHeader'
import Button from '../../components/ui/Button'
import Badge from '../../components/ui/Badge'
import EmptyState from '../../components/ui/EmptyState'
import Spinner from '../../components/ui/Spinner'
import Modal from '../../components/ui/Modal'
import toast from 'react-hot-toast'

export default function TeacherLeaveReview() {
  const [leaves, setLeaves] = useState([])
  const [loading, setLoading] = useState(true)
  const [reviewing, setReviewing] = useState(null)
  const [notes, setNotes] = useState('')

  const load = () =>
    teacherApi.getPendingLeaves()
      .then(setLeaves)
      .catch(() => toast.error('Failed to load leave requests'))
      .finally(() => setLoading(false))

  useEffect(() => { load() }, [])

  const handleReview = async (leaveId, decision) => {
    try {
      await teacherApi.reviewLeave(leaveId, decision, notes)
      toast.success(`Leave ${decision.toLowerCase()}`)
      setReviewing(null)
      setNotes('')
      load()
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to review leave')
    }
  }

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>

  return (
    <div className="animate-fade-in max-w-2xl mx-auto">
      <PageHeader title="Leave Review" subtitle="Approve or reject student leave requests" />

      {leaves.length === 0 ? (
        <EmptyState icon={CalendarDays} title="No pending requests"
          description="All leave requests have been reviewed."
        />
      ) : (
        <div className="space-y-3">
          {leaves.map(lr => (
            <div key={lr.id} className="card">
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 rounded-xl bg-amber-100 flex items-center justify-center shrink-0">
                  <CalendarDays size={18} className="text-amber-600" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">
                    {lr.studentName || 'Student'} · {lr.leaveType}
                  </p>
                  <p className="text-xs text-gray-500">
                    {lr.courseName || '—'} · {lr.fromDate} to {lr.toDate}
                  </p>
                  <p className="text-xs text-gray-500">Roll: {lr.studentRollNumber || '—'}</p>
                  {lr.reason && <p className="text-xs text-gray-600 mt-1">{lr.reason}</p>}
                </div>
                <Badge variant="yellow">PENDING</Badge>
              </div>
              <div className="flex gap-2 mt-3 ml-[52px]">
                <Button variant="primary" size="xs" onClick={() => setReviewing(lr)}>
                  <CheckCircle size={13} /> Review
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal open={!!reviewing} onClose={() => { setReviewing(null); setNotes('') }} title="Review Leave Request" size="sm">
        {reviewing && (
          <div className="space-y-4">
            <div className="text-sm space-y-1">
              <p><span className="text-gray-500">Student:</span> {reviewing.studentName}</p>
              <p><span className="text-gray-500">Course:</span> {reviewing.courseName}</p>
              <p><span className="text-gray-500">Type:</span> {reviewing.leaveType}</p>
              <p><span className="text-gray-500">Dates:</span> {reviewing.fromDate} to {reviewing.toDate}</p>
              <p><span className="text-gray-500">Reason:</span> {reviewing.reason}</p>
            </div>
            <textarea
              className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary-500 focus:border-transparent resize-none"
              rows={3} placeholder="Review notes (optional)" value={notes}
              onChange={e => setNotes(e.target.value)} />
            <div className="flex gap-3">
              <Button variant="danger" className="flex-1" onClick={() => handleReview(reviewing.id, 'REJECTED')}>
                <XCircle size={14} /> Reject
              </Button>
              <Button variant="primary" className="flex-1" onClick={() => handleReview(reviewing.id, 'APPROVED')}>
                <CheckCircle size={14} /> Approve
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  )
}
