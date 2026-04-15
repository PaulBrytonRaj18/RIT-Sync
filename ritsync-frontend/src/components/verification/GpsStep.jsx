import { useEffect, useState } from 'react'
import { MapPin, CheckCircle, XCircle, RefreshCw } from 'lucide-react'
import { getCurrentPosition, haversineDistance } from '../../utils/geo'
import Spinner from '../ui/Spinner'
import Button from '../ui/Button'

export default function GpsStep({ session, onResult }) {
  const [phase,    setPhase]    = useState('checking')
  const [position, setPosition] = useState(null)
  const [distance, setDistance] = useState(null)
  const [error,    setError]    = useState('')

  const check = async () => {
    setPhase('checking')
    setError('')
    try {
      const pos  = await getCurrentPosition()
      setPosition(pos)
      const dist = Math.round(haversineDistance(
        pos.lat, pos.lng,
        parseFloat(session.geofenceLat),
        parseFloat(session.geofenceLng)
      ))
      setDistance(dist)
      const inside = dist <= session.geofenceRadiusM
      setPhase(inside ? 'inside' : 'outside')
      setTimeout(() => onResult({ verified: inside, distance: dist, position: pos }), 800)
    } catch (e) {
      setError(e.message)
      setPhase('error')
    }
  }

  useEffect(() => { check() }, [])

  const radius = session?.geofenceRadiusM ?? 100

  return (
    <div className="space-y-4">
      <div className="text-center mb-2">
        <p className="text-sm font-medium text-gray-700">Step 2 — Location check</p>
        <p className="text-xs text-gray-400">Verifying you are within {radius}m of the classroom.</p>
      </div>

      <div className="relative bg-gradient-to-b from-blue-50 to-blue-100 rounded-2xl overflow-hidden h-48 flex flex-col items-center justify-center gap-3 border border-blue-200">
        {phase === 'checking' && (
          <>
            <Spinner size="lg" className="border-blue-200 border-t-blue-600" />
            <p className="text-sm text-blue-600">Acquiring GPS signal…</p>
          </>
        )}

        {(phase === 'inside' || phase === 'outside') && (
          <>
            <div className={`w-28 h-28 rounded-full border-4 flex items-center justify-center
              ${phase === 'inside' ? 'border-green-400 bg-green-50' : 'border-red-400 bg-red-50'}`}>
              <MapPin size={28} className={phase === 'inside' ? 'text-green-600' : 'text-red-500'} />
            </div>
            <div className="text-center">
              <p className={`text-lg font-bold ${phase === 'inside' ? 'text-green-700' : 'text-red-600'}`}>
                {distance}m away
              </p>
              <p className="text-xs text-gray-500">Allowed radius: {radius}m</p>
            </div>
          </>
        )}

        {phase === 'error' && (
          <div className="text-center px-4">
            <XCircle size={32} className="text-red-400 mx-auto mb-2" />
            <p className="text-sm text-red-600">{error}</p>
          </div>
        )}
      </div>

      {phase === 'inside' && (
        <div className="flex items-center gap-2 p-3 bg-green-50 border border-green-100 rounded-xl text-sm text-green-700">
          <CheckCircle size={15} className="shrink-0" />
          You are within the classroom geofence. Location verified.
        </div>
      )}
      {phase === 'outside' && (
        <div className="flex items-center gap-2 p-3 bg-red-50 border border-red-100 rounded-xl text-sm text-red-700">
          <XCircle size={15} className="shrink-0" />
          You are {distance}m away — {distance - radius}m outside the allowed {radius}m radius.
        </div>
      )}
      {phase === 'error' && (
        <Button variant="secondary" onClick={check} className="w-full gap-2">
          <RefreshCw size={14} /> Retry location
        </Button>
      )}
    </div>
  )
}
