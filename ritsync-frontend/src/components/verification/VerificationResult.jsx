import { CheckCircle, XCircle, RotateCcw, MinusCircle, CloudOff } from 'lucide-react'
import Button from '../ui/Button'

/** Shows the server's verdict for each check. "Not required" checks are shown as such, not as passes. */
export default function VerificationResult({ result, onRetry, onDone }) {
  if (!result) return null

  const {
    allVerified, queued, error,
    faceVerified, livenessVerified, gpsVerified, beaconVerified, deviceVerified,
    livenessRequired, beaconRequired, attemptsRemaining,
    message, faceDistance, gpsDistanceMetres,
  } = result

  const checks = error || queued ? [] : [
    { label: 'Face ID',        state: faceVerified ? 'pass' : 'fail',
      detail: typeof faceDistance === 'number' ? `Distance: ${faceDistance.toFixed(3)}` : '' },
    { label: 'Liveness',       state: !livenessRequired ? 'skip' : livenessVerified ? 'pass' : 'fail',
      detail: !livenessRequired ? 'Not required for this session' : '' },
    { label: 'GPS location',   state: gpsVerified ? 'pass' : 'fail',
      detail: typeof gpsDistanceMetres === 'number' ? `${Math.round(gpsDistanceMetres)}m from classroom` : '' },
    { label: 'BLE beacon',     state: !beaconRequired ? 'skip' : beaconVerified ? 'pass' : 'fail',
      detail: !beaconRequired ? 'Not required for this session' : '' },
    { label: 'Trusted device', state: deviceVerified ? 'pass' : 'fail',
      detail: deviceVerified ? '' : 'Verify this device under Devices' },
  ]

  const icon = {
    pass: <CheckCircle size={18} className="text-green-500 shrink-0" />,
    fail: <XCircle     size={18} className="text-red-400 shrink-0" />,
    skip: <MinusCircle size={18} className="text-gray-300 shrink-0" />,
  }
  const badge = {
    pass: <span className="text-xs font-semibold text-green-600">PASS</span>,
    fail: <span className="text-xs font-semibold text-red-500">FAIL</span>,
    skip: <span className="text-xs font-semibold text-gray-400">N/A</span>,
  }

  return (
    <div className="text-center space-y-5">
      <div className={`inline-flex items-center justify-center w-20 h-20 rounded-full
        ${allVerified ? 'bg-green-100' : queued ? 'bg-amber-100' : 'bg-red-100'}`}>
        {allVerified
          ? <CheckCircle size={40} className="text-green-600" />
          : queued
            ? <CloudOff size={40} className="text-amber-600" />
            : <XCircle size={40} className="text-red-500" />}
      </div>

      <div>
        <h2 className={`text-xl font-bold ${allVerified ? 'text-green-700' : queued ? 'text-amber-700' : 'text-red-600'}`}>
          {allVerified ? 'Attendance marked!' : queued ? 'Saved offline' : 'Verification failed'}
        </h2>
        <p className="text-sm text-gray-500 mt-1 max-w-sm mx-auto">{message}</p>
      </div>

      {checks.length > 0 && (
        <div className="text-left space-y-2 bg-gray-50 rounded-2xl p-4">
          {checks.map(({ label, state, detail }) => (
            <div key={label} className="flex items-center gap-3">
              {icon[state]}
              <div className="flex-1">
                <span className={`text-sm font-medium ${state === 'fail' ? 'text-gray-500' : 'text-gray-800'}`}>{label}</span>
                {detail && <span className="text-xs text-gray-400 ml-2">{detail}</span>}
              </div>
              {badge[state]}
            </div>
          ))}
        </div>
      )}

      {allVerified && (
        <div className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-100 text-green-700 rounded-full text-xs font-semibold">
          <CheckCircle size={12} /> Status: PRESENT
        </div>
      )}

      {!allVerified && !queued && typeof attemptsRemaining === 'number' && (
        <p className="text-xs text-gray-500">
          {attemptsRemaining > 0
            ? `${attemptsRemaining} attempt${attemptsRemaining === 1 ? '' : 's'} left for this session.`
            : 'No attempts left for this session. Please contact your teacher.'}
        </p>
      )}

      <div className="flex gap-3">
        {!allVerified && !queued && attemptsRemaining !== 0 && (
          <Button variant="secondary" onClick={onRetry} className="flex-1 gap-2">
            <RotateCcw size={14} /> Try again
          </Button>
        )}
        <Button variant="primary" onClick={onDone} className="flex-1">
          {allVerified || queued ? 'Done' : 'Back to dashboard'}
        </Button>
      </div>
    </div>
  )
}
