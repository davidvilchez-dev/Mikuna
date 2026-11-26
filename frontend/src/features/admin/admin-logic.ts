import type { OrderStatus } from '../restaurant/orders-logic'
import { orderStatusLabels } from '../restaurant/orders-logic'

export const ADMIN_PAGE_SIZE = 20

export type AccountTab = 'users' | 'restaurants' | 'couriers'
export type UserRole = 'CUSTOMER' | 'RESTAURANT' | 'COURIER'
export type RegistrationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type OperationFilter = 'ACTIVE' | 'INACTIVE'
export type OrdersTab = 'orders' | 'deliveries'

export interface AccountFilters {
  q: string
  /** Solo en la pestaña Usuarios. */
  role: UserRole | ''
  /** Solo en la pestaña Usuarios: estado de acceso. */
  access: 'ENABLED' | 'DISABLED' | ''
  /** Solo en Restaurantes y Repartidores: estado de operación. */
  operation: OperationFilter | ''
  /** Solo en Restaurantes: estado de registro (spec 003). */
  registration: RegistrationStatus | ''
}

export const emptyAccountFilters: AccountFilters = { q: '', role: '', access: '', operation: '', registration: '' }

export const tabLabels: Record<AccountTab, string> = {
  users: 'Usuarios',
  restaurants: 'Restaurantes',
  couriers: 'Repartidores',
}

export const roleLabels: Record<UserRole, string> = {
  CUSTOMER: 'Cliente',
  RESTAURANT: 'Restaurante',
  COURIER: 'Repartidor',
}

export const registrationLabels: Record<RegistrationStatus, string> = {
  PENDING: 'Pendiente de aprobación',
  APPROVED: 'Aprobado',
  REJECTED: 'Rechazado',
}

/** Estados en los que existe una entrega: nace al quedar el pedido Listo para recoger (spec 008). */
export const DELIVERY_STATUSES: OrderStatus[] = ['READY', 'ASSIGNED', 'PICKED_UP', 'DELIVERED']
export const ORDER_STATUSES = Object.keys(orderStatusLabels) as OrderStatus[]

export function statusOptionsFor(tab: OrdersTab): OrderStatus[] {
  return tab === 'deliveries' ? DELIVERY_STATUSES : ORDER_STATUSES
}

/** Ruta del listado de cada pestaña de cuentas. */
export function accountsPath(tab: AccountTab): string {
  switch (tab) {
    case 'restaurants':
      return '/api/admin/restaurants/search'
    case 'couriers':
      return '/api/admin/couriers'
    default:
      return '/api/admin/users'
  }
}

/** Ruta base de detalle y acciones de cada pestaña. */
export function accountBase(tab: AccountTab): string {
  return tab === 'restaurants' ? '/api/admin/restaurants' : tab === 'couriers' ? '/api/admin/couriers' : '/api/admin/users'
}

/** Query del listado de cuentas: omite los filtros vacíos y los que no aplican a la pestaña. */
export function buildAccountQuery(tab: AccountTab, filters: AccountFilters, page: number): string {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(ADMIN_PAGE_SIZE))
  const q = filters.q.trim()
  if (q) params.set('q', q)
  if (tab === 'users') {
    if (filters.role) params.set('role', filters.role)
    if (filters.access) params.set('status', filters.access)
  } else {
    if (filters.operation) params.set('operation', filters.operation)
    if (tab === 'restaurants' && filters.registration) params.set('registration', filters.registration)
  }
  return '?' + params.toString()
}

/** Query del listado de pedidos o entregas. */
export function buildOrdersQuery(status: OrderStatus | '', page: number): string {
  const params = new URLSearchParams()
  params.set('page', String(page))
  params.set('size', String(ADMIN_PAGE_SIZE))
  if (status) params.set('status', status)
  return '?' + params.toString()
}

export function accessLabel(enabled: boolean): string {
  return enabled ? 'Acceso habilitado' : 'Acceso bloqueado'
}

/** La operación solo existe para restaurantes y repartidores; en clientes no se muestra. */
export function operationLabel(enabled: boolean | undefined, tab: AccountTab): string | null {
  if (enabled === undefined || tab === 'users') return null
  if (tab === 'restaurants') return enabled ? 'Recibe pedidos' : 'Desactivado (no recibe pedidos)'
  return enabled ? 'Candidato a entregas' : 'Desactivado (no es candidato)'
}

