import { CheckCircle, XCircle, RotateCcw } from 'lucide-react'
import Button from '../ui/Button'

export default function VerificationResult({ result, onRetry, onDone }) {
  if (!result) return null

  const { allVerified, faceVerified, livenessVerified, gpsVerified, beaconVerified,
          deviceVerified, message, faceDistance, gpsDistanceMetres, status } = result

  const checks = [
    { label: 'Face ID',          ok: faceVerified,      detail: faceDistance !== undefined ? `Distance: ${faceDistance?.toFixed(3)}` : '' },
    { label: 'Liveness',         ok: livenessVerified,  detail: livenessVerified ? 'Blink detected' : '' },
    { label: 'GPS location',     ok: gpsVerified,       detail: gpsDistanceMetres !== undefined ? `${Math.round(gpsDistanceMetres)}m from classroom` : '' },
    { label: 'BLE Beacon',       ok: beaconVerified,    detail: beaconVerified ? 'Proximity verified' : '' },
    { label: 'Device binding',   ok: deviceVerified,    detail: 'Fingerprint matched' },
  ]

  return (
    <div className="text-center space-y-5">
      <div className={`inline-flex items-center justify-center w-20 h-20 rounded-full
        ${allVerified ? 'bg-green-100' : 'bg-red-100'}`}>
        {allVerified
          ? <CheckCircle size={40} className="text-green-600" />
          : <XCircle    size={40} className="text-red-500" />
        }
      </div>

      <div>
        <h2 className={`text-xl font-bold ${allVerified ? 'text-green-700' : 'text-red-600'}`}>
          {allVerified ? 'Attendance marked!' : 'Verification failed'}
        </h2>
        <p className="text-sm text-gray-500 mt-1 max-w-sm mx-auto">{message}</p>
      </div>

      <div className="text-left space-y-2 bg-gray-50 rounded-2xl p-4">
        {checks.map(({ label, ok, detail }) => (
          <div key={label} className="flex items-center gap-3">
            {ok
              ? <CheckCircle size={18} className="text-green-500 shrink-0" />
              : <XCircle     size={18} className="text-red-400 shrink-0"   />
            }
            <div className="flex-1">
              <span className={`text-sm font-medium ${ok ? 'text-gray-800' : 'text-gray-500'}`}>{label}</span>
              {detail && <span className="text-xs text-gray-400 ml-2">{detail}</span>}
            </div>
            <span className={`text-xs font-semibold ${ok ? 'text-green-600' : 'text-red-500'}`}>
              {ok ? 'PASS' : 'FAIL'}
            </span>
          </div>
        ))}
      </div>

      {allVerified && (
        <div className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-100 text-green-700 rounded-full text-xs font-semibold">
          <CheckCircle size={12} /> Status: PRESENT
        </div>
      )}

      <div className="flex gap-3">
        {!allVerified && (
          <Button variant="secondary" onClick={onRetry} className="flex-1 gap-2">
            <RotateCcw size={14} /> Try again
          </Button>
        )}
        <Button variant="primary" onClick={onDone} className="flex-1">
          {allVerified ? 'Done' : 'Back to dashboard'}
        </Button>
      </div>
    </div>
  )
}
