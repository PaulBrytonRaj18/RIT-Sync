import { useEffect } from 'react'
import { Monitor } from 'lucide-react'
import { getDeviceFingerprint, getDeviceName } from '../../utils/device'
import Spinner from '../ui/Spinner'

export default function DeviceStep({ onResult }) {
  useEffect(() => {
    const fp   = getDeviceFingerprint()
    const name = getDeviceName()
    const timer = setTimeout(() => {
      onResult({ verified: true, fingerprint: fp, deviceName: name })
    }, 900)
    return () => clearTimeout(timer)
  }, [onResult])

  return (
    <div className="space-y-4">
      <div className="text-center mb-2">
        <p className="text-sm font-medium text-gray-700">Step 3 — Device check</p>
        <p className="text-xs text-gray-400">Verifying this is your registered device.</p>
      </div>

      <div className="flex flex-col items-center gap-4 py-8">
        <div className="relative">
          <div className="w-20 h-20 bg-blue-50 rounded-2xl flex items-center justify-center border border-blue-100">
            <Monitor size={36} className="text-blue-600" />
          </div>
          <div className="absolute -bottom-1 -right-1 w-7 h-7 bg-white rounded-full flex items-center justify-center border border-gray-100 shadow-sm">
            <Spinner size="sm" />
          </div>
        </div>
        <div className="text-center">
          <p className="text-sm font-medium text-gray-800">{getDeviceName()}</p>
          <p className="text-xs text-gray-400 font-mono mt-0.5">{getDeviceFingerprint()}</p>
        </div>
      </div>
    </div>
  )
}
