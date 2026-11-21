import { useState, useCallback } from 'react'

const MODELS_URL = '/face-models'
const FALLBACK   = 'https://cdn.jsdelivr.net/npm/face-api.js@0.22.2/weights'

const FRAMES      = 50   // ~5 s at 10 fps
const INTERVAL_MS = 100
const EAR_CLOSED  = 0.21
const EAR_OPEN    = 0.25

let modelsPromise = null
async function loadLivenessModels() {
  if (!modelsPromise) {
    modelsPromise = (async () => {
      const faceapi = await import('face-api.js')
      const load = (url) => Promise.all([
        faceapi.nets.tinyFaceDetector.loadFromUri(url),
        faceapi.nets.faceLandmark68TinyNet.loadFromUri(url),
      ])
      try { await load(MODELS_URL) } catch { await load(FALLBACK) }
      return faceapi
    })().catch((e) => { modelsPromise = null; throw e })
  }
  return modelsPromise
}

const dist = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1])

// Eye aspect ratio over the 6 eye landmarks (p1..p6), same formula the server uses.
const ear = (f, p1, p2, p3, p4, p5, p6) => {
  const h = dist(f[p1], f[p4])
  return h === 0 ? 0 : (dist(f[p2], f[p6]) + dist(f[p3], f[p5])) / (2 * h)
}

function countBlinks(frames) {
  let blinks = 0
  let closed = false
  for (const f of frames) {
    const e = (ear(f, 36, 37, 38, 39, 40, 41) + ear(f, 42, 43, 44, 45, 46, 47)) / 2
    if (!closed && e < EAR_CLOSED) closed = true
    else if (closed && e > EAR_OPEN) { closed = false; blinks++ }
  }
  return blinks
}

/**
 * Captures ~5 s of 68-point facial landmarks. The frames are sent to the server, which makes
 * the actual liveness decision; the client-side blink count is only used for instant feedback.
 */
export const useLivenessDetection = (isEnabled = false) => {
  const [isLivenessChecking, setIsLivenessChecking] = useState(false)
  const [livenessResult, setLivenessResult] = useState(null)

  /** Resolves to { success, blinkCount, frames, message }. */
  const runLivenessCheck = useCallback(async (videoElement) => {
    if (!isEnabled) {
      const r = { success: true, blinkCount: 0, frames: null, message: 'Liveness not required' }
      setLivenessResult(r)
      return r
    }

    setIsLivenessChecking(true)
    setLivenessResult(null)
    try {
      const faceapi = await loadLivenessModels()
      const options = new faceapi.TinyFaceDetectorOptions()
      const frames = []

      for (let i = 0; i < FRAMES; i++) {
        const started = performance.now()
        const det = await faceapi.detectSingleFace(videoElement, options).withFaceLandmarks(true)
        if (det) frames.push(det.landmarks.positions.map(p => [p.x, p.y]))
        const wait = INTERVAL_MS - (performance.now() - started)
        if (wait > 0) await new Promise(r => setTimeout(r, wait))
      }

      let r
      if (frames.length < 10) {
        r = { success: false, blinkCount: 0, frames, message: 'Face not visible long enough. Keep your face in the frame.' }
      } else {
        const blinkCount = countBlinks(frames)
        r = blinkCount >= 1
          ? { success: true,  blinkCount, frames, message: `Blink detected (${blinkCount})` }
          : { success: false, blinkCount, frames, message: 'No blink detected. Please blink naturally.' }
      }
      setLivenessResult(r)
      return r
    } finally {
      setIsLivenessChecking(false)
    }
  }, [isEnabled])

  return { runLivenessCheck, isLivenessChecking, livenessResult }
}
