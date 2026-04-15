import { useState, useCallback } from 'react'
import { getCurrentPosition, haversineDistance, isWithinGeofence } from '../utils/geo'

export function useGeofence() {
  const [status,   setStatus]   = useState('idle')
  const [position, setPosition] = useState(null)
  const [distance, setDistance] = useState(null)

  const check = useCallback(async (fenceLat, fenceLng, radiusM) => {
    setStatus('checking')
    try {
      const pos  = await getCurrentPosition()
      setPosition(pos)
      const dist = Math.round(haversineDistance(pos.lat, pos.lng, fenceLat, fenceLng))
      setDistance(dist)
      const inside = dist <= radiusM
      setStatus(inside ? 'inside' : 'outside')
      return { inside, position: pos, distance: dist }
    } catch (err) {
      setStatus('error')
      throw err
    }
  }, [])

  return { status, position, distance, check }
}