export function actionLabel(action: string, scope: string): string {
  const activated = action === 'ACTIVATED'
  if (scope === 'ACCESS') return activated ? 'Acceso restablecido' : 'Acceso bloqueado'
  return activated ? 'Operación activada' : 'Operación desactivada'
}

/** Texto del botón y de la confirmación según la pestaña y el estado actual. */
export function toggleInfo(tab: AccountTab, enabledNow: boolean): { button: string; confirm: string } {
  if (tab === 'users') {
    return enabledNow
      ? { button: 'Bloquear acceso', confirm: 'La persona ya no podrá iniciar sesión. ¿Bloquear su acceso?' }
      : { button: 'Restablecer acceso', confirm: '¿Restablecer el acceso de esta persona?' }
  }
  const who = tab === 'restaurants' ? 'restaurante' : 'repartidor'
  return enabledNow
    ? { button: 'Desactivar', confirm: `El ${who} dejará de recibir ${tab === 'restaurants' ? 'pedidos nuevos' : 'entregas nuevas'}. ¿Desactivarlo?` }
    : { button: 'Activar', confirm: `¿Activar de nuevo al ${who}?` }
}

/** Ruta de la acción para la pestaña: el cambio de acceso usa disable/enable y la operación deactivate/activate. */
export function togglePath(tab: AccountTab, id: string, enableNow: boolean): string {
  const verb = tab === 'users' ? (enableNow ? 'enable' : 'disable') : enableNow ? 'activate' : 'deactivate'
  return `${accountBase(tab)}/${encodeURIComponent(id)}/${verb}`
}

export function pageSummary(page: number, totalPages: number, totalItems: number): string {
  if (totalItems === 0) return 'Sin resultados'
  return `Página ${page + 1} de ${Math.max(totalPages, 1)} · ${totalItems} en total`
}

const money = new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' })
export function formatMoney(value: number | undefined | null): string {
  return value === undefined || value === null ? '—' : money.format(value)
}

const dateTime = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' })
export function formatDateTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : dateTime.format(date)
}

export const paymentLabels: Record<string, string> = {
  AUTHORIZED: 'Reservado',
  CAPTURED: 'Cobrado',
  RELEASED: 'Liberado',
  REFUNDED: 'Reembolsado',
}

/** Spec 014: estados en los que el administrador puede cancelar (aceptado y aún no entregado). */
export const ADMIN_CANCELLABLE: OrderStatus[] = ['ACCEPTED', 'PREPARING', 'READY', 'ASSIGNED', 'PICKED_UP']

export function canAdminCancel(status: string): boolean {
  return (ADMIN_CANCELLABLE as string[]).includes(status)
}

export const CANCEL_REASON_MAX = 200

/** Motivo obligatorio de la cancelación administrativa; devuelve el error o null si es válido. */
export function validateCancelReason(text: string): string | null {
  const reason = text.trim()
  if (!reason) return 'Indica el motivo de la cancelación'
  if (reason.length > CANCEL_REASON_MAX) return 'El motivo admite hasta 200 caracteres'
  return null
}

export const transactionTypeLabels: Record<string, string> = {
  AUTHORIZATION: 'Reserva',
  CAPTURE: 'Cobro',
  RELEASE: 'Liberación',
  REFUND: 'Reembolso',
}

export const transactionStatusLabels: Record<string, string> = {
  AUTHORIZED: 'Reservado',
  CAPTURED: 'Cobrado',
  RELEASED: 'Liberado',
  REFUNDED: 'Reembolsado',
  FAILED: 'Fallido',
}

export function paymentLabel(status: string | undefined): string {
  return status ? (paymentLabels[status] ?? status) : 'Sin pago'
}

export function orderStatusLabel(status: string): string {
  return orderStatusLabels[status as OrderStatus] ?? status
}

export type Tone = 'ok' | 'bad' | 'warn' | 'info' | 'muted'

/** Color del distintivo según el estado del pedido: solo presentación; el backend decide el estado. */
export function statusTone(status: string): Tone {
  switch (status) {
    case 'DELIVERED':
      return 'ok'
    case 'REJECTED':
    case 'CANCELLED':
      return 'bad'
    case 'PENDING':
      return 'warn'
    default:
      return 'info'
  }
}
