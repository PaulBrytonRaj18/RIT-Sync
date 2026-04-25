import { create } from 'zustand'
import { queueAttendance, getPendingCount } from '../offline/db'

export const useAttendanceStore = create((set, get) => ({
  activeSession: null,
  verifications: {
    face:   'pending',
    gps:    'pending',
    device: 'pending',
  },
  pendingSync: 0,
  records: [],

  startSession: (session) => set({
    activeSession: session,
    verifications: { face: 'pending', gps: 'pending', device: 'pending' }
  }),

  clearSession: () => set({
    activeSession: null,
    verifications: { face: 'pending', gps: 'pending', device: 'pending' }
  }),

  setVerification: (type, status) => set(state => ({
    verifications: { ...state.verifications, [type]: status }
  })),

  isAllVerified: () => {
    const v = get().verifications
    return v.face === 'passed' && v.gps === 'passed' && v.device === 'passed'
  },

  getVerifications: () => get().verifications,

  queueRecord: async (record) => {
    await queueAttendance(record)
    const count = await getPendingCount()
    set({ pendingSync: count })
  },

  loadPendingCount: async () => {
    const count = await getPendingCount()
    set({ pendingSync: count })
  },

  setRecords: (records) => set({ records }),
}))
