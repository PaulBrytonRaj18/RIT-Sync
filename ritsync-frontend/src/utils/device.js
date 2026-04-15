// Generates a stable device fingerprint from browser properties.
// Not cryptographic — just enough uniqueness to bind a device.
export function getDeviceFingerprint() {
  const cached = sessionStorage.getItem('_device_fp')
  if (cached) return cached

  const nav = window.navigator
  const screen = window.screen
  const parts = [
    nav.userAgent,
    nav.language,
    nav.hardwareConcurrency,
    screen.colorDepth,
    screen.width + 'x' + screen.height,
    new Date().getTimezoneOffset(),
    nav.platform || '',
  ]
  const raw = parts.join('|')
  // Simple djb2 hash
  let hash = 5381
  for (let i = 0; i < raw.length; i++) {
    hash = ((hash << 5) + hash) ^ raw.charCodeAt(i)
    hash = hash >>> 0
  }
  const fp = hash.toString(16).padStart(8, '0')
  sessionStorage.setItem('_device_fp', fp)
  return fp
}

export function getDeviceName() {
  const ua = navigator.userAgent
  if (/iPhone|iPad/.test(ua)) return 'iOS Device'
  if (/Android/.test(ua)) return 'Android Device'
  if (/Macintosh/.test(ua)) return 'Mac'
  if (/Windows/.test(ua)) return 'Windows PC'
  return 'Unknown Device'
}
