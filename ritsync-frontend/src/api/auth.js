import { apiClient } from './client'
import { getDeviceFingerprint } from '../utils/device'

export const authApi = {
  register: async (data) => {
    const res = await apiClient.post('/auth/register', data)
    return res.data.data
  },

  login: async ({ email, password }) => {
    const deviceFingerprint = getDeviceFingerprint()
    const deviceName        = navigator.userAgent.slice(0, 100)
    const res = await apiClient.post('/auth/login', {
      email, password, deviceFingerprint, deviceName
    })
    return res.data.data
  },

  refreshToken: async (refreshToken) => {
    const res = await apiClient.post('/auth/refresh', { refreshToken })
    return res.data.data
  },
}
