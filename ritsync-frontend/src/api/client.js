import axios from 'axios'
import { enqueueSyncItem } from '../offline/db'

export const apiClient = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// Attach JWT token
apiClient.interceptors.request.use((config) => {
  const raw = localStorage.getItem('ritsync-auth')
  if (raw) {
    try {
      const state = JSON.parse(raw)
      const token = state?.state?.accessToken
      if (token) config.headers.Authorization = `Bearer ${token}`
    } catch {}
  }
  return config
})

// Auto-refresh on 401 + offline queue on network failure
apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true
      try {
        const raw   = localStorage.getItem('ritsync-auth')
        const state = JSON.parse(raw)?.state
        const res   = await axios.post('/api/auth/refresh', {
          refreshToken: state.refreshToken
        })
        const { accessToken } = res.data.data
        // Patch zustand storage
        const next = JSON.parse(localStorage.getItem('ritsync-auth'))
        next.state.accessToken = accessToken
        localStorage.setItem('ritsync-auth', JSON.stringify(next))
        original.headers.Authorization = `Bearer ${accessToken}`
        return apiClient(original)
      } catch {
        localStorage.removeItem('ritsync-auth')
        window.location.href = '/login'
      }
    }
    // Queue for offline retry on network errors
    if (!error.response && original?.offlineQueueable) {
      await enqueueSyncItem({
        method:  original.method,
        url:     original.url,
        data:    original.data,
        headers: original.headers,
      })
    }
    return Promise.reject(error)
  }
)

export default apiClient
