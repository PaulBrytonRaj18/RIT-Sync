import { create } from 'zustand'

const useOfflineStore = create((set) => ({
  isOnline:    typeof navigator !== 'undefined' ? navigator.onLine : true,
  syncPending: 0,
  lastSynced:  null,
  setOnline:       (v)  => set({ isOnline: v }),
  setSyncPending:  (n)  => set({ syncPending: n }),
  setLastSynced:   ()   => set({ lastSynced: new Date().toISOString() }),
  incrementPending: ()  => set(s => ({ syncPending: s.syncPending + 1 })),
  decrementPending: ()  => set(s => ({ syncPending: Math.max(0, s.syncPending - 1) })),
}))

export default useOfflineStore
