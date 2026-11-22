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
}

function headers(token: string): HeadersInit {
  return panelHeaders(token)
}

export function listOrders(token: string, status: OrderStatus): Promise<RestaurantOrder[]> {
  return apiRequest(BASE + '?status=' + encodeURIComponent(status), { headers: headers(token) })
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
