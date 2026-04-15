// Generates a stable device fingerprint from browser properties.
export async function getDeviceFingerprint() {
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
    nav.deviceMemory || 0,
    screen.pixelDepth || 0,
  ]
  const raw = parts.join('|')
  
  const encoder = new TextEncoder()
  const data = encoder.encode(raw)
  const hashBuffer = await crypto.subtle.digest('SHA-256', data)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  const fp = hashArray.map(b => b.toString(16).padStart(2, '0')).join('').slice(0, 16)
  
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
