import { apiClient } from './client'

export const adminApi = {
  getDashboard: async () => {
    const res = await apiClient.get('/admin/dashboard')
    return res.data.data
  },

  getDailyAnalytics: async () => {
    const res = await apiClient.get('/admin/analytics/daily')
    return res.data.data
  },

  getDepartmentTrends: async () => {
    const res = await apiClient.get('/admin/analytics/departments')
    return res.data.data
  },

  // Only admins can create teacher/admin accounts (students may also self-register)
  createUser: async (data) => {
    const res = await apiClient.post('/admin/users', data)
    return res.data.data
  },

  // Lets a student enroll their Face ID again
  resetFace: async (userId) => {
    const res = await apiClient.delete(`/admin/users/${userId}/face`)
    return res.data
  },

  // Removes all device bindings; the user's next sign-in device becomes trusted
  resetDevices: async (userId) => {
    const res = await apiClient.delete(`/admin/users/${userId}/devices`)
    return res.data
  },
}
