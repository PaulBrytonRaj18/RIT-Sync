import { useEffect } from 'react'
import { processSyncQueue } from '../offline/syncEngine'
import useOfflineStore from '../store/offlineStore'
import { getSyncQueueCount } from '../offline/db'
import toast from 'react-hot-toast'

export function useOnlineStatus() {
  const { setOnline, setSyncPending, setLastSynced, isOnline } = useOfflineStore()

  useEffect(() => {
    const updateCount = async () => {
      const count = await getSyncQueueCount()
      setSyncPending(count)
    }
    updateCount()

    const handleOnline = async () => {
      setOnline(true)
      toast.success('Back online — syncing pending records…')
      const result = await processSyncQueue()
      setSyncPending(result.remaining)
      if (result.succeeded > 0) {
        setLastSynced()
        toast.success(`Synced ${result.succeeded} attendance record${result.succeeded > 1 ? 's' : ''}`)
      }
    }

    const handleOffline = () => {
      setOnline(false)
      toast.error('You are offline — attendance will sync when reconnected', { duration: 6000 })
    }

    window.addEventListener('online',  handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online',  handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [setOnline, setSyncPending, setLastSynced])

  return isOnline
}
