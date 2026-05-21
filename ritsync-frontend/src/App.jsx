import { useEffect } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import useAuthStore from './store/authStore'
import useOfflineStore, { registerSyncTrigger } from './store/offlineStore'
import AppShell from './components/layout/AppShell'
import { processSyncQueue } from './offline/syncEngine'

// Auth
import LoginPage    from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'

// Student
import StudentDashboard    from './pages/student/StudentDashboard'
import StudentCoursesPage  from './pages/student/StudentCoursesPage'
import StudentAttendancePage from './pages/student/StudentAttendancePage'
import FaceEnrollPage      from './pages/student/FaceEnrollPage'
import MarkAttendancePage  from './pages/student/MarkAttendancePage'
import DeviceManagementPage from './pages/student/DeviceManagementPage'
import ProfilePage from './pages/student/ProfilePage'
import StudentLeaveRequest from './pages/student/StudentLeaveRequest'

// Teacher
import TeacherDashboard    from './pages/teacher/TeacherDashboard'
import SubjectTeacherDashboard from './pages/teacher/SubjectTeacherDashboard'
import ClassTeacherDashboard from './pages/teacher/ClassTeacherDashboard'
import TeacherSessionsPage from './pages/teacher/TeacherSessionsPage'
import TeacherAttendancePage from './pages/teacher/TeacherAttendancePage'
import AttendanceMarkingPage from './pages/teacher/AttendanceMarkingPage'
import HeatmapDashboard from './pages/teacher/HeatmapDashboard'
import SubjectAttendanceView from './pages/teacher/SubjectAttendanceView'
import DefaulterManagement from './pages/teacher/DefaulterManagement'
import TeacherLeaveReview from './pages/teacher/TeacherLeaveReview'

// Admin
import AdminDashboard    from './pages/admin/AdminDashboard'
import AdminUsersPage    from './pages/admin/AdminUsersPage'
import AdminAnalyticsPage from './pages/admin/AdminAnalyticsPage'
import AdminAuditPage    from './pages/admin/AdminAuditPage'

function RequireAuth({ children, roles }) {
  const { isAuthenticated, user } = useAuthStore()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (roles && !roles.includes(user?.role)) return <Navigate to={roleLanding(user?.role)} replace />
  return children
}

function GuestOnly({ children }) {
  const { isAuthenticated, user } = useAuthStore()
  if (isAuthenticated) return <Navigate to={roleLanding(user?.role)} replace />
  return children
}

function roleLanding(role) {
  return { STUDENT: '/student', TEACHER: '/teacher', ADMIN: '/admin' }[role] ?? '/login'
}

async function handleSync() {
  try {
    const result = await processSyncQueue()
    if (result.succeeded > 0) {
      console.log(`[RIT-Sync] Synced ${result.succeeded} items successfully`)
    }
  } catch (err) {
    console.error('[RIT-Sync] Sync failed:', err)
  }
}

function RootRedirect() {
  const { isAuthenticated, user } = useAuthStore()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <Navigate to={roleLanding(user?.role)} replace />
}

export default function App() {
  const { isAuthenticated } = useAuthStore()
  const triggerSync = useOfflineStore(s => s.triggerSync)

  useEffect(() => {
    registerSyncTrigger(handleSync)
    
    if (isAuthenticated) {
      handleSync()
    }
  }, [isAuthenticated])

  return (
    <Routes>
      {/* Public */}
      <Route path="/login"    element={<GuestOnly><LoginPage /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><RegisterPage /></GuestOnly>} />
      <Route path="/"         element={<RootRedirect />} />

      {/* Student */}
      <Route element={<RequireAuth roles={['STUDENT']}><AppShell /></RequireAuth>}>
        <Route path="/student"                        element={<StudentDashboard />} />
        <Route path="/student/courses"                element={<StudentCoursesPage />} />
        <Route path="/student/attendance"             element={<StudentAttendancePage />} />
        <Route path="/student/face-enroll"            element={<FaceEnrollPage />} />
        <Route path="/student/devices"               element={<DeviceManagementPage />} />
        <Route path="/student/profile"               element={<ProfilePage />} />
        <Route path="/student/leave"                 element={<StudentLeaveRequest />} />
        <Route path="/student/mark/:sessionId"        element={<MarkAttendancePage />} />
      </Route>

       {/* Teacher */}
       <Route element={<RequireAuth roles={['TEACHER','ADMIN']}><AppShell /></RequireAuth>}>
         <Route path="/teacher"                        element={<TeacherDashboard />} />
         <Route path="/teacher/subject"                element={<SubjectTeacherDashboard />} />
         <Route path="/teacher/class"                  element={<ClassTeacherDashboard />} />
         <Route path="/teacher/courses"                element={<StudentCoursesPage />} />
         <Route path="/teacher/sessions"               element={<TeacherSessionsPage />} />
         <Route path="/teacher/sessions/new"           element={<TeacherSessionsPage />} />
         <Route path="/teacher/attendance/:sessionId"  element={<TeacherAttendancePage />} />
         <Route path="/teacher/mark/:sessionId"        element={<AttendanceMarkingPage />} />
         <Route path="/teacher/heatmap/:sessionId"      element={<HeatmapDashboard />} />
         <Route path="/teacher/subject-attendance/:courseId" element={<SubjectAttendanceView />} />
         <Route path="/teacher/defaulters"             element={<DefaulterManagement />} />
        <Route path="/teacher/leave-review"          element={<TeacherLeaveReview />} />
       </Route>

      {/* Admin */}
      <Route element={<RequireAuth roles={['ADMIN']}><AppShell /></RequireAuth>}>
        <Route path="/admin"            element={<AdminDashboard />} />
        <Route path="/admin/users"      element={<AdminUsersPage />} />
        <Route path="/admin/analytics"  element={<AdminAnalyticsPage />} />
        <Route path="/admin/courses"    element={<StudentCoursesPage />} />
        <Route path="/admin/attendance" element={<TeacherAttendancePage sessionId="0" />} />
        <Route path="/admin/audit"     element={<AdminAuditPage />} />
      </Route>

      {/* Catch-all */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
