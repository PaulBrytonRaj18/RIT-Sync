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
}
