import { getAllSyncItems, deleteSyncItem, incrementRetry, getSyncQueueCount } from './db'
import { apiClient } from '../api/client'

const MAX_RETRIES = 3

export async function processSyncQueue(onProgress) {
  const items = await getAllSyncItems()
  let succeeded = 0
  let failed = 0

  for (const item of items) {
    if (item.retryCount >= MAX_RETRIES) {
      await deleteSyncItem(item.id)
      failed++
      continue
    }
    try {
      await apiClient({
        method:  item.method,
        url:     item.url,
        data:    item.data,
        headers: item.headers,
      })
      await deleteSyncItem(item.id)
      succeeded++
      onProgress?.({ succeeded, failed, total: items.length })
    } catch (err) {
      if (err.response?.status >= 400 && err.response?.status < 500) {
        await deleteSyncItem(item.id)
        failed++
      } else {
        await incrementRetry(item.id)
      }
    }
  }
  return { succeeded, failed, remaining: await getSyncQueueCount() }
}

export async function registerSyncListeners(triggerSync) {
  if (typeof window === 'undefined') return
  window.addEventListener('online', () => {
    console.log('[RIT-Sync] Back online — triggering sync')
    triggerSync()
  })
}
