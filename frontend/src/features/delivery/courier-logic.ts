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
  ASSIGNED: 'Ve al restaurante y pide el pedido con su código.',
  PICKED_UP: 'Lleva el pedido a la dirección del cliente.',
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

/** Pasos que ve el repartidor; el servidor decide siempre las transiciones. */
export const DELIVERY_STEPS = ['Recoger el pedido', 'Entregar al cliente'] as const

/** Paso en curso (0: recoger, 1: entregar). */
export function currentStep(status: OrderStatus): number {
  return status === 'PICKED_UP' ? 1 : 0
}

export interface Place {
  street: string
  district: string
  latitude: number | null
  longitude: number | null
}

/** Enlace para abrir la ruta en la app de mapas del teléfono: al punto exacto si existe, o a la dirección escrita. */
export function navigationUrl(place: Place): string {
  if (place.latitude !== null && place.longitude !== null) {
    return `https://www.google.com/maps/dir/?api=1&destination=${place.latitude},${place.longitude}&travelmode=driving`
  }
  const query = encodeURIComponent(`${place.street}, ${place.district}, Ayacucho, Perú`)
  return `https://www.google.com/maps/search/?api=1&query=${query}`
}

/** Teléfono apto para `tel:`: conserva dígitos y el signo +; devuelve `null` si no hay números. */
export function phoneHref(phone: string | null): string | null {
  if (!phone) return null
  const digits = phone.replace(/[^\d+]/g, '')
  return /\d/.test(digits) ? 'tel:' + digits : null
}
