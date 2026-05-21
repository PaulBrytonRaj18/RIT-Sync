import { create } from 'zustand'
import { persist } from 'zustand/middleware'

const useAuthStore = create(
  persist(
    (set, get) => ({
      user:            null,
      accessToken:     null,
      refreshToken:    null,
      isAuthenticated: false,
      teacherRole:     null, // 'subject_teacher' | 'class_teacher' | null

      login: (authResponse, teacherRole) => set({
        user:            authResponse.user,
        accessToken:     authResponse.accessToken,
        refreshToken:    authResponse.refreshToken,
        isAuthenticated: true,
        teacherRole:     teacherRole || null,
      }),

      logout: () => set({
        user: null, accessToken: null, refreshToken: null,
        isAuthenticated: false, teacherRole: null,
      }),

      setTeacherRole: (role) => set({ teacherRole: role }),

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
        teacherRole: s.teacherRole,
      }),
    }
  )
)

export default useAuthStore
