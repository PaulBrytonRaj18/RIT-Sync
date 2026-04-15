import { openDB } from 'idb'

const DB_NAME = 'ritsync-offline'
const DB_VERSION = 1
let dbPromise = null

export function getDB() {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('syncQueue')) {
          const sq = db.createObjectStore('syncQueue', { keyPath: 'id', autoIncrement: true })
          sq.createIndex('by-createdAt', 'createdAt')
        }
        if (!db.objectStoreNames.contains('sessions'))
          db.createObjectStore('sessions', { keyPath: 'id' })
        if (!db.objectStoreNames.contains('attendanceDrafts')) {
          const ad = db.createObjectStore('attendanceDrafts', { keyPath: 'localId', autoIncrement: true })
          ad.createIndex('by-sessionId', 'sessionId')
        }
        if (!db.objectStoreNames.contains('courses'))
          db.createObjectStore('courses', { keyPath: 'id' })
        if (!db.objectStoreNames.contains('profile'))
          db.createObjectStore('profile', { keyPath: 'key' })
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
  return db.add('attendanceDrafts', { ...draft, savedAt: Date.now() })
}
export async function getAllAttendanceDrafts() {
  const db = await getDB()
  return db.getAll('attendanceDrafts')
}
export async function deleteAttendanceDraft(localId) {
  const db = await getDB()
  return db.delete('attendanceDrafts', localId)
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
