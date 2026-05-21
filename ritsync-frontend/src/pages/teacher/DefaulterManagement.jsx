import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AlertTriangle, ChevronRight, Search, Mail, BarChart2 } from 'lucide-react'
import { teacherApi } from '../../api/teacher'
import PageHeader from '../../components/shared/PageHeader'
import Spinner from '../../components/ui/Spinner'
import Badge from '../../components/ui/Badge'
import Input from '../../components/ui/Input'
import Button from '../../components/ui/Button'
import { fmt } from '../../utils/format'
import toast from 'react-hot-toast'

export default function DefaulterManagement() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [expanded, setExpanded] = useState(null)

  useEffect(() => {
    teacherApi.getClassDashboard()
      .then(setData)
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>
  if (!data) return null

  const defaulters = data.defaulters || []
  const filtered = defaulters.filter(d =>
    d.studentName.toLowerCase().includes(search.toLowerCase()) ||
    (d.rollNumber && d.rollNumber.toLowerCase().includes(search.toLowerCase()))
  )

  return (
    <div className="animate-fade-in">
      <PageHeader
        title="Defaulter Management"
        subtitle={`${defaulters.length} student${defaulters.length !== 1 ? 's' : ''} below 75% attendance threshold`}
      />

      <div className="mb-4">
        <Input
          placeholder="Search defaulters..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      {filtered.length === 0 ? (
        <div className="card text-center py-16">
          <div className="p-4 bg-green-50 rounded-2xl inline-flex mb-4">
            <AlertTriangle size={28} className="text-green-500" />
          </div>
          <p className="font-medium text-gray-700 mb-1">No defaulters</p>
          <p className="text-sm text-gray-500">All students are above the 75% attendance threshold</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map(d => (
            <div key={d.studentId} className="card">
              <button
                onClick={() => setExpanded(expanded === d.studentId ? null : d.studentId)}
                className="w-full flex items-center gap-3 text-left"
              >
                <div className="w-10 h-10 rounded-full bg-red-50 flex items-center justify-center text-red-600 text-sm font-semibold shrink-0">
                  {d.studentName[0]}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900">{d.studentName}</p>
                  <p className="text-xs text-gray-400">{d.rollNumber || '—'}</p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-semibold text-red-500">{d.overallPct.toFixed(0)}%</p>
                  <p className="text-[10px] text-gray-400">overall</p>
                </div>
                <ChevronRight size={14} className={`text-gray-300 transition-transform ${expanded === d.studentId ? 'rotate-90' : ''}`} />
              </button>

              {expanded === d.studentId && (
                <div className="mt-3 pt-3 border-t border-gray-100">
                  <p className="text-xs font-medium text-gray-500 mb-2">Course-wise breakdown</p>
                  <div className="space-y-1.5 mb-3">
                    {d.deficits?.map(def => (
                      <div key={def.courseId} className="flex items-center justify-between text-xs py-1.5 px-3 bg-red-50 rounded-lg">
                        <span className="text-gray-700">{def.courseName}</span>
                        <span className={`font-semibold ${def.percentage >= 75 ? 'text-green-600' : 'text-red-500'}`}>
                          {def.percentage.toFixed(0)}%
                        </span>
                      </div>
                    ))}
                  </div>
                  <div className="flex gap-2">
                    <Link to={`/teacher/subject-attendance/${d.deficits?.[0]?.courseId}`}>
                      <Button variant="secondary" size="sm">
                        <BarChart2 size={13} /> View details
                      </Button>
                    </Link>
                    <Button variant="secondary" size="sm" onClick={() => {
                      const subject = `Attendance Warning - ${d.studentName}`
                      const body = `Dear ${d.studentName},\n\nYour attendance has dropped to ${d.overallPct.toFixed(0)}%, which is below the minimum 75% threshold.\n\nPlease ensure you attend all upcoming classes.\n\n- RIT-Sync`
                      window.location.href = `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`
                      toast.success('Email client opened')
                    }}>
                      <Mail size={13} /> Send warning
                    </Button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
