import { create } from 'zustand'
import { persist } from 'zustand/middleware'

const useAuthStore = create(
  persist(
    (set, get) => ({
      user:            null,
      accessToken:     null,
      refreshToken:    null,
      isAuthenticated: false,

      login: (authResponse) => set({
        user:            authResponse.user,
        accessToken:     authResponse.accessToken,
        refreshToken:    authResponse.refreshToken,
        isAuthenticated: true,
      }),

      logout: () => set({ user: null, accessToken: null, refreshToken: null, isAuthenticated: false }),

      updateUser:   (user)   => set({ user }),
      updateTokens: (access, refresh) =>
        set({ accessToken: access, ...(refresh && { refreshToken: refresh }) }),

      getRole: () => get().user?.role ?? null,
      isRole:  (role) => get().user?.role === role,
    }),
    {
      name: 'ritsync-auth',
      partialize: (s) => ({
        user: s.user, accessToken: s.accessToken,
        refreshToken: s.refreshToken, isAuthenticated: s.isAuthenticated,
      }),
    }
  )
)

export default useAuthStore
