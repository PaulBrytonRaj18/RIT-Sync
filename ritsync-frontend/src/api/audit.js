import { apiClient } from './client'

export const auditApi = {
  getAuditLogs: async (page = 0, size = 50) => {
    const res = await apiClient.get('/audit-log', { params: { page, size } })
    return res.data.data
  },
}
