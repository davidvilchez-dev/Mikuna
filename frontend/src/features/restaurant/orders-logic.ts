/** Estados del pedido (spec 007) y reglas de interfaz que se derivan de ellos. El backend decide siempre. */
export type OrderStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'PREPARING' | 'READY'

export const ORDER_REASON_MAX = 200

export const orderStatusLabels: Record<OrderStatus, string> = {
  PENDING: 'Pendiente de aceptación',
  ACCEPTED: 'Aceptado',
  REJECTED: 'Rechazado por el restaurante',
  CANCELLED: 'Cancelado',
  PREPARING: 'En preparación',
  READY: 'Listo para recoger',
}

export type RestaurantAction = 'accept' | 'reject' | 'start-preparation' | 'mark-ready'

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
