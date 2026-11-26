import { describe, expect, it } from 'vitest'
import {
  ADMIN_PAGE_SIZE,
  DELIVERY_STATUSES,
  ORDER_STATUSES,
  accessLabel,
  canAdminCancel,
  transactionStatusLabels,
  transactionTypeLabels,
  validateCancelReason,
  accountBase,
  accountsPath,
  actionLabel,
  buildAccountQuery,
  buildOrdersQuery,
  emptyAccountFilters,
  formatDateTime,
  formatMoney,
  operationLabel,
  orderStatusLabel,
  pageSummary,
  paymentLabel,
  statusOptionsFor,
  toggleInfo,
  togglePath,
} from './admin-logic'

describe('buildAccountQuery', () => {
  it('omite los filtros vacíos y usa la página y el tamaño', () => {
    expect(buildAccountQuery('users', emptyAccountFilters, 0)).toBe(`?page=0&size=${ADMIN_PAGE_SIZE}`)
  })

  it('en usuarios envía rol y estado de acceso, y recorta la búsqueda', () => {
    const filters = { ...emptyAccountFilters, q: '  ana ', role: 'COURIER' as const, access: 'DISABLED' as const, operation: 'ACTIVE' as const }
    expect(buildAccountQuery('users', filters, 2)).toBe(`?page=2&size=${ADMIN_PAGE_SIZE}&q=ana&role=COURIER&status=DISABLED`)
  })

  it('en restaurantes envía operación y registro, y en repartidores solo la operación', () => {
    const filters = { ...emptyAccountFilters, operation: 'INACTIVE' as const, registration: 'PENDING' as const, role: 'CUSTOMER' as const }
    expect(buildAccountQuery('restaurants', filters, 0)).toBe(`?page=0&size=${ADMIN_PAGE_SIZE}&operation=INACTIVE&registration=PENDING`)
    expect(buildAccountQuery('couriers', filters, 0)).toBe(`?page=0&size=${ADMIN_PAGE_SIZE}&operation=INACTIVE`)
  })
})

describe('rutas', () => {
  it('cada pestaña usa su listado y su base', () => {
    expect(accountsPath('users')).toBe('/api/admin/users')
    expect(accountsPath('restaurants')).toBe('/api/admin/restaurants/search')
    expect(accountsPath('couriers')).toBe('/api/admin/couriers')
    expect(accountBase('restaurants')).toBe('/api/admin/restaurants')
  })

  it('el acceso usa disable/enable y la operación deactivate/activate', () => {
    expect(togglePath('users', 'u 1', false)).toBe('/api/admin/users/u%201/disable')
    expect(togglePath('users', 'u1', true)).toBe('/api/admin/users/u1/enable')
    expect(togglePath('restaurants', 'r1', false)).toBe('/api/admin/restaurants/r1/deactivate')
    expect(togglePath('couriers', 'c1', true)).toBe('/api/admin/couriers/c1/activate')
  })
})

describe('buildOrdersQuery y estados', () => {
  it('añade el estado solo si hay filtro', () => {
    expect(buildOrdersQuery('', 1)).toBe(`?page=1&size=${ADMIN_PAGE_SIZE}`)
    expect(buildOrdersQuery('DELIVERED', 0)).toBe(`?page=0&size=${ADMIN_PAGE_SIZE}&status=DELIVERED`)
  })

  it('las entregas solo filtran por estados en los que existen', () => {
    expect(statusOptionsFor('deliveries')).toEqual(DELIVERY_STATUSES)
    expect(statusOptionsFor('orders')).toEqual(ORDER_STATUSES)
    expect(ORDER_STATUSES).toHaveLength(9)
    expect(DELIVERY_STATUSES).not.toContain('PENDING')
  })
})

describe('etiquetas', () => {
  it('distingue acceso y operación', () => {
    expect(accessLabel(true)).toBe('Acceso habilitado')
    expect(accessLabel(false)).toBe('Acceso bloqueado')
    expect(operationLabel(true, 'users')).toBeNull()
    expect(operationLabel(undefined, 'couriers')).toBeNull()
    expect(operationLabel(false, 'restaurants')).toContain('no recibe pedidos')
    expect(operationLabel(true, 'couriers')).toBe('Candidato a entregas')
    expect(operationLabel(false, 'couriers')).toContain('no es candidato')
  })

  it('describe las acciones registradas', () => {
    expect(actionLabel('DEACTIVATED', 'ACCESS')).toBe('Acceso bloqueado')
    expect(actionLabel('ACTIVATED', 'ACCESS')).toBe('Acceso restablecido')
    expect(actionLabel('DEACTIVATED', 'OPERATION')).toBe('Operación desactivada')
    expect(actionLabel('ACTIVATED', 'OPERATION')).toBe('Operación activada')
  })

  it('el botón y la confirmación dependen de la pestaña y del estado', () => {
    expect(toggleInfo('users', true).button).toBe('Bloquear acceso')
    expect(toggleInfo('users', false).button).toBe('Restablecer acceso')
    expect(toggleInfo('restaurants', true).confirm).toContain('pedidos nuevos')
    expect(toggleInfo('couriers', true).confirm).toContain('entregas nuevas')
    expect(toggleInfo('couriers', false).button).toBe('Activar')
  })

  it('traduce pagos y estados con respaldo', () => {
    expect(paymentLabel('CAPTURED')).toBe('Cobrado')
    expect(paymentLabel('OTRO')).toBe('OTRO')
    expect(paymentLabel(undefined)).toBe('Sin pago')
    expect(orderStatusLabel('DELIVERED')).toBe('Entregado')
    expect(orderStatusLabel('RARO')).toBe('RARO')
  })
})

describe('formato', () => {
  it('resume la página', () => {
    expect(pageSummary(0, 0, 0)).toBe('Sin resultados')
    expect(pageSummary(1, 3, 45)).toBe('Página 2 de 3 · 45 en total')
  })

  it('da formato a importes y fechas, y tolera valores inválidos', () => {
    expect(formatMoney(52.25)).toContain('52.25')
    expect(formatMoney(undefined)).toBe('—')
    expect(formatDateTime('no es fecha')).toBe('no es fecha')
    expect(formatDateTime('2026-11-18T12:00:00Z')).not.toBe('2026-11-18T12:00:00Z')
  })
})

describe('cancelación administrativa (spec 014)', () => {
  it('solo se ofrece de Aceptado a Recogido', () => {
    for (const status of ['ACCEPTED', 'PREPARING', 'READY', 'ASSIGNED', 'PICKED_UP']) expect(canAdminCancel(status)).toBe(true)
    for (const status of ['PENDING', 'REJECTED', 'CANCELLED', 'DELIVERED']) expect(canAdminCancel(status)).toBe(false)
  })

  it('exige un motivo de hasta 200 caracteres', () => {
    expect(validateCancelReason('')).toBe('Indica el motivo de la cancelación')
    expect(validateCancelReason('   ')).toBe('Indica el motivo de la cancelación')
    expect(validateCancelReason('x'.repeat(201))).toBe('El motivo admite hasta 200 caracteres')
    expect(validateCancelReason('  ' + 'x'.repeat(200) + '  ')).toBeNull()
    expect(validateCancelReason('El local cerró')).toBeNull()
  })

  it('nombra el reembolso y las transacciones', () => {
    expect(paymentLabel('REFUNDED')).toBe('Reembolsado')
    expect(transactionTypeLabels.REFUND).toBe('Reembolso')
    expect(transactionStatusLabels.FAILED).toBe('Fallido')
  })
})
