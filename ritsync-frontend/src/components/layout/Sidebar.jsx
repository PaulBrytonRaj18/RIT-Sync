import { NavLink } from 'react-router-dom'
import { LayoutDashboard, BookOpen, CheckSquare, Users,
         CalendarCheck, ShieldCheck, LogOut, Smartphone, BarChart2,
         AlertTriangle, ClipboardList } from 'lucide-react'
import useAuthStore from '../../store/authStore'
import RoleSwitcher from './RoleSwitcher'
import clsx from 'clsx'

const NAV = {
  STUDENT: [
    { to: '/student',              icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/student/courses',      icon: BookOpen,        label: 'My courses' },
    { to: '/student/attendance',   icon: CheckSquare,     label: 'Attendance history' },
    { to: '/student/leave',       icon: CalendarCheck,    label: 'Leave request' },
    { to: '/student/face-enroll', icon: ShieldCheck,      label: 'Face ID setup' },
    { to: '/student/devices',     icon: Smartphone,       label: 'My devices' },
    { to: '/student/profile',     icon: Users,            label: 'Profile' },
  ],
  TEACHER: [
    { to: '/teacher',              icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/teacher/subject',      icon: BookOpen,        label: 'Subject dashboard' },
    { to: '/teacher/class',        icon: Users,           label: 'Class overview' },
    { to: '/teacher/sessions',     icon: CalendarCheck,    label: 'Sessions' },
    { to: '/teacher/attendance',   icon: CheckSquare,     label: 'Mark attendance' },
    { to: '/teacher/leave-review', icon: CalendarCheck,   label: 'Leave review' },
    { to: '/teacher/defaulters',   icon: AlertTriangle,   label: 'Defaulters' },
    { to: '/teacher/courses',      icon: BookOpen,        label: 'My courses' },
  ],
  ADMIN: [
    { to: '/admin',                icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/admin/users',          icon: Users,            label: 'Users' },
    { to: '/admin/courses',        icon: BookOpen,         label: 'Courses' },
    { to: '/admin/attendance',     icon: CheckSquare,      label: 'Attendance' },
    { to: '/admin/analytics',      icon: BarChart2,        label: 'Analytics' },
    { to: '/admin/audit',          icon: ClipboardList,    label: 'Audit log' },
  ],
}

export default function Sidebar({ onClose }) {
  const { user, logout, getRole, teacherRole } = useAuthStore()
  const role = getRole()
  const links = NAV[role] || []

  return (
    <aside className="flex flex-col h-full bg-white border-r border-gray-100 w-64 shrink-0">
      {/* Brand */}
      <div className="p-5 border-b border-gray-100">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 bg-primary-600 rounded-lg flex items-center justify-center">
            <CheckSquare size={16} className="text-white" />
          </div>
          <span className="text-base font-semibold text-gray-900">RIT-Sync</span>
        </div>
      </div>

      {/* Nav */}
      <nav className="flex-1 overflow-y-auto p-3 scrollbar-hide">
        {role === 'TEACHER' && (
          <div className="sidebar-section">
            <RoleSwitcher />
          </div>
        )}
        <div className="sidebar-section">
          <p className="sidebar-title">Menu</p>
          {links.map(({ to, icon: Icon, label }) => {
            if (role === 'TEACHER' && to === '/teacher' && teacherRole === 'class_teacher') return null
            if (role === 'TEACHER' && to === '/teacher/subject' && teacherRole !== 'subject_teacher') return null
            if (role === 'TEACHER' && to === '/teacher/class' && teacherRole !== 'class_teacher') return null
            return (
              <NavLink key={to} to={to}
                className={({ isActive }) => clsx('nav-link', isActive && 'active')}
                onClick={onClose}>
                <Icon size={17} />
                {label}
              </NavLink>
            )
          })}
        </div>
        <div className="sidebar-section">
          <p className="sidebar-title">Account</p>
        </div>
      </nav>

      {/* User footer */}
      <div className="p-3 border-t border-gray-100">
        <div className="flex items-center gap-3 px-2 py-2">
          <div className="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center text-primary-700 text-sm font-semibold shrink-0">
            {user?.name?.[0]?.toUpperCase() || 'U'}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-medium text-gray-900 truncate">{user?.name}</p>
            <p className="text-xs text-gray-500 truncate">
              {user?.role === 'TEACHER' && teacherRole
                ? (teacherRole === 'subject_teacher' ? 'Subject Teacher' : 'Class Teacher')
                : user?.role}
            </p>
          </div>
          <button onClick={logout} className="p-1.5 hover:bg-gray-100 rounded-lg transition-colors" title="Sign out">
            <LogOut size={15} className="text-gray-500" />
          </button>
        </div>
      </div>
    </aside>
  )
}
