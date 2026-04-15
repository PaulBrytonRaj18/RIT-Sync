import { create } from 'zustand'
import { db } from '../utils/db'

export const useAttendanceStore = create((set, get) => ({
  // Current session being marked
  activeSession: null,
  // Verification states
  verifications: {
    face:   'pending', // pending | checking | passed | failed
    gps:    'pending',
    device: 'pending',
  },
  // Offline queue count
  pendingSync: 0,
  // Recent records
  records: [],

  // Start an attendance session
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

  // Queue record for background sync (offline-first)
  queueRecord: async (record) => {
    await db.queueAttendance(record)
    const count = await db.getPendingCount()
    set({ pendingSync: count })
  },

  // Load pending count from IndexedDB
  loadPendingCount: async () => {
    const count = await db.getPendingCount()
    set({ pendingSync: count })
  },

  setRecords: (records) => set({ records }),
}))
