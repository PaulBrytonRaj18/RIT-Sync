import { useEffect, useState, useCallback, useRef } from 'react'
import { Camera, AlertCircle, RefreshCw } from 'lucide-react'
import { useFaceVerification } from '../../hooks/useFaceVerification'
import { useLivenessDetection } from '../../hooks/useLivenessDetection'
import { decryptDescriptor, compareDescriptors } from '../../utils/faceEncryption'
import Button from '../ui/Button'
import Spinner from '../ui/Spinner'

export default function FaceStep({ onResult, storedDescriptor, session }) {
  const {
    status, videoRef,
    loadModels, startCamera, stopCamera,
    captureDescriptor, verifyAgainst,
  } = useFaceVerification()

  const enableLiveness = session?.enableLiveness ?? false

  const { startLivenessCheck, isLivenessChecking, livenessResult, blinkCount } =
    useLivenessDetection(enableLiveness)

  const [phase,   setPhase]   = useState('init')
  const [error,   setError]   = useState('')
  const [countdown, setCountdown] = useState(null)
  const [retryKey, setRetryKey] = useState(0)
  const livenessStartedRef = useRef(false)

  const capture = useCallback(async () => {
    setPhase('capturing')
    setCountdown(null)
    try {
      let result
      let livenessVerified = false
      let blinks = 0

      if (storedDescriptor) {
        result = await verifyAgainst(storedDescriptor)
      } else {
        const descriptor = await captureDescriptor()
        if (!descriptor) throw new Error('No face detected. Look directly at the camera.')
        result = { verified: true, confidence: 1, faceDescriptor: descriptor }
      }

      if (enableLiveness && livenessResult) {
        livenessVerified = livenessResult.success
        blinks = livenessResult.blinkCount || 0
      } else if (!enableLiveness) {
        livenessVerified = true
      }

      onResult({
        verified: result.verified,
        faceDescriptor: result.faceDescriptor,
        livenessVerified,
        blinkCount: blinks,
      })
      setPhase('done')
    } catch (e) {
      setError(e.message || 'Face detection failed')
      setPhase('error')
    }
  }, [storedDescriptor, verifyAgainst, captureDescriptor, onResult, enableLiveness, livenessResult])

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

        if (enableLiveness && videoRef.current && !livenessStartedRef.current) {
          livenessStartedRef.current = true
          startLivenessCheck(videoRef.current)
        }

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
  }, [retryKey, enableLiveness])

  const retry = () => {
    setError('')
    livenessStartedRef.current = false
    stopCamera()
    setRetryKey(k => k + 1)
  }

  return (
    <div className="space-y-4">
      <div className="text-center mb-2">
        <p className="text-sm font-medium text-gray-700">Step 1 — Face verification</p>
        <p className="text-xs text-gray-400">Look directly at the camera. Keep your face centred.</p>
        {enableLiveness && <p className="text-xs text-amber-600 mt-1">Blink naturally to pass liveness check</p>}
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

        {(phase === 'capturing' || isLivenessChecking) && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/50 gap-2">
            <Spinner size="lg" className="border-white border-t-primary-400" />
            <p className="text-white text-sm">
              {isLivenessChecking ? 'Checking liveness…' : 'Analysing face…'}
            </p>
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

      {livenessResult && !livenessResult.success && enableLiveness && (
        <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-100 rounded-xl text-sm text-amber-700">
          <AlertCircle size={15} className="mt-0.5 shrink-0" />
          <span>{livenessResult.message}</span>
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
