import { create } from 'zustand'

let syncTriggerCallback = null

export function registerSyncTrigger(callback) {
  syncTriggerCallback = callback
}

const useOfflineStore = create((set, get) => {
  if (typeof window !== 'undefined') {
    window.addEventListener('online', async () => {
      set({ isOnline: true })
      if (syncTriggerCallback) {
        syncTriggerCallback()
      }
    })
    window.addEventListener('offline', () => set({ isOnline: false }))
  }
  
  return {
    isOnline:    typeof navigator !== 'undefined' ? navigator.onLine : true,
    syncPending: 0,
    lastSynced:  null,
    setOnline:       (v)  => set({ isOnline: v }),
    setSyncPending:  (n)  => set({ syncPending: n }),
    setLastSynced:   ()   => set({ lastSynced: new Date().toISOString() }),
    incrementPending: ()  => set(s => ({ syncPending: s.syncPending + 1 })),
    decrementPending: ()  => set(s => ({ syncPending: Math.max(0, s.syncPending - 1) })),
    triggerSync: () => {
      if (syncTriggerCallback) {
        syncTriggerCallback()
      }
    },
  }
})

export default useOfflineStore
