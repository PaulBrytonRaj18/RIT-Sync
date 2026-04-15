import { apiClient } from './client'

export const devicesApi = {
  getMyDevices: async () => {
    const res = await apiClient.get('/devices')
    return res.data.data
  },

  sendVerificationEmail: async (deviceId) => {
    const res = await apiClient.post(`/devices/verify/${deviceId}`)
    return res.data.data
  },

  confirmDevice: async (token) => {
    const res = await apiClient.get(`/devices/confirm`, { params: { token } })
    return res.data.data
  },

  revokeDevice: async (deviceId) => {
    const res = await apiClient.delete(`/devices/${deviceId}`)
    return res.data.data
  },
}
