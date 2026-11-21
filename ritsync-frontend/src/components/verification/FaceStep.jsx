import { useEffect, useState, useCallback, useRef } from 'react'
import { AlertCircle, RefreshCw } from 'lucide-react'
import { useFaceVerification } from '../../hooks/useFaceVerification'
import { useLivenessDetection } from '../../hooks/useLivenessDetection'
import Button from '../ui/Button'
import Spinner from '../ui/Spinner'

/**
 * Captures a live face descriptor (and, when the session requires it, ~5 s of landmark frames
 * for liveness). Matching and the liveness decision happen on the server.
 */
export default function FaceStep({ onResult, session }) {
  const { videoRef, loadModels, startCamera, stopCamera, captureDescriptor } = useFaceVerification()

  const enableLiveness = session?.enableLiveness ?? false
  const { runLivenessCheck, isLivenessChecking } = useLivenessDetection(enableLiveness)

  const [phase,     setPhase]     = useState('init')
  const [error,     setError]     = useState('')
  const [countdown, setCountdown] = useState(null)
  const [retryKey,  setRetryKey]  = useState(0)
  const busyRef = useRef(false)

  const capture = useCallback(async () => {
    if (busyRef.current) return
    busyRef.current = true
    setCountdown(null)
    try {
      let liveness = { success: true, frames: null }
      if (enableLiveness) {
        setPhase('liveness')
        liveness = await runLivenessCheck(videoRef.current)
        if (!liveness.success) throw new Error(liveness.message)
      }

      setPhase('capturing')
      const descriptor = await captureDescriptor()
      if (!descriptor) throw new Error('No face detected. Look directly at the camera.')

      onResult({
        verified: true,
        faceDescriptor: descriptor,
        livenessFrames: liveness.frames,
      })
      setPhase('done')
    } catch (e) {
      setError(e.message || 'Face detection failed')
      setPhase('error')
    } finally {
      busyRef.current = false
    }
  }, [enableLiveness, runLivenessCheck, captureDescriptor, onResult, videoRef])

  useEffect(() => {
    let cancelled = false
    let timer = null
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
        timer = setInterval(() => {
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
      if (timer) clearInterval(timer)
      stopCamera()
    }
    // capture is intentionally excluded: re-running would restart the camera mid-capture
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [retryKey, loadModels, startCamera, stopCamera])

  const retry = () => {
    setError('')
    stopCamera()
    setRetryKey(k => k + 1)
  }

  const overlayText = phase === 'liveness' || isLivenessChecking
    ? 'Blink naturally… checking liveness'
    : 'Analysing face…'

  return (
    <div className="space-y-4">
      <div className="text-center mb-2">
        <p className="text-sm font-medium text-gray-700">Step 1 — Face verification</p>
        <p className="text-xs text-gray-400">Look directly at the camera. Keep your face centred.</p>
        {enableLiveness && <p className="text-xs text-amber-600 mt-1">This session checks liveness: blink naturally when asked.</p>}
      </div>

      <div className="relative bg-black rounded-2xl overflow-hidden aspect-video max-h-64 mx-auto">
        <video ref={videoRef} autoPlay muted playsInline className="w-full h-full object-cover" />

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

        {(phase === 'capturing' || phase === 'liveness') && (
          <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/40 gap-2">
            <Spinner size="lg" className="border-white border-t-primary-400" />
            <p className="text-white text-sm">{overlayText}</p>
          </div>
        )}

        {phase === 'init' && (
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
