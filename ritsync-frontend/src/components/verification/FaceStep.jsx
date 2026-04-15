import { useEffect, useState, useCallback } from 'react'
import { Camera, AlertCircle, RefreshCw } from 'lucide-react'
import { useFaceVerification } from '../../hooks/useFaceVerification'
import Button from '../ui/Button'
import Spinner from '../ui/Spinner'

export default function FaceStep({ onResult, storedDescriptor }) {
  const {
    status, videoRef,
    loadModels, startCamera, stopCamera,
    captureDescriptor, verifyAgainst,
  } = useFaceVerification()

  const [phase,   setPhase]   = useState('init')
  const [error,   setError]   = useState('')
  const [countdown, setCountdown] = useState(null)
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    const init = async () => {
      try {
        setPhase('init')
        await loadModels()
        if (cancelled) return
        await startCamera()
        if (cancelled) return
        setPhase('camera')
        let c = 3
        setCountdown(c)
        const timer = setInterval(() => {
          c--
          setCountdown(c)
          if (c <= 0) {
            clearInterval(timer)
            if (!cancelled) capture()
          }
        }, 1000)
      } catch (e) {
        if (!cancelled) { setError(e.message); setPhase('error') }
      }
    }
    init()
    return () => {
      cancelled = true
      stopCamera()
    }
  }, [retryKey])

  const capture = useCallback(async () => {
    setPhase('capturing')
    setCountdown(null)
    try {
      let result
      if (storedDescriptor) {
        result = await verifyAgainst(storedDescriptor)
        onResult({ ...result, faceDescriptor: null })
      } else {
        const descriptor = await captureDescriptor()
        if (!descriptor) throw new Error('No face detected. Look directly at the camera.')
        onResult({ verified: true, confidence: 1, faceDescriptor: descriptor })
      }
      setPhase('done')
    } catch (e) {
      setError(e.message || 'Face detection failed')
      setPhase('error')
    }
  }, [storedDescriptor, verifyAgainst, captureDescriptor, onResult])

  const retry = () => {
    setError('')
    stopCamera()
    setRetryKey(k => k + 1)
  }

  return (
    <div className="space-y-4">
      <div className="text-center mb-2">
        <p className="text-sm font-medium text-gray-700">Step 1 — Face verification</p>
        <p className="text-xs text-gray-400">Look directly at the camera. Keep your face centred.</p>
      </div>

      <div className="relative bg-black rounded-2xl overflow-hidden aspect-video max-h-64 mx-auto">
        <video ref={videoRef} autoPlay muted playsInline
          className="w-full h-full object-cover" />

        {phase === 'camera' && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className="w-36 h-44 border-2 border-primary-400/70 rounded-full" />
          </div>
        )}

        {countdown !== null && countdown > 0 && (
          <div className="absolute inset-0 flex items-center justify-center bg-black/30">
            <span className="text-white text-5xl font-bold drop-shadow-lg">{countdown}</span>
          </div>
        )}

        {phase === 'capturing' && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/50 gap-2">
            <Spinner size="lg" className="border-white border-t-primary-400" />
            <p className="text-white text-sm">Analysing face…</p>
          </div>
        )}

        {(phase === 'init') && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-black gap-2">
            <Spinner size="lg" className="border-gray-700 border-t-primary-400" />
            <p className="text-gray-400 text-sm">Loading face models…</p>
          </div>
        )}
      </div>

      {phase === 'error' && (
        <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-100 rounded-xl text-sm text-red-700">
          <AlertCircle size={15} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <div className="flex gap-3">
        {phase === 'error' && (
          <Button variant="secondary" onClick={retry} className="flex-1 gap-2">
            <RefreshCw size={14} /> Try again
          </Button>
        )}
        {phase === 'camera' && (
          <Button variant="primary" onClick={capture} className="flex-1">
            Capture now
          </Button>
        )}
      </div>
    </div>
  )
}
