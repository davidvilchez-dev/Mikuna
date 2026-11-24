import { panelHeaders } from './branch-storage'
import { apiRequest } from '../auth/auth-api'
import type { OrderStatus, RestaurantAction } from './orders-logic'

const BASE = '/api/restaurant/orders'

export interface RestaurantOrder {
  id: string
  status: OrderStatus
  lines: { productId: string; name: string; unitPrice: number; quantity: number; lineTotal: number }[]
  delivery: { label: string | null; street: string; district: string; reference: string | null; contactPhone: string | null }
  totals: { subtotal: number; deliveryFee: number; commission: number; total: number }
  payment: { method: 'CARD' | 'CASH'; status: 'AUTHORIZED' | 'CAPTURED' | 'RELEASED' }
  options: { storeNote: string | null; scheduledFor: string | null; receiver: { name: string; phone: string; note: string | null } | null }
  createdAt: string
  /** Vencimiento si nadie responde (spec 012) y segundos restantes según el servidor; null fuera de Pendiente. */
  expiresAt: string | null
  secondsToExpire: number | null
}

function headers(token: string): HeadersInit {
  return panelHeaders(token)
}

export function listOrders(token: string, status: OrderStatus): Promise<RestaurantOrder[]> {
  return apiRequest(BASE + '?status=' + encodeURIComponent(status), { headers: headers(token) })
}

/**
 * Pedidos con repartidor asignado que aún no lo recogen. Es complementario: si el servidor no lo admite
 * (versión anterior) o falla, se devuelve una lista vacía para no tumbar el resto del panel.
 */
export function listAssignedOrders(token: string): Promise<RestaurantOrder[]> {
  return listOrders(token, 'ASSIGNED').catch(() => [])
}

/** Una acción sobre un pedido propio; solo el rechazo admite un motivo opcional. */
export function actOnOrder(token: string, id: string, action: RestaurantAction, reason?: string): Promise<RestaurantOrder> {
  const trimmed = reason?.trim()
  return apiRequest(BASE + '/' + encodeURIComponent(id) + '/' + action, {
    method: 'POST',
    headers: headers(token),
    ...(action === 'reject' && trimmed ? { body: JSON.stringify({ reason: trimmed }) } : {}),
  })
}
