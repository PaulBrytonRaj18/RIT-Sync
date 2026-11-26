import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { BookOpen } from 'lucide-react'
import useAuthStore from '../../store/authStore'
import useOfflineStore from '../../store/offlineStore'
import { sessionsApi } from '../../api/sessions'
import { attendanceApi } from '../../api/attendance'
import { getCachedSessions } from '../../offline/db'
import VerificationStepper from '../../components/verification/VerificationStepper'
import FaceStep from '../../components/verification/FaceStep'
import GpsStep from '../../components/verification/GpsStep'
import BLEStep from '../../components/verification/BLEStep'
import DeviceStep from '../../components/verification/DeviceStep'
import VerificationResult from '../../components/verification/VerificationResult'
import Spinner from '../../components/ui/Spinner'
import EmptyState from '../../components/ui/EmptyState'
import Button from '../../components/ui/Button'
import toast from 'react-hot-toast'

const STEPS = { FACE: 1, GPS: 2, BLE: 3, DEVICE: 4, SUBMITTING: 5, RESULT: 6 }

// Evidence collected on the device. The server makes every verification decision.
const EMPTY_VERIFICATION = {
  faceDescriptor: null,
  livenessFrames: null,
  gpsPosition:    null,
  gpsDistance:    null,
  beaconUuid:     null,
  beaconToken:    null,
  beaconRssi:     null,
  deviceFingerprint: null,
  deviceName:     null,
}

