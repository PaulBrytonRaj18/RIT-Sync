import { useEffect, useState } from 'react'
import { Camera, CheckCircle, RefreshCw, Info } from 'lucide-react'
import { useFaceVerification } from '../../hooks/useFaceVerification'
import { usersApi } from '../../api/users'
import useAuthStore from '../../store/authStore'
import PageHeader from '../../components/shared/PageHeader'
import Button from '../../components/ui/Button'
import Spinner from '../../components/ui/Spinner'
import toast from 'react-hot-toast'

// Steps: instructions → camera → capturing → saving → done

export default function FaceEnrollPage() {
  const user       = useAuthStore(s => s.user)
  const updateUser = useAuthStore(s => s.updateUser)
  const { status, videoRef, loadModels, startCamera, stopCamera, captureDescriptor } = useFaceVerification()
  const [step, setStep]     = useState('instructions')
  const [saving, setSaving] = useState(false)
  const [captures, setCaptures] = useState([])

  const isEnrolled = user?.faceEnrolled

  const handleStartCamera = async () => {
    setStep('camera')
    await loadModels()
    await startCamera()
  }

  const handleCapture = async () => {
    setStep('capturing')
    try {
      const desc = await captureDescriptor()
      if (!desc) {
        toast.error('No face detected — please look directly at the camera')
        setStep('camera')
        return
      }

      const newCaptures = [...captures, desc]
      setCaptures(newCaptures)

      if (newCaptures.length < 3) {
        setStep('camera')
        toast.success(`Sample ${newCaptures.length}/3 captured — keep going!`)
        return
      }

      setStep('saving')
      setSaving(true)

      const avg = newCaptures.reduce((acc, curr) =>
        curr.map((val, i) => (acc[i] || 0) + val / newCaptures.length), Array(128).fill(0))

      // The server encrypts the template at rest (AES-256-GCM) and never returns it.
      await usersApi.enrollFaceDescriptor(avg)

      stopCamera()
      updateUser({ ...user, faceEnrolled: true })
      setStep('done')
      toast.success('Face ID enrolled successfully!')
    } catch (err) {
      if (err.response?.status === 409) {
        // Already enrolled on the server (e.g. from another device)
        stopCamera()
        updateUser({ ...user, faceEnrolled: true })
        setCaptures([])
        setStep('instructions')
      }
      toast.error(err.response?.data?.message || err.message || 'Capture failed — try again')
      if (err.response?.status !== 409) setStep('camera')
    } finally {
      setSaving(false)
    }
  }

  const handleReset = () => {
    stopCamera()
    setCaptures([])
    setStep('instructions')
  }

  useEffect(() => () => stopCamera(), [stopCamera])

  return (
    <div className="animate-fade-in max-w-lg mx-auto">
      <PageHeader
        title="Face ID setup"
        subtitle="Set up facial recognition for attendance verification"
      />

      {isEnrolled && step === 'instructions' && (
        <div className="mb-4 flex items-center gap-2 p-3 bg-green-50 border border-green-100 rounded-xl text-sm text-green-700">
          <CheckCircle size={15} /> Your Face ID is enrolled. To re-enroll, ask an administrator to reset it.
        </div>
      )}

      {step === 'instructions' && (
        <div className="card space-y-5">
          <div className="p-4 bg-primary-50 rounded-xl space-y-2">
            {[
              'Ensure good lighting — face a light source',
              'Remove glasses, masks, or hats if possible',
              'Look directly at the camera',
              'We capture 3 samples for accuracy',
            ].map((tip, i) => (
              <div key={i} className="flex items-start gap-2.5 text-sm text-primary-800">
                <Info size={14} className="mt-0.5 shrink-0" />
                {tip}
              </div>
            ))}
          </div>
          <Button variant="primary" className="w-full" onClick={handleStartCamera} disabled={isEnrolled}>
            <Camera size={16} /> {isEnrolled ? 'Already enrolled' : 'Start camera'}
          </Button>
        </div>
      )}

      {(step === 'camera' || step === 'capturing') && (
        <div className="card space-y-4">
          <div className="relative bg-black rounded-xl overflow-hidden aspect-video">
            <video ref={videoRef} autoPlay playsInline muted
              className="w-full h-full object-cover scale-x-[-1]" />
            {/* Oval face guide */}
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <div className="w-40 h-52 border-2 border-white/60 rounded-full" />
            </div>
            {step === 'capturing' && (
              <div className="absolute inset-0 flex items-center justify-center bg-black/50">
                <Spinner size="lg" className="border-white border-t-primary-400" />
              </div>
            )}
          </div>

          {/* Progress dots */}
          <div className="flex items-center justify-center gap-2">
            {[0,1,2].map(i => (
              <div key={i} className={`w-2.5 h-2.5 rounded-full transition-colors ${
                i < captures.length ? 'bg-green-500' : 'bg-gray-200'
              }`} />
            ))}
            <span className="text-xs text-gray-500 ml-2">{captures.length}/3 samples</span>
          </div>

          {status === 'loading' ? (
            <div className="flex items-center justify-center gap-2 text-sm text-gray-500">
              <Spinner size="sm" /> Loading face detection models…
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              <Button variant="secondary" onClick={handleReset}><RefreshCw size={14} /> Restart</Button>
              <Button variant="primary" onClick={handleCapture} loading={saving || step === 'capturing'}>
                <Camera size={14} /> Capture sample
              </Button>
            </div>
          )}
        </div>
      )}

      {step === 'done' && (
        <div className="card text-center space-y-4">
          <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto">
            <CheckCircle size={32} className="text-green-600" />
          </div>
          <div>
            <h3 className="font-semibold text-gray-900">Face ID enrolled!</h3>
            <p className="text-sm text-gray-500 mt-1">
              Your face has been registered securely. You can now mark attendance using facial recognition.
            </p>
          </div>
          <Button variant="secondary" className="mx-auto" onClick={handleReset}>
            <RefreshCw size={14} /> Re-enroll
          </Button>
        </div>
      )}
    </div>
  )
}
