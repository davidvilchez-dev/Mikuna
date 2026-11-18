import { apiRequest, type Role } from '../auth/auth-api'
import type { OrderStatus } from '../restaurant/orders-logic'
import { buildQuery, historyPath, type HistoryFilter } from './history-logic'

export interface HistoryLine {
  productId?: string
  name: string
  unitPrice?: number
  quantity: number
  lineTotal?: number
}

/** Una operación del historial. Los campos que el rol no debe ver no llegan (restaurante: sin `restaurant`; repartidor: sin importes). */
export interface HistoryItem {
  orderId: string
  status: OrderStatus
  createdAt: string
  restaurant?: { id: string; name: string }
  customerName?: string
  deliveryAddress?: { street: string; district: string; reference?: string }
  lines: HistoryLine[]
  totals?: { subtotal: number; deliveryFee: number; commission: number; total: number }
  deliveryFee?: number
  payment?: { method: string; status: string }
  rating?: number
  canRate: boolean
}

export interface HistoryPage {
  items: HistoryItem[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

function headers(token: string): HeadersInit {
  return { Authorization: 'Bearer ' + token }
}

export function fetchHistory(role: Role, token: string, filter: HistoryFilter, page: number): Promise<HistoryPage> {
  return apiRequest(historyPath(role) + buildQuery(filter, page), { headers: headers(token) })
}

export interface RatingResult {
  orderId: string
  value: number
  createdAt: string
}

export function rateOrder(token: string, orderId: string, value: number): Promise<RatingResult> {
  return apiRequest('/api/customer/orders/' + encodeURIComponent(orderId) + '/rating', {
    method: 'POST',
    headers: headers(token),
    body: JSON.stringify({ value }),
  })
}
