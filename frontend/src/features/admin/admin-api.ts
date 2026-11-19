import { apiRequest } from '../auth/auth-api'
import type { OrderStatus } from '../restaurant/orders-logic'
import {
  accountBase,
  accountsPath,
  buildAccountQuery,
  buildOrdersQuery,
  togglePath,
  type AccountFilters,
  type AccountTab,
} from './admin-logic'

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface AccountSummary {
  /** Identificador del recurso: la cuenta en usuarios y repartidores, el restaurante en restaurantes. */
  id: string
  accountId: string
  name: string
  email?: string
  role: 'CUSTOMER' | 'RESTAURANT' | 'COURIER'
  accessEnabled: boolean
  operationEnabled?: boolean
  registrationStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdAt: string
}

export interface ActionEntry {
  action: 'ACTIVATED' | 'DEACTIVATED'
  scope: 'ACCESS' | 'OPERATION'
  createdAt: string
  adminId: string
  adminName?: string
}

export interface AccountDetail {
  account: AccountSummary
  restaurant?: { category: string; description?: string; address: string; zone: string; phone: string; available: boolean }
  courier?: { available: boolean; freeSince?: string }
  actions: ActionEntry[]
}

export interface Party {
  id: string
  name?: string
  email?: string
}

export interface OrderSummary {
  id: string
  status: OrderStatus
  createdAt: string
  total: number
  customer: Party
  restaurant: Party
  paymentStatus?: string
}

export interface StatusChange {
  from: OrderStatus
  to: OrderStatus
  changedAt: string
  actorName?: string
  actorRole: string
  reason?: string
}

export interface OrderDetail {
  id: string
  status: OrderStatus
  createdAt: string
  customer: Party
  restaurant: Party
  courier?: Party
  deliveryId?: string
  lines: { productId: string; name: string; unitPrice: number; quantity: number; lineTotal: number }[]
  amounts: { subtotal: number; deliveryFee: number; commission: number; total: number }
  payment: { method: string; status?: string; amount?: number; currency?: string }
  deliveryAddress: { street: string; district: string; reference?: string; phone?: string }
  statusChanges: StatusChange[]
}

export interface DeliverySummary {
  id: string
  orderId: string
  status: OrderStatus
  createdAt: string
  courier?: Party
  restaurantName: string
}

export interface DeliveryDetail {
  id: string
  orderId: string
  status: OrderStatus
  readyAt: string
  assignedAt?: string
  courier?: Party
  statusChanges: StatusChange[]
  order: OrderSummary
}

function headers(token: string): HeadersInit {
  return { Authorization: 'Bearer ' + token }
}

export function listAccounts(token: string, tab: AccountTab, filters: AccountFilters, page: number): Promise<Page<AccountSummary>> {
  return apiRequest(accountsPath(tab) + buildAccountQuery(tab, filters, page), { headers: headers(token) })
}

export function accountDetail(token: string, tab: AccountTab, id: string): Promise<AccountDetail> {
  return apiRequest(accountBase(tab) + '/' + encodeURIComponent(id), { headers: headers(token) })
}

/** Activa o desactiva: el cambio efectivo queda registrado en el servidor; repetirlo no cambia nada. */
export function toggleAccount(token: string, tab: AccountTab, id: string, enableNow: boolean): Promise<AccountDetail> {
  return apiRequest(togglePath(tab, id, enableNow), { method: 'POST', headers: headers(token) })
}

/** Total de un listado administrativo sin traer sus filas: pide una sola y lee el total (métricas del panel). */
export async function countOf(token: string, path: string, params: Record<string, string> = {}): Promise<number> {
  const query = new URLSearchParams({ page: '0', size: '1', ...params })
  const result = await apiRequest<Page<unknown>>(path + '?' + query.toString(), { headers: headers(token) })
  return result.totalItems
}

export function listOrders(token: string, status: OrderStatus | '', page: number): Promise<Page<OrderSummary>> {
  return apiRequest('/api/admin/orders' + buildOrdersQuery(status, page), { headers: headers(token) })
}

export function orderDetail(token: string, id: string): Promise<OrderDetail> {
  return apiRequest('/api/admin/orders/' + encodeURIComponent(id), { headers: headers(token) })
}

export function listDeliveries(token: string, status: OrderStatus | '', page: number): Promise<Page<DeliverySummary>> {
  return apiRequest('/api/admin/deliveries' + buildOrdersQuery(status, page), { headers: headers(token) })
}

export function deliveryDetail(token: string, id: string): Promise<DeliveryDetail> {
  return apiRequest('/api/admin/deliveries/' + encodeURIComponent(id), { headers: headers(token) })
}
