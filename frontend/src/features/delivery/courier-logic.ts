import { orderStatusLabels, type OrderStatus } from '../restaurant/orders-logic'

/** Reglas de interfaz del reparto (spec 008). El backend decide siempre; aquí solo se ofrecen las acciones válidas. */
export type CourierAction = 'pickup' | 'complete' | 'reject'

export const DELIVERY_REASON_MAX = 200

/** Intervalo de actualización de las pantallas de reparto: los avisos son de la spec 009. */
export const DELIVERY_POLL_MS = 10_000

export interface CourierActionOption {
  action: CourierAction
  label: string
}

/** Acciones del repartidor según el estado de su entrega: Asignada → recoger o rechazar; Recogida → entregar. */
export function courierActions(status: OrderStatus): CourierActionOption[] {
  switch (status) {
    case 'ASSIGNED':
      return [{ action: 'pickup', label: 'Recogí el pedido' }, { action: 'reject', label: 'Rechazar entrega' }]
    case 'PICKED_UP':
      return [{ action: 'complete', label: 'Entregué el pedido' }]
    default:
      return []
  }
}

export const courierStatusHints: Partial<Record<OrderStatus, string>> = {
  ASSIGNED: 'Recoge el pedido en el restaurante o recházalo.',
  PICKED_UP: 'Entrega el pedido al cliente.',
}

export function courierStatusLabel(status: OrderStatus): string {
  return orderStatusLabels[status]
}

export function availabilityLabel(available: boolean): string {
  return available ? 'Disponible para recibir entregas' : 'No disponible'
}

export const courierDoneMessages: Record<CourierAction, string> = {
  pickup: 'Pedido recogido',
  complete: 'Pedido entregado',
  reject: 'Entrega rechazada; se asignará a otro repartidor',
}

export function validateDeliveryReason(reason: string): string | undefined {
  if (reason.trim().length > DELIVERY_REASON_MAX) return `El motivo admite hasta ${DELIVERY_REASON_MAX} caracteres`
  return undefined
}

export interface Destination {
  street: string
  district: string
  reference: string | null
}

/** «Calle, distrito (referencia)» sin paréntesis vacíos. */
export function formatDestination(destination: Destination): string {
  const base = `${destination.street}, ${destination.district}`
  return destination.reference ? `${base} (${destination.reference})` : base
}
