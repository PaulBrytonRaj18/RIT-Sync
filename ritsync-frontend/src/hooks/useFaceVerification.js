import { useState, useRef, useCallback } from 'react'

const MODELS_URL = '/face-models'
const FALLBACK   = 'https://cdn.jsdelivr.net/npm/face-api.js@0.22.2/weights'

export function useFaceVerification() {
  const [status,     setStatus]     = useState('idle')
  const [confidence, setConfidence] = useState(null)
  const videoRef    = useRef(null)
  const streamRef   = useRef(null)
  const faceapiRef  = useRef(null)
  const loadedRef   = useRef(false)

  const loadModels = useCallback(async () => {
    if (loadedRef.current) return faceapiRef.current
    setStatus('loading')
    const faceapi = await import('face-api.js')
    try {
      await Promise.all([
        faceapi.nets.ssdMobilenetv1.loadFromUri(MODELS_URL),
        faceapi.nets.faceLandmark68Net.loadFromUri(MODELS_URL),
        faceapi.nets.faceRecognitionNet.loadFromUri(MODELS_URL),
      ])
    } catch {
      await Promise.all([
        faceapi.nets.ssdMobilenetv1.loadFromUri(FALLBACK),
        faceapi.nets.faceLandmark68Net.loadFromUri(FALLBACK),
        faceapi.nets.faceRecognitionNet.loadFromUri(FALLBACK),
      ])
    }
    loadedRef.current = true
    faceapiRef.current = faceapi
    setStatus('ready')
    return faceapi
  }, [])

  const startCamera = useCallback(async () => {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } }
    })
    streamRef.current = stream
    if (videoRef.current) {
      videoRef.current.srcObject = stream
      await new Promise(r => { videoRef.current.onloadedmetadata = r })
      await videoRef.current.play()
    }
  }, [])

  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach(t => t.stop())
    streamRef.current = null
  }, [])

  const captureDescriptor = useCallback(async () => {
    const faceapi = await loadModels()
    setStatus('detecting')
    await new Promise(r => setTimeout(r, 200))
    const detection = await faceapi
      .detectSingleFace(videoRef.current, new faceapi.SsdMobilenetv1Options({ minConfidence: 0.5 }))
      .withFaceLandmarks()
      .withFaceDescriptor()
    if (!detection) { setStatus('ready'); return null }
    const descriptor = Array.from(detection.descriptor)
    sessionStorage.setItem('_face_descriptor_cache', JSON.stringify(descriptor))
    setStatus('done')
    return descriptor
  }, [loadModels])

  const verifyAgainst = useCallback(async (storedDescriptorArray) => {
    const faceapi = await loadModels()
    setStatus('detecting')
    await new Promise(r => setTimeout(r, 200))
    const detection = await faceapi
      .detectSingleFace(videoRef.current, new faceapi.SsdMobilenetv1Options({ minConfidence: 0.5 }))
      .withFaceLandmarks()
      .withFaceDescriptor()

    if (!detection) {
      setStatus('failed')
      return { verified: false, confidence: 0, distance: 1 }
    }

    const stored   = new Float32Array(storedDescriptorArray)
    const distance = faceapi.euclideanDistance(detection.descriptor, stored)
    const conf     = Math.max(0, Math.min(1, 1 - distance))
    const verified  = distance < 0.55

    setConfidence(conf)
    setStatus(verified ? 'verified' : 'failed')

    const descriptor = Array.from(detection.descriptor)
    sessionStorage.setItem('_face_descriptor_cache', JSON.stringify(descriptor))

    return { verified, confidence: conf, distance, faceDescriptor: descriptor }
  }, [loadModels])

  return {
    status, confidence, videoRef,
    loadModels, startCamera, stopCamera,
    captureDescriptor, verifyAgainst,
  }
}
