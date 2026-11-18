import type { OrderStatus } from '../restaurant/orders-logic'
import type { LocationUnavailableReason } from './tracking-api'

/** El cliente consulta el seguimiento cada 5 s mientras el pedido siga en curso (SC-002: nueva posición en < 10 s). */
export const TRACKING_POLL_MS = 5_000

const FINAL_STATUSES: OrderStatus[] = ['DELIVERED', 'REJECTED', 'CANCELLED']

/** Un pedido terminado ya no cambia: se deja de consultar. */
export function isFinalStatus(status: OrderStatus): boolean {
  return FINAL_STATUSES.includes(status)
}

export const locationReasonMessages: Record<LocationUnavailableReason, string> = {
  NOT_PICKED_UP: 'Podrás ver la ubicación del repartidor cuando recoja tu pedido.',
  FINISHED: 'El pedido ya terminó, por eso no se muestra la ubicación del repartidor.',
  NO_LOCATION_YET: 'El repartidor aún no envió su ubicación. Se mostrará aquí en cuanto la comparta.',
}

export function formatStageTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' })
}

/** Antigüedad de la ubicación («hace 12 s», «hace 3 min») para que el cliente sepa cuán reciente es. */
export function formatAge(iso: string, nowMs: number): string {
  const elapsed = Math.round((nowMs - new Date(iso).getTime()) / 1000)
  if (Number.isNaN(elapsed)) return ''
  if (elapsed < 5) return 'ahora mismo'
  if (elapsed < 60) return `hace ${elapsed} s`
  const minutes = Math.floor(elapsed / 60)
  if (minutes < 60) return `hace ${minutes} min`
  return `hace ${Math.floor(minutes / 60)} h`
}

export function formatCoordinates(latitude: number, longitude: number): string {
  return `${latitude.toFixed(5)}, ${longitude.toFixed(5)}`
}

/** Enlace normal a un mapa público por coordenadas; no se incrusta ningún mapa ni se llama a una API externa. */
export function mapUrl(latitude: number, longitude: number): string {
  const lat = latitude.toFixed(5)
  const lon = longitude.toFixed(5)
  return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=17/${lat}/${lon}`
}
