import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, AlertTriangle, Search, ChevronRight } from 'lucide-react'
import { teacherApi } from '../../api/teacher'
import PageHeader from '../../components/shared/PageHeader'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import Input from '../../components/ui/Input'
import { fmt } from '../../utils/format'

export default function SubjectAttendanceView() {
  const { courseId } = useParams()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [selectedStudent, setSelectedStudent] = useState(null)
  const [studentHistory, setStudentHistory] = useState([])
  const [historyLoading, setHistoryLoading] = useState(false)

  useEffect(() => {
    teacherApi.getSubjectAttendance(courseId)
      .then(setData)
      .finally(() => setLoading(false))
  }, [courseId])

  const viewHistory = async (studentId) => {
    setSelectedStudent(studentId)
    setHistoryLoading(true)
    try {
      const records = await teacherApi.getStudentHistory(courseId, studentId)
      setStudentHistory(records)
    } finally {
      setHistoryLoading(false)
    }
  }

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>
  if (!data) return null

  const filtered = data.students.filter(s =>
    s.studentName.toLowerCase().includes(search.toLowerCase()) ||
    (s.rollNumber && s.rollNumber.toLowerCase().includes(search.toLowerCase()))
  )

  const atRiskCount = data.students.filter(s => s.atRisk).length

  return (
    <div className="animate-fade-in">
      <PageHeader
        title={data.courseName}
        subtitle={`${data.courseCode} · ${data.totalSessions} sessions conducted`}
      />

      {/* Summary */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="stat-card">
          <p className="stat-value">{data.students.length}</p>
          <p className="stat-label">Students</p>
        </div>
        <div className="stat-card">
          <p className="stat-value">{data.totalSessions}</p>
          <p className="stat-label">Sessions</p>
        </div>
        <div className="stat-card">
          <p className="stat-value text-red-500">{atRiskCount}</p>
          <p className="stat-label">At risk (&lt;75%)</p>
        </div>
      </div>

      {/* Search */}
      <div className="mb-4">
        <Input
          placeholder="Search students..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="pl-10"
        />
      </div>

      {/* Student list */}
      <div className="card divide-y divide-gray-50">
        {filtered.length === 0 ? (
          <p className="text-center text-sm text-gray-500 py-10">No students found</p>
        ) : (
          filtered.map(s => (
            <div key={s.studentId}>
              <button onClick={() => viewHistory(s.studentId)}
                className="w-full flex items-center gap-3 py-3 hover:bg-gray-50 px-1 transition-colors text-left">
                <div className="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center text-primary-700 text-xs font-semibold shrink-0">
                  {s.studentName[0]}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">{s.studentName}</p>
                  <p className="text-xs text-gray-400">{s.rollNumber || '—'}</p>
                </div>
                <div className="text-right">
                  <p className={`text-sm font-semibold ${s.percentage >= 75 ? 'text-green-600' : 'text-red-500'}`}>
                    {fmt.pct(s.percentage)}
                  </p>
                  <p className="text-[10px] text-gray-400">{s.present}P / {s.absent}A / {s.late}L</p>
                </div>
                {s.atRisk && <AlertTriangle size={14} className="text-red-400 shrink-0" />}
                <ChevronRight size={14} className="text-gray-300 shrink-0" />
              </button>

              {/* Expanded history */}
              {selectedStudent === s.studentId && (
                <div className="bg-gray-50 rounded-xl mx-2 mb-2 p-3">
                  {historyLoading ? (
                    <div className="flex justify-center py-4"><Spinner size="sm" /></div>
                  ) : (
                    <div className="space-y-1.5 max-h-48 overflow-y-auto">
                      {studentHistory.length === 0 ? (
                        <p className="text-xs text-gray-500 text-center py-2">No records found</p>
                      ) : (
                        studentHistory.map(r => (
                          <div key={r.id} className="flex items-center justify-between text-xs py-1.5 px-2 bg-white rounded-lg">
                            <span className="text-gray-600">{r.markedAt ? fmt.date(r.markedAt) : '—'}</span>
                            <span className="text-gray-500">{r.sessionTitle || 'Session'}</span>
                            <Badge variant={
                              r.status === 'PRESENT' ? 'green' : r.status === 'LATE' ? 'yellow' : 'red'
                            }>{r.status}</Badge>
                          </div>
                        ))
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  )
}
