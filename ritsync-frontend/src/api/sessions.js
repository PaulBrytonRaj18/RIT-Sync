import { apiClient } from './client'
import { cacheSessions } from '../offline/db'

export const sessionsApi = {
  getCourseSessions: async (courseId) => {
    const res = await apiClient.get(`/sessions/course/${courseId}`)
    const sessions = res.data.data
    await cacheSessions(sessions)
    return sessions
  },

  createSession: async (data) => {
    const res = await apiClient.post('/sessions', data)
    return res.data.data
  },

  openSession: async (sessionId) => {
    const res = await apiClient.patch(`/sessions/${sessionId}/open`)
    return res.data.data
  },

  closeSession: async (sessionId) => {
    const res = await apiClient.patch(`/sessions/${sessionId}/close`)
    return res.data.data
  },

  getOpenSessions: async () => {
    const res = await apiClient.get('/sessions/open')
    return res.data.data
  },
}