export default function MarkAttendancePage() {
  const navigate     = useNavigate()
  const user         = useAuthStore(s => s.user)
  const isOnline     = useOfflineStore(s => s.isOnline)

  const [openSessions, setOpenSessions] = useState([])
  const [selectedSession, setSelectedSession] = useState(null)
  const [loadingSessions, setLoadingSessions] = useState(true)
  const [faceRequired, setFaceRequired] = useState(false)

  const [currentStep, setCurrentStep] = useState(STEPS.FACE)
  const [stepStatuses, setStepStatuses] = useState({ 1: 'active', 2: 'idle', 3: 'idle', 4: 'idle' })

  const [verificationData, setVerificationData] = useState(EMPTY_VERIFICATION)

  const [result,     setResult]     = useState(null)
  const [, setSubmitting] = useState(false)

  useEffect(() => {
    if (!user?.faceEnrolled) {
      setFaceRequired(true)
      setLoadingSessions(false)
      return
    }

    const load = async () => {
      try {
        const sessions = isOnline
          ? await sessionsApi.getOpenSessions()
          : (await getCachedSessions()).filter(s => s.isOpen)
        setOpenSessions(sessions)
        if (sessions.length === 1) setSelectedSession(sessions[0])
      } catch (e) {
        toast.error('Could not load open sessions')
      } finally {
        setLoadingSessions(false)
      }
    }
    load()
  }, [isOnline, user?.faceEnrolled])

  const advanceTo = useCallback((nextStep, prevStepResult) => {
    setStepStatuses(prev => ({
      ...prev,
      [currentStep]: prevStepResult ? 'done' : 'failed',
      ...(nextStep <= 4 ? { [nextStep]: 'active' } : {}),
    }))
    setCurrentStep(nextStep)
  }, [currentStep])

  const onFaceResult = useCallback(({ verified, faceDescriptor, livenessFrames }) => {
    setVerificationData(prev => ({
      ...prev,
      faceDescriptor,
      livenessFrames: livenessFrames || null,
    }))
    if (!verified) {
      toast.error('Face ID failed. Please try again.')
      advanceTo(STEPS.FACE, false)
      return
    }
    toast.success('Face captured!')
    advanceTo(STEPS.GPS, true)
  }, [advanceTo])

  const onGpsResult = useCallback(({ verified, position, distance }) => {
    setVerificationData(prev => ({
      ...prev,
      gpsPosition: position,
      gpsDistance: distance,
    }))
    if (!verified) {
      toast.error(`Location check failed — ${distance}m from classroom.`)
    } else {
      toast.success('Location verified!')
    }
    advanceTo(STEPS.BLE, verified)
  }, [advanceTo])

  const onBLEResult = useCallback(({ beaconUuid, beaconToken, beaconRssi }) => {
    setVerificationData(prev => ({
      ...prev,
      beaconUuid:  beaconUuid,
      beaconToken: beaconToken,
      beaconRssi:  beaconRssi,
    }))
    toast.success('Beacon verified!')
    advanceTo(STEPS.DEVICE, true)
  }, [advanceTo])

  const onDeviceResult = useCallback(async ({ fingerprint, deviceName }) => {
    if (!selectedSession?.id) {
      toast.error('Session information missing. Please select a session again.')
      return
    }

    setVerificationData(prev => ({
      ...prev,
      deviceFingerprint: fingerprint,
      deviceName:        deviceName,
    }))
    advanceTo(STEPS.SUBMITTING, true)

    setSubmitting(true)
    try {
      const payload = {
        sessionId:         selectedSession.id,
        faceDescriptor:    verificationData.faceDescriptor,
        livenessFrames:    verificationData.livenessFrames,
        userLat:           verificationData.gpsPosition?.lat,
        userLng:           verificationData.gpsPosition?.lng,
        gpsAccuracy:       verificationData.gpsPosition?.accuracy,
        beaconUuid:        verificationData.beaconUuid,
        beaconToken:       verificationData.beaconToken,
        beaconRssi:        verificationData.beaconRssi,
        deviceFingerprint: fingerprint,
        deviceName:        deviceName,
      }
      const res = await attendanceApi.markAttendance(payload, !isOnline)
      if (res.offline) {
        // Nothing has been verified yet: the server checks everything when the request syncs.
        setResult({
          queued: true,
          allVerified: false,
          message: 'Saved offline. It will be verified and recorded when you reconnect, as long as the session is still open.',
          status: 'QUEUED',
        })
      } else {
        setResult(res)
      }
    } catch (e) {
      const msg = e.response?.data?.message || 'Submission failed'
      toast.error(msg)
      setResult({ allVerified: false, error: true, message: msg, status: 'ABSENT' })
    } finally {
      setSubmitting(false)
      setCurrentStep(STEPS.RESULT)
    }
  }, [selectedSession, verificationData, isOnline, advanceTo])

  const handleRetry = () => {
    setCurrentStep(STEPS.FACE)
    setStepStatuses({ 1: 'active', 2: 'idle', 3: 'idle', 4: 'idle' })
    setResult(null)
    setVerificationData(EMPTY_VERIFICATION)
  }

  const handleEnrollNow = () => {
    navigate('/student/face-enroll')
  }

  if (faceRequired) {
    return (
      <div className="max-w-md mx-auto animate-fade-in">
        <div className="card text-center space-y-4">
          <div className="w-16 h-16 bg-amber-100 rounded-full flex items-center justify-center mx-auto">
            <svg className="w-8 h-8 text-amber-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
          </div>
          <div>
            <h3 className="text-lg font-semibold text-gray-900">Face ID Required</h3>
            <p className="text-sm text-gray-500 mt-2">
              You must enroll your Face ID before marking attendance. This helps verify your identity during attendance.
            </p>
          </div>
          <Button variant="primary" className="w-full" onClick={handleEnrollNow}>
            Enroll Face ID Now
          </Button>
        </div>
      </div>
    )
  }

  if (loadingSessions) {
    return (
      <div className="flex flex-col items-center justify-center py-32 gap-3">
        <Spinner size="lg" />
        <p className="text-sm text-gray-500">Looking for open sessions…</p>
      </div>
    )
  }

  if (openSessions.length === 0) {
    return (
      <div className="max-w-md mx-auto animate-fade-in">
        <div className="card">
          <EmptyState
            icon={BookOpen}
            title="No open sessions"
            description="Your teacher hasn't opened a session yet. Check back shortly."
          />
        </div>
      </div>
    )
  }

  if (!selectedSession) {
    return (
      <div className="max-w-md mx-auto animate-fade-in space-y-4">
        <h1 className="text-lg font-semibold text-gray-900">Select a session</h1>
        {openSessions.map(session => (
          <button key={session.id}
            onClick={() => setSelectedSession(session)}
            className="card w-full text-left hover:shadow-card-hover transition-shadow group">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-primary-100 rounded-xl flex items-center justify-center text-primary-700 font-bold text-sm">
                {session.courseCode?.slice(0, 2)}
              </div>
              <div className="flex-1">
                <p className="font-semibold text-gray-900">{session.courseName}</p>
                <p className="text-xs text-gray-500">{session.title || 'Class session'} · {session.geofenceRadiusM}m radius</p>
              </div>
              <span className="badge-green">Live</span>
            </div>
          </button>
        ))}
      </div>
    )
  }

  return (
    <div className="max-w-md mx-auto animate-fade-in">

      <div className="flex items-center gap-3 p-3 bg-primary-50 border border-primary-100 rounded-xl mb-6">
        <div className="w-9 h-9 bg-primary-100 rounded-lg flex items-center justify-center text-primary-700 font-bold text-xs">
          {selectedSession.courseCode?.slice(0, 2)}
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-primary-900 truncate">{selectedSession.courseName}</p>
          <p className="text-xs text-primary-600">{selectedSession.title || 'Class session'}</p>
        </div>
        <span className="badge-green shrink-0">Live</span>
      </div>

      <div className="card">
        {currentStep !== STEPS.RESULT && currentStep !== STEPS.SUBMITTING && (
          <VerificationStepper currentStep={currentStep} stepStatuses={stepStatuses} />
        )}

        {currentStep === STEPS.FACE && (
          <FaceStep
            onResult={onFaceResult}
            session={selectedSession}
          />
        )}

        {currentStep === STEPS.GPS && (
          <GpsStep
            session={selectedSession}
            onResult={onGpsResult}
          />
        )}

        {currentStep === STEPS.BLE && (
          <BLEStep
            session={selectedSession}
            onComplete={onBLEResult}
          />
        )}

        {currentStep === STEPS.DEVICE && (
          <DeviceStep onResult={onDeviceResult} />
        )}

        {currentStep === STEPS.SUBMITTING && (
          <div className="flex flex-col items-center py-12 gap-4">
            <Spinner size="lg" />
            <div className="text-center">
              <p className="font-medium text-gray-800">Submitting attendance…</p>
              <p className="text-sm text-gray-500 mt-0.5">Verifying with server</p>
            </div>
          </div>
        )}

        {currentStep === STEPS.RESULT && (
          <VerificationResult
            result={result}
            onRetry={handleRetry}
            onDone={() => navigate('/student')}
          />
        )}
      </div>
    </div>
  )
}
