/** Envío periódico de la ubicación del repartidor mientras tiene una entrega en curso (RF-26). */

/** Como máximo una ubicación cada 10 s: suficiente para el seguimiento sin gastar batería ni red. */
export const LOCATION_INTERVAL_MS = 10_000

export interface Position {
  latitude: number
  longitude: number
  recordedAt: string
}

export type SharingState = 'idle' | 'sharing' | 'denied' | 'unsupported' | 'error'

export const sharingMessages: Record<SharingState, string> = {
  idle: 'Tu ubicación se compartirá mientras tengas una entrega en curso.',
  sharing: 'Estás compartiendo tu ubicación con el cliente.',
  denied: 'Permite el acceso a tu ubicación en el navegador para que el cliente pueda seguir su pedido.',
  unsupported: 'Tu navegador no permite compartir la ubicación.',
  error: 'No se pudo enviar tu ubicación; se reintentará.',
}

/** Limita el ritmo de envíos: pasa una posición solo si transcurrió el intervalo desde el último envío. */
export function createLocationThrottle(intervalMs: number, send: (position: Position) => Promise<void>,
  now: () => number = Date.now) {
  let lastSentAt: number | null = null
  let sending = false

  return async function offer(position: Position): Promise<boolean> {
    const current = now()
    if (sending || (lastSentAt !== null && current - lastSentAt < intervalMs)) return false
    sending = true
    lastSentAt = current
    try {
      await send(position)
      return true
    } finally {
      sending = false
    }
  }
}

interface GeolocationLike {
  watchPosition: (
    success: (position: { coords: { latitude: number; longitude: number }; timestamp: number }) => void,
    error: (error: { code: number }) => void,
    options?: { enableHighAccuracy?: boolean; maximumAge?: number },
  ) => number
  clearWatch: (id: number) => void
}

const PERMISSION_DENIED = 1

/**
 * Empieza a compartir la ubicación del navegador; devuelve la función que lo detiene. El permiso lo concede el
 * repartidor en el navegador (aplicaciones nativas fuera de alcance). Un fallo de red no detiene el envío.
 */
export function startLocationSharing(
  send: (position: Position) => Promise<void>,
  onState: (state: SharingState) => void,
  geolocation: GeolocationLike | undefined = typeof navigator === 'undefined' ? undefined : navigator.geolocation,
  now: () => number = Date.now,
): () => void {
  if (!geolocation) {
    onState('unsupported')
    return () => undefined
  }
  const offer = createLocationThrottle(LOCATION_INTERVAL_MS, send, now)
  const watchId = geolocation.watchPosition(
    (position) => {
      onState('sharing')
      offer({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        recordedAt: new Date(position.timestamp).toISOString(),
      }).catch(() => onState('error'))
    },
    (error) => onState(error.code === PERMISSION_DENIED ? 'denied' : 'error'),
    { enableHighAccuracy: true, maximumAge: 5_000 },
  )
  return () => {
    geolocation.clearWatch(watchId)
    onState('idle')
  }
}
