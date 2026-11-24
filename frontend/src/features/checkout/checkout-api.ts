import { apiRequest } from '../auth/auth-api'
import type { Cart } from '../cart/cart-api'
import type { OrderStatus } from '../restaurant/orders-logic'
import type { CheckoutBlocker } from './checkout-validation'

const CHECKOUT = '/api/customer/checkout'
const ORDERS = '/api/customer/orders'

function auth(token: string): HeadersInit {
  return { Authorization: 'Bearer ' + token }
}

export interface Checkout {
  cart: Cart
  ready: boolean
  blockers: CheckoutBlocker[]
  paymentMethods: { code: string; label: string }[]
  publishableKey: string
}

export type PaymentStatus = 'AUTHORIZED' | 'CAPTURED' | 'RELEASED'

export interface Order {
  id: string
  status: OrderStatus
  restaurant: { id: string; name: string }
  lines: { productId: string; name: string; unitPrice: number; quantity: number; lineTotal: number }[]
  delivery: { label: string | null; street: string; district: string; reference: string | null; contactPhone: string | null }
  totals: { subtotal: number; deliveryFee: number; commission: number; total: number }
  payment: { method: string; status: PaymentStatus }
  createdAt: string
  options: OrderOptions
  /** Vencimiento si el restaurante no responde (spec 012) y segundos restantes según el servidor. */
  expiresAt: string | null
  secondsToExpire: number | null
}

export interface OrderOptions {
  storeNote: string | null
  /** `null` es entrega inmediata. */
  scheduledFor: string | null
  /** `null` es "yo mismo". */
  receiver: { name: string; phone: string; note: string | null } | null
}

export type PaymentChoice = 'CARD' | 'CASH'

export interface ConfirmOptions {
  storeNote: string | null
  scheduledFor: string | null
  receiver: { name: string; phone: string; note: string | null } | null
}

export function getCheckout(token: string): Promise<Checkout> {
  return apiRequest(CHECKOUT, { headers: auth(token) })
}

/** El monto reservado es siempre el que recalcula el servidor; `expectedTotal` solo se compara con él. */
export function confirmOrder(token: string, confirmationKey: string, method: PaymentChoice, paymentMethodId: string | null, expectedTotal: number, options: ConfirmOptions): Promise<Order> {
  return apiRequest(ORDERS, {
    method: 'POST',
    headers: { ...auth(token), 'Idempotency-Key': confirmationKey },
    body: JSON.stringify({ paymentMethod: method, paymentMethodId, expectedTotal, ...options }),
  })
}

export function getOrder(token: string, id: string): Promise<Order> {
  return apiRequest(ORDERS + '/' + encodeURIComponent(id), { headers: auth(token) })
}

/** Cancela un pedido propio mientras esté Pendiente; la reserva se libera sin cobro (RF-20). */
export function cancelOrder(token: string, id: string): Promise<Order> {
  return apiRequest(ORDERS + '/' + encodeURIComponent(id) + '/cancel', { method: 'POST', headers: auth(token) })
}

export function paymentStatusLabel(method: string, status: PaymentStatus): string {
  if (method === 'CASH') return status === 'CAPTURED' ? 'Efectivo: se paga al recibir el pedido' : status === 'RELEASED' ? 'Efectivo: pedido sin cobro' : 'Efectivo: pagas al recibir el pedido'
  return paymentStatusLabels[status]
}

export const paymentStatusLabels: Record<PaymentStatus, string> = {
  AUTHORIZED: 'Reservado (se cobra cuando el restaurante acepte)',
  CAPTURED: 'Cobrado',
  RELEASED: 'Liberado (no se cobró)',
}
