import { NavLink } from 'react-router-dom'
import { LayoutDashboard, BookOpen, CheckSquare, Users, Settings,
         BarChart2, CalendarCheck, ShieldCheck, LogOut } from 'lucide-react'
import useAuthStore from '../../store/authStore'
import clsx from 'clsx'

const NAV = {
  STUDENT: [
    { to: '/student',              icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/student/courses',      icon: BookOpen,        label: 'My courses' },
    { to: '/student/attendance',   icon: CheckSquare,     label: 'My attendance' },
    { to: '/student/face-enroll',  icon: ShieldCheck,     label: 'Face ID setup' },
  ],
  TEACHER: [
    { to: '/teacher',              icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/teacher/courses',      icon: BookOpen,        label: 'My courses' },
    { to: '/teacher/sessions',     icon: CalendarCheck,   label: 'Sessions' },
    { to: '/teacher/attendance',   icon: CheckSquare,     label: 'Attendance' },
    { to: '/teacher/reports',      icon: BarChart2,       label: 'Reports' },
  ],
  ADMIN: [
    { to: '/admin',                icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/admin/users',          icon: Users,           label: 'Users' },
    { to: '/admin/courses',        icon: BookOpen,        label: 'Courses' },
    { to: '/admin/attendance',     icon: CheckSquare,     label: 'Attendance' },
    { to: '/admin/analytics',      icon: BarChart2,       label: 'Analytics' },
    { to: '/admin/audit',          icon: ShieldCheck,     label: 'Audit log' },
  ],
}

export default function Sidebar({ onClose }) {
  const { user, logout, getRole } = useAuthStore()
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
        <div className="sidebar-section">
          <p className="sidebar-title">Menu</p>
          {links.map(({ to, icon: Icon, label }) => (
            <NavLink key={to} to={to} end={to.split('/').length === 2}
              className={({ isActive }) => clsx('nav-link', isActive && 'active')}
              onClick={onClose}>
              <Icon size={17} />
              {label}
            </NavLink>
          ))}
        </div>
        <div className="sidebar-section">
          <p className="sidebar-title">Account</p>
          <NavLink to="/settings" className={({ isActive }) => clsx('nav-link', isActive && 'active')} onClick={onClose}>
            <Settings size={17} /> Settings
          </NavLink>
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
            <p className="text-xs text-gray-500 truncate">{user?.role}</p>
          </div>
          <button onClick={logout} className="p-1.5 hover:bg-gray-100 rounded-lg transition-colors" title="Sign out">
            <LogOut size={15} className="text-gray-500" />
          </button>
        </div>
      </div>
    </aside>
  )
}
