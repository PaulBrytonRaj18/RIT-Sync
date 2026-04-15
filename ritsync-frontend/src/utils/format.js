import { format, formatDistance, parseISO } from 'date-fns'

export const fmt = {
  date:     (d) => format(typeof d === 'string' ? parseISO(d) : d, 'MMM d, yyyy'),
  time:     (d) => format(typeof d === 'string' ? parseISO(d) : d, 'h:mm a'),
  datetime: (d) => format(typeof d === 'string' ? parseISO(d) : d, 'MMM d, yyyy h:mm a'),
  relative: (d) => formatDistance(typeof d === 'string' ? parseISO(d) : d, new Date(), { addSuffix: true }),
  percent:  (n, d) => (d === 0 ? '0%' : ((n / d) * 100).toFixed(1) + '%'),
  pct:      (v)    => v.toFixed(1) + '%',
}

export function attendanceBadgeClass(pct) {
  if (pct >= 90) return 'badge-green'
  if (pct >= 75) return 'badge-yellow'
  return 'badge-red'
}

export function roleLabel(role) {
  return { STUDENT: 'Student', TEACHER: 'Teacher', ADMIN: 'Admin' }[role] ?? role
}

export function statusBadgeClass(status) {
  return {
    PRESENT: 'badge-green',
    ABSENT:  'badge-red',
    LATE:    'badge-yellow',
    EXCUSED: 'badge-blue',
  }[status] ?? 'badge-gray'
}
