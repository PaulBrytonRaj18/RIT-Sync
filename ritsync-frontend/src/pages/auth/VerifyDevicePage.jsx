import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CheckCircle, XCircle } from 'lucide-react'
import { devicesApi } from '../../api/devices'
import Spinner from '../../components/ui/Spinner'

/** Target of the "Verify Device" link in the email. Public: the token itself is the proof. */
export default function VerifyDevicePage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [state, setState] = useState(token ? 'loading' : 'error')
  const [message, setMessage] = useState(token ? '' : 'This link is missing its verification token.')
  const ran = useRef(false)

  useEffect(() => {
    if (!token || ran.current) return
    ran.current = true // the token is single-use; don't fire twice under StrictMode
    devicesApi.confirmDevice(token)
      .then(() => {
        setState('ok')
        setMessage('This device is now your trusted device for attendance. Any previously trusted device has been replaced.')
      })
      .catch(err => {
        setState('error')
        setMessage(err.response?.data?.message || 'This link is invalid or has expired.')
      })
  }, [token])

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface p-4">
      <div className="card w-full max-w-md text-center space-y-4">
        {state === 'loading' && (<><Spinner size="lg" /><p className="text-sm text-gray-500">Verifying device…</p></>)}
        {state === 'ok' && <CheckCircle size={48} className="mx-auto text-green-600" />}
        {state === 'error' && <XCircle size={48} className="mx-auto text-red-500" />}
        {state !== 'loading' && (
          <>
            <h1 className="text-lg font-semibold text-gray-900">
              {state === 'ok' ? 'Device verified' : 'Verification failed'}
            </h1>
            <p className="text-sm text-gray-500">{message}</p>
            <Link to="/" className="inline-block text-primary-600 text-sm font-medium hover:underline">Continue to RIT-Sync</Link>
          </>
        )}
      </div>
    </div>
  )
}
