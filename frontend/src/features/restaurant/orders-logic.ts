/** Estados del pedido (specs 007 y 008) y reglas de interfaz que se derivan de ellos. El backend decide siempre. */
export type OrderStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'PREPARING' | 'READY'
  | 'ASSIGNED' | 'PICKED_UP' | 'DELIVERED'

export const ORDER_REASON_MAX = 200

export const orderStatusLabels: Record<OrderStatus, string> = {
  PENDING: 'Pendiente de aceptación',
  ACCEPTED: 'Aceptado',
  REJECTED: 'Rechazado por el restaurante',
  CANCELLED: 'Cancelado',
  PREPARING: 'En preparación',
  READY: 'Listo para recoger',
  ASSIGNED: 'Repartidor asignado',
  PICKED_UP: 'En camino',
  DELIVERED: 'Entregado',
}

/**
 * Código corto del pedido (#A1B2) para nombrarlo en voz alta: el mismo en las pantallas del cliente, el
 * restaurante, el repartidor y el administrador.
 */
export function orderCode(id: string): string {
  return '#' + id.replace(/-/g, '').slice(-4).toUpperCase()
}

export type RestaurantAction ='accept' | 'reject' | 'start-preparation' | 'mark-ready'

export interface ActionOption {
  action: RestaurantAction
  label: string
}

/** Acciones que el restaurante puede ofrecer según el estado: solo las transiciones permitidas, en orden. */
export function restaurantActions(status: OrderStatus): ActionOption[] {
  switch (status) {
    case 'PENDING':
      return [{ action: 'accept', label: 'Aceptar' }, { action: 'reject', label: 'Rechazar' }]
    case 'ACCEPTED':
      return [{ action: 'start-preparation', label: 'Marcar en preparación' }]
    case 'PREPARING':
      return [{ action: 'mark-ready', label: 'Marcar listo para recoger' }]
    default:
      return []
  }
}

/** El cliente solo puede cancelar mientras el pedido esté Pendiente (después ya está cobrado y no hay reembolsos). */
export function canCustomerCancel(status: OrderStatus): boolean {
  return status === 'PENDING'
}

export function validateOrderReason(reason: string): string | undefined {
  if (reason.trim().length > ORDER_REASON_MAX) return `El motivo admite hasta ${ORDER_REASON_MAX} caracteres`
  return undefined
}

/**
 * Vencimiento de pedidos Pendientes (spec 012). La fecha límite local se calcula desde los segundos restantes que
 * da el servidor al responder, no desde el reloj del navegador, para que un reloj desfasado no altere la cuenta.
 */
export function expiryDeadline(secondsToExpire: number | null | undefined, receivedAt: number): number | null {
  if (secondsToExpire == null) return null
  return receivedAt + Math.max(0, secondsToExpire) * 1000
}

/** Milisegundos que faltan, nunca negativos. */
export function remainingMs(deadline: number, now: number): number {
  return Math.max(0, deadline - now)
}

/** Próximo a vencer: queda el 20 % del plazo total o menos. */
export function isNearExpiry(remaining: number, createdAt: string, expiresAt: string | null): boolean {
  if (!expiresAt) return false
  const total = Date.parse(expiresAt) - Date.parse(createdAt)
  if (!(total > 0)) return remaining > 0
  return remaining <= total * 0.2
}

/** Tiempo restante como m:ss, redondeando hacia arriba para no mostrar 0:00 antes de tiempo. */
export function formatRemaining(ms: number): string {
  const totalSeconds = Math.ceil(Math.max(0, ms) / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}
