import { apiRequest } from '../auth/auth-api'
import type { OrderStatus } from '../restaurant/orders-logic'
import type { CourierAction } from './courier-logic'
import type { Position } from './location-sender'

const BASE = '/api/courier'

export interface Availability {
  available: boolean
  freeSince: string | null
}

export interface CourierDelivery {
  orderId: string
  status: OrderStatus
  restaurant: { name: string }
  delivery: { label: string | null; street: string; district: string; reference: string | null; phone: string | null }
  lines: { productName: string; quantity: number }[]
  assignedAt: string | null
}

function headers(token: string): HeadersInit {
  return { Authorization: 'Bearer ' + token }
}

export function getAvailability(token: string): Promise<Availability> {
  return apiRequest(BASE + '/availability', { headers: headers(token) })
}

export function setAvailability(token: string, available: boolean): Promise<Availability> {
  return apiRequest(BASE + '/availability', {
    method: 'PUT',
    headers: { ...headers(token), 'Content-Type': 'application/json' },
    body: JSON.stringify({ available }),
  })
}

/** Ubicación vigente del repartidor con una entrega en curso (RF-26); el servidor la asocia a su entrega. */
export function sendLocation(token: string, position: Position): Promise<void> {
  return apiRequest(BASE + '/location', {
    method: 'PUT',
    headers: { ...headers(token), 'Content-Type': 'application/json' },
    body: JSON.stringify(position),
  })
}

export function listDeliveries(token: string): Promise<CourierDelivery[]> {
  return apiRequest(BASE + '/deliveries', { headers: headers(token) })
}

/** Una acción sobre la entrega propia; solo el rechazo admite un motivo opcional. */
export function actOnDelivery(token: string, orderId: string, action: CourierAction, reason?: string): Promise<CourierDelivery> {
  const trimmed = reason?.trim()
  return apiRequest(BASE + '/deliveries/' + encodeURIComponent(orderId) + '/' + action, {
    method: 'POST',
    headers: { ...headers(token), 'Content-Type': 'application/json' },
    ...(action === 'reject' && trimmed ? { body: JSON.stringify({ reason: trimmed }) } : {}),
  })
}
