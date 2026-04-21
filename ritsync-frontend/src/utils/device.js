// Generates a stable device fingerprint from browser properties.
export async function getDeviceFingerprint() {
  const cached = sessionStorage.getItem('_device_fp')
  if (cached) return cached

  const nav = window.navigator
  const screen = window.screen
  const parts = [
    nav.userAgent,
    nav.language,
    nav.languages?.join(',') || '',
    nav.hardwareConcurrency,
    nav.deviceMemory || 0,
    screen.colorDepth,
    screen.width + 'x' + screen.height,
    screen.pixelDepth || 0,
    new Date().getTimezoneOffset(),
    nav.platform || '',
    nav.maxTouchPoints || 0,
    window.devicePixelRatio || 1,
    Intl.DateTimeFormat().resolvedOptions().timeZone,
  ]

  const raw = parts.join('|')

  const encoder = new TextEncoder()
  const data = encoder.encode(raw)
  const hashBuffer = await crypto.subtle.digest('SHA-256', data)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  const fp = hashArray.map(b => b.toString(16).padStart(2, '0')).join('')

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
