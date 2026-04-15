import { useEffect, useState } from 'react'
import { BarChart2 } from 'lucide-react'
import { usersApi } from '../../api/users'
import PageHeader from '../../components/shared/PageHeader'
import EmptyState from '../../components/ui/EmptyState'
import {
  LineChart, Line, BarChart, Bar, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer, Cell
} from 'recharts'

const WEEK_DATA = [
  { day: 'Mon', attendance: 88 },
  { day: 'Tue', attendance: 82 },
  { day: 'Wed', attendance: 91 },
  { day: 'Thu', attendance: 79 },
  { day: 'Fri', attendance: 85 },
]
const DEPT_TREND = [
  { month: 'Jan', CSE: 88, ECE: 82, MECH: 76 },
  { month: 'Feb', CSE: 85, ECE: 84, MECH: 79 },
  { month: 'Mar', CSE: 90, ECE: 87, MECH: 81 },
  { month: 'Apr', CSE: 88, ECE: 83, MECH: 77 },
]

export default function AdminAnalyticsPage() {
  return (
    <div className="animate-fade-in">
      <PageHeader title="Analytics" subtitle="Department-wide attendance trends and insights" />

      <div className="grid gap-5">
        <div className="card">
          <h2 className="font-semibold text-gray-900 mb-1">Weekly attendance rate</h2>
          <p className="text-xs text-gray-500 mb-4">Institution average this week</p>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={WEEK_DATA}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="day" tick={{ fontSize: 12 }} />
              <YAxis domain={[60, 100]} tick={{ fontSize: 12 }} unit="%" />
              <Tooltip formatter={v => [v + '%', 'Attendance']} />
              <Bar dataKey="attendance" radius={[4,4,0,0]}>
                {WEEK_DATA.map((d, i) => <Cell key={i} fill={d.attendance >= 85 ? '#22c55e' : d.attendance >= 75 ? '#f59e0b' : '#ef4444'} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className="card">
          <h2 className="font-semibold text-gray-900 mb-1">Department trends</h2>
          <p className="text-xs text-gray-500 mb-4">Monthly comparison by department</p>
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={DEPT_TREND}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
              <XAxis dataKey="month" tick={{ fontSize: 12 }} />
              <YAxis domain={[60, 100]} tick={{ fontSize: 12 }} unit="%" />
              <Tooltip formatter={v => [v + '%']} />
              <Legend />
              <Line type="monotone" dataKey="CSE"  stroke="#6366f1" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="ECE"  stroke="#22c55e" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="MECH" stroke="#f59e0b" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>

        <div className="card p-4 bg-amber-50 border border-amber-100">
          <p className="text-sm font-medium text-amber-800 mb-1">Note — sample data</p>
          <p className="text-xs text-amber-700">
            These charts display illustrative data. Live analytics will populate as sessions and attendance records are created through the system.
          </p>
        </div>
      </div>
    </div>
  )
}
