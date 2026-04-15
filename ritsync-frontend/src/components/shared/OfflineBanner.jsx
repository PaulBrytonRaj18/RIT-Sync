import { WifiOff, RefreshCw } from 'lucide-react'
import useOfflineStore from '../../store/offlineStore'
import { processSyncQueue } from '../../offline/syncEngine'
import { getSyncQueueCount } from '../../offline/db'
import { useState } from 'react'
import toast from 'react-hot-toast'

export default function OfflineBanner() {
  const { isOnline, syncPending, setSyncPending, setLastSynced } = useOfflineStore()
  const [syncing, setSyncing] = useState(false)

  const handleSync = async () => {
    setSyncing(true)
    try {
      const result = await processSyncQueue()
      setSyncPending(result.remaining)
      if (result.succeeded > 0) { setLastSynced(); toast.success(`Synced ${result.succeeded} records`) }
      else toast('Nothing new to sync')
    } finally {
      setSyncing(false)
    }
  }

  if (isOnline && syncPending === 0) return null

  return (
    <div className={`flex items-center gap-2 px-4 py-2 text-xs font-medium ${
      isOnline ? 'bg-amber-50 text-amber-700 border-b border-amber-100'
               : 'bg-red-50 text-red-700 border-b border-red-100'
    }`}>
      <WifiOff size={12} />
      {isOnline ? (
        <>
          <span>{syncPending} pending record{syncPending !== 1 ? 's' : ''} to sync</span>
          <button onClick={handleSync} disabled={syncing}
            className="ml-auto flex items-center gap-1 hover:underline disabled:opacity-50">
            <RefreshCw size={10} className={syncing ? 'animate-spin' : ''} />
            Sync now
          </button>
        </>
      ) : (
        <span>Offline — attendance will sync automatically when you reconnect</span>
      )}
    </div>
  )
}
