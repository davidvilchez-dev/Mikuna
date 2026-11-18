import type { Role } from '../auth/auth-api'
import type { OrderStatus } from '../restaurant/orders-logic'
import { orderStatusLabels } from '../restaurant/orders-logic'

export const HISTORY_PAGE_SIZE = 20

export interface HistoryFilter {
  status: OrderStatus | ''
  from: string
  to: string
}

export const emptyFilter: HistoryFilter = { status: '', from: '', to: '' }

/** Roles con historial; el administrador no lo tiene en este incremento (vistas de la spec 011). */
export function hasHistory(role: Role | null): role is 'CUSTOMER' | 'RESTAURANT' | 'COURIER' {
  return role === 'CUSTOMER' || role === 'RESTAURANT' || role === 'COURIER'
}

export function historyPath(role: Role): string {
  switch (role) {
    case 'RESTAURANT':
      return '/api/restaurant/orders/history'
    case 'COURIER':
      return '/api/courier/deliveries/history'
    default:
      return '/api/customer/orders/history'
  }
}

const COURIER_STATUSES: OrderStatus[] = ['ASSIGNED', 'PICKED_UP', 'DELIVERED']
const ALL_STATUSES = Object.keys(orderStatusLabels) as OrderStatus[]

/** El repartidor solo filtra por los estados que incluye su historial. */
export function statusOptions(role: Role): OrderStatus[] {
  return role === 'COURIER' ? COURIER_STATUSES : ALL_STATUSES
}

/** Query de la consulta: omite los filtros vacíos. */
export function buildQuery(filter: HistoryFilter, page: number): string {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(HISTORY_PAGE_SIZE))
  if (filter.status) params.set('status', filter.status)
  if (filter.from) params.set('from', filter.from)
  if (filter.to) params.set('to', filter.to)
  return '?' + params.toString()
}

/** Mensaje si el rango es inválido; se muestra antes de llamar al servidor. */
export function validateRange(from: string, to: string): string | undefined {
  if (from && to && from > to) return 'La fecha inicial no puede ser posterior a la final'
  return undefined
}

/** Añade la página nueva sin repetir operaciones ya mostradas. */
export function mergePages<T extends { orderId: string }>(previous: T[], next: T[]): T[] {
  const seen = new Set(previous.map((item) => item.orderId))
  return [...previous, ...next.filter((item) => !seen.has(item.orderId))]
}

export const RATING_VALUES = [1, 2, 3, 4, 5] as const

export function isValidRating(value: number): boolean {
  return Number.isInteger(value) && value >= 1 && value <= 5
}

/** Texto para el error de la calificación según el código del servidor. */
export function ratingErrorMessage(status: number, code: string | null, fallback: string): string {
  if (code === 'RATING_ALREADY_EXISTS') return 'Este pedido ya fue calificado.'
  if (code === 'ORDER_NOT_DELIVERED') return 'Solo puedes calificar un pedido que ya te entregaron.'
  if (status === 404) return 'No encontramos ese pedido.'
  return fallback
}

export function formatHistoryDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' })
}
