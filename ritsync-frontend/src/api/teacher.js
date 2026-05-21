import { apiClient } from './client'

export const teacherApi = {
  getDashboard: async () => {
    const res = await apiClient.get('/teacher/dashboard')
    return res.data.data
  },

  getSubjectAttendance: async (courseId) => {
    const res = await apiClient.get(`/teacher/courses/${courseId}/attendance`)
    return res.data.data
  },

  getStudentHistory: async (courseId, studentId) => {
    const res = await apiClient.get(`/teacher/courses/${courseId}/students/${studentId}/history`)
    return res.data.data
  },

  getClassDashboard: async () => {
    const res = await apiClient.get('/teacher/class-dashboard')
    return res.data.data
  },

  requestLeave: async ({ courseId, leaveType, reason, fromDate, toDate }) => {
    const res = await apiClient.post('/teacher/leaves', null, {
      params: { courseId, leaveType, reason, fromDate, toDate }
    })
    return res.data.data
  },

  getPendingLeaves: async () => {
    const res = await apiClient.get('/teacher/leaves/pending')
    return res.data.data
  },

  reviewLeave: async (leaveId, decision, notes) => {
    const res = await apiClient.put(`/teacher/leaves/${leaveId}/review`, null, {
      params: { decision, notes }
    })
    return res.data.data
  },

  getMyLeaves: async () => {
    const res = await apiClient.get('/teacher/leaves/my')
    return res.data.data
  },
}
