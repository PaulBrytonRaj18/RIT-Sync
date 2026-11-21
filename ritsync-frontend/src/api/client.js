import axios from 'axios'
import { enqueueSyncItem } from '../offline/db'
import useAuthStore from '../store/authStore'

const BASE_URL = '/api'

export const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// Attach the current access token from the auth store (single source of truth)
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Auth endpoints never trigger a refresh: a failed login is just a failed login.
const NO_REFRESH_PATHS = ['/auth/login', '/auth/register', '/auth/refresh']

// One refresh in flight at a time; concurrent 401s wait for the same promise.
let refreshPromise = null

async function refreshTokens() {
  const { refreshToken } = useAuthStore.getState()
  if (!refreshToken) throw new Error('No refresh token')
  // Plain axios (not apiClient) so this call can't recurse into the interceptor.
  const res = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken })
  const { accessToken, refreshToken: newRefresh, user } = res.data.data
  const store = useAuthStore.getState()
  store.updateTokens(accessToken, newRefresh)
  if (user) store.updateUser(user)
  return accessToken
}

function forceLogout() {
  useAuthStore.getState().logout()
  if (window.location.pathname !== '/login') window.location.href = '/login'
}

apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    const status = error.response?.status
    const isAuthCall = NO_REFRESH_PATHS.some(p => original?.url?.startsWith(p))

    if (status === 401 && original && !original._retry && !isAuthCall) {
      original._retry = true
      try {
        refreshPromise ??= refreshTokens().finally(() => { refreshPromise = null })
        const accessToken = await refreshPromise
        original.headers.Authorization = `Bearer ${accessToken}`
        return apiClient(original)
      } catch (refreshError) {
        forceLogout()
        return Promise.reject(refreshError)
      }
    }

    // Queue for offline retry on network errors. The Authorization header is NOT
    // stored: it would be expired by the time we sync; a fresh one is attached on replay.
    if (!error.response && original?.offlineQueueable) {
      // eslint-disable-next-line no-unused-vars
      const { Authorization, ...headers } = original.headers ?? {}
      await enqueueSyncItem({
        method:  original.method,
        url:     original.url,
        data:    original.data,
        headers,
      })
    }
    return Promise.reject(error)
  }
)

export default apiClient
