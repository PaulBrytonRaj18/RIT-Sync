import { apiClient } from './client'
import { enqueueSyncItem, saveAttendanceDraft } from '../offline/db'

export const attendanceApi = {
  markAttendance: async (payload, isOffline = false) => {
    if (isOffline) {
      await saveAttendanceDraft(body)
      await enqueueSyncItem({
        method: 'POST',
        url:    '/attendance/mark',
        data:   JSON.stringify(body),
        headers: { 'Content-Type': 'application/json' },
      })
      return { offline: true }
    }

    const res = await apiClient.post('/attendance/mark', body)
    return res.data.data
  },

  getSessionAttendance: async (sessionId) => {
    const res = await apiClient.get(`/attendance/session/${sessionId}`)
    return res.data.data
  },

  getStudentStats: async (studentId, courseId) => {
    const res = await apiClient.get(`/attendance/stats/${studentId}/course/${courseId}`)
    return res.data.data
  },

  manualOverride: async (sessionId, studentId, data) => {
    const res = await apiClient.put(
      `/attendance/override/${sessionId}/student/${studentId}`,
      data
    )
    return res.data.data
  },
}
