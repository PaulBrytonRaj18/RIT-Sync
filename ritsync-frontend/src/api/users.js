import { apiClient } from './client'

export const usersApi = {
  getMe: async () => {
    const res = await apiClient.get('/users/me')
    return res.data.data
  },

  getById: async (id) => {
    const res = await apiClient.get(`/users/${id}`)
    return res.data.data
  },

  getByRole: async (role) => {
    const res = await apiClient.get(`/users/role/${role}`)
    return res.data.data
  },

  enrollFaceDescriptor: async (descriptor) => {
    const res = await apiClient.post('/users/me/face', { descriptor })
    return res.data.data
  },

  getFaceEnrollmentStatus: async () => {
    const res = await apiClient.get('/users/me/face/status')
    return res.data.data
  },
}
