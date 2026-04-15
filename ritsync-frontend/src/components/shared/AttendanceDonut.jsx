import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts'

const COLORS = { PRESENT: '#22c55e', ABSENT: '#ef4444', LATE: '#f59e0b', EXCUSED: '#3b82f6' }

export default function AttendanceDonut({ data, size = 200 }) {
  const chartData = Object.entries(data || {}).map(([name, value]) => ({ name, value }))
  const total = chartData.reduce((s, d) => s + d.value, 0)
  const presentPct = total ? Math.round(((data.PRESENT || 0) / total) * 100) : 0

  return (
    <div className="relative" style={{ width: size, height: size }}>
      <ResponsiveContainer width="100%" height="100%">
        <PieChart>
          <Pie data={chartData} cx="50%" cy="50%" innerRadius="60%" outerRadius="80%"
               dataKey="value" paddingAngle={3}>
            {chartData.map((entry) => (
              <Cell key={entry.name} fill={COLORS[entry.name] || '#9ca3af'} />
            ))}
          </Pie>
          <Tooltip formatter={(val, name) => [val, name]} />
        </PieChart>
      </ResponsiveContainer>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-2xl font-bold text-gray-900">{presentPct}%</span>
        <span className="text-xs text-gray-500">attendance</span>
      </div>
    </div>
  )
}
