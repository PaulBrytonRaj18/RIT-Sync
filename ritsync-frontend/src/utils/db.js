import { openDB } from 'idb'

const DB_NAME    = 'ritsync-db'
const DB_VERSION = 1

let _db = null

async function getDB() {
  if (_db) return _db
  _db = await openDB(DB_NAME, DB_VERSION, {
    upgrade(db) {
      // Pending attendance queue (offline-first)
      if (!db.objectStoreNames.contains('attendance_queue')) {
        const store = db.createObjectStore('attendance_queue', {
          keyPath: 'localId', autoIncrement: true
        })
        store.createIndex('by_status', 'syncStatus')
        store.createIndex('by_session', 'sessionId')
      }

      // Face descriptors cache (so we don't reload models each time)
      if (!db.objectStoreNames.contains('face_descriptors')) {
        db.createObjectStore('face_descriptors', { keyPath: 'userId' })
      }

      // Cached course schedule
      if (!db.objectStoreNames.contains('courses')) {
        db.createObjectStore('courses', { keyPath: 'id' })
      }

      // Session tokens cached for offline geofence check
      if (!db.objectStoreNames.contains('active_sessions')) {
        db.createObjectStore('active_sessions', { keyPath: 'id' })
      }
    }
  })
  return _db
}

export const db = {
  // Attendance queue
  async queueAttendance(record) {
    const db = await getDB()
    return db.add('attendance_queue', {
      ...record,
      syncStatus: 'pending',
      createdAt: new Date().toISOString(),
    })
  },

  async getPendingRecords() {
    const db = await getDB()
    return db.getAllFromIndex('attendance_queue', 'by_status', 'pending')
  },

  async getPendingCount() {
    const records = await this.getPendingRecords()
    return records.length
  },

  async markSynced(localId) {
    const db = await getDB()
    const tx = db.transaction('attendance_queue', 'readwrite')
    const store = tx.objectStore('attendance_queue')
    const item = await store.get(localId)
    if (item) {
      item.syncStatus = 'synced'
      item.syncedAt = new Date().toISOString()
      await store.put(item)
    }
    await tx.done
  },

  async markFailed(localId, error) {
    const db = await getDB()
    const tx = db.transaction('attendance_queue', 'readwrite')
    const store = tx.objectStore('attendance_queue')
    const item = await store.get(localId)
    if (item) {
      item.syncStatus = 'failed'
      item.error = error
      item.retryCount = (item.retryCount || 0) + 1
      await store.put(item)
    }
    await tx.done
  },

  // Face descriptors
  async saveFaceDescriptor(userId, descriptor) {
    const db = await getDB()
    return db.put('face_descriptors', {
      userId,
      descriptor: Array.from(descriptor), // Float32Array → plain array for IDB
      savedAt: new Date().toISOString(),
    })
  },

  async getFaceDescriptor(userId) {
    const db = await getDB()
    const record = await db.get('face_descriptors', userId)
    if (!record) return null
    return new Float32Array(record.descriptor)
  },

  // Active sessions
  async saveSession(session) {
    const db = await getDB()
    return db.put('active_sessions', session)
  },

  async getSession(id) {
    const db = await getDB()
    return db.get('active_sessions', id)
  },

  // Courses
  async saveCourses(courses) {
    const db = await getDB()
    const tx = db.transaction('courses', 'readwrite')
    for (const c of courses) await tx.store.put(c)
    await tx.done
  },

  async getCourses() {
    const db = await getDB()
    return db.getAll('courses')
  },
}
