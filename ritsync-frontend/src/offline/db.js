import { openDB } from 'idb'

const DB_NAME = 'ritsync-offline'
const DB_VERSION = 2
let dbPromise = null

export function getDB() {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(db, oldVersion) {
        if (oldVersion < 1) {
          if (!db.objectStoreNames.contains('syncQueue')) {
            const sq = db.createObjectStore('syncQueue', { keyPath: 'id', autoIncrement: true })
            sq.createIndex('by-createdAt', 'createdAt')
          }
          if (!db.objectStoreNames.contains('sessions'))
            db.createObjectStore('sessions', { keyPath: 'id' })
          if (!db.objectStoreNames.contains('attendanceQueue')) {
            const aq = db.createObjectStore('attendanceQueue', { keyPath: 'localId', autoIncrement: true })
            aq.createIndex('by-status', 'syncStatus')
          }
          if (!db.objectStoreNames.contains('courses'))
            db.createObjectStore('courses', { keyPath: 'id' })
          if (!db.objectStoreNames.contains('profile'))
            db.createObjectStore('profile', { keyPath: 'key' })
          if (!db.objectStoreNames.contains('faceDescriptors'))
            db.createObjectStore('faceDescriptors', { keyPath: 'userId' })
        }
      }
    })
  }
  return dbPromise
}

export async function enqueueSyncItem(item) {
  const db = await getDB()
  return db.add('syncQueue', { ...item, createdAt: Date.now(), retryCount: 0 })
}
export async function getAllSyncItems() {
  const db = await getDB()
  return db.getAllFromIndex('syncQueue', 'by-createdAt')
}
export async function deleteSyncItem(id) {
  const db = await getDB()
  return db.delete('syncQueue', id)
}
export async function incrementRetry(id) {
  const db = await getDB()
  const item = await db.get('syncQueue', id)
  if (item) { item.retryCount = (item.retryCount || 0) + 1; await db.put('syncQueue', item) }
}
export async function getSyncQueueCount() {
  const db = await getDB()
  return db.count('syncQueue')
}
export async function cacheSessions(sessions) {
  const db = await getDB()
  const tx = db.transaction('sessions', 'readwrite')
  await Promise.all(sessions.map(s => tx.store.put(s)))
  await tx.done
}
export async function getCachedSessions() {
  const db = await getDB()
  return db.getAll('sessions')
}
export async function saveAttendanceDraft(draft) {
  const db = await getDB()
  return db.add('attendanceQueue', { ...draft, savedAt: Date.now(), syncStatus: 'pending' })
}
export async function getAllAttendanceDrafts() {
  const db = await getDB()
  return db.getAll('attendanceQueue')
}
export async function deleteAttendanceDraft(localId) {
  const db = await getDB()
  return db.delete('attendanceQueue', localId)
}
export async function cacheCourses(courses) {
  const db = await getDB()
  const tx = db.transaction('courses', 'readwrite')
  await tx.store.clear()
  await Promise.all(courses.map(c => tx.store.put(c)))
  await tx.done
}
export async function getCachedCourses() {
  const db = await getDB()
  return db.getAll('courses')
}
export async function saveProfile(user) {
  const db = await getDB()
  return db.put('profile', { key: 'user', ...user })
}
export async function getProfile() {
  const db = await getDB()
  return db.get('profile', 'user')
}

export async function saveFaceDescriptor(userId, descriptor) {
  const db = await getDB()
  return db.put('faceDescriptors', {
    userId,
    descriptor: Array.from(descriptor),
    savedAt: new Date().toISOString(),
  })
}
export async function getFaceDescriptor(userId) {
  const db = await getDB()
  const record = await db.get('faceDescriptors', userId)
  if (!record) return null
  return new Float32Array(record.descriptor)
}

export async function queueAttendance(record) {
  const db = await getDB()
  return db.add('attendanceQueue', {
    ...record,
    syncStatus: 'pending',
    createdAt: new Date().toISOString(),
  })
}
export async function getPendingRecords() {
  const db = await getDB()
  return db.getAllFromIndex('attendanceQueue', 'by-status', 'pending')
}
export async function getPendingCount() {
  const records = await getPendingRecords()
  return records.length
}
export async function markSynced(localId) {
  const db = await getDB()
  const tx = db.transaction('attendanceQueue', 'readwrite')
  const store = tx.objectStore('attendanceQueue')
  const item = await store.get(localId)
  if (item) {
    item.syncStatus = 'synced'
    item.syncedAt = new Date().toISOString()
    await store.put(item)
  }
  await tx.done
}
export async function markFailed(localId, error) {
  const db = await getDB()
  const tx = db.transaction('attendanceQueue', 'readwrite')
  const store = tx.objectStore('attendanceQueue')
  const item = await store.get(localId)
  if (item) {
    item.syncStatus = 'failed'
    item.error = error
    item.retryCount = (item.retryCount || 0) + 1
    await store.put(item)
  }
  await tx.done
}
