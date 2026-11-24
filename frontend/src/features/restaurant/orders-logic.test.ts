import { describe, expect, it } from 'vitest'
import {
  ORDER_REASON_MAX,
  canCustomerCancel,
  expiryDeadline,
  formatRemaining,
  isNearExpiry,
  orderStatusLabels,
  remainingMs,
  restaurantActions,
  validateOrderReason,
  type OrderStatus,
} from './orders-logic'

const all: OrderStatus[] = [
  'PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'PREPARING', 'READY', 'ASSIGNED', 'PICKED_UP', 'DELIVERED',
]

describe('restaurantActions', () => {
  it('ofrece aceptar o rechazar solo en Pendiente', () => {
    expect(restaurantActions('PENDING').map((a) => a.action)).toEqual(['accept', 'reject'])
  })

  it('avanza en orden: Aceptado → En preparación → Listo, sin saltos', () => {
    expect(restaurantActions('ACCEPTED').map((a) => a.action)).toEqual(['start-preparation'])
    expect(restaurantActions('PREPARING').map((a) => a.action)).toEqual(['mark-ready'])
  })

  it('no ofrece nada en los estados finales', () => {
    for (const status of ['REJECTED', 'CANCELLED', 'READY', 'ASSIGNED', 'PICKED_UP', 'DELIVERED'] as OrderStatus[]) {
      expect(restaurantActions(status)).toEqual([])
    }
  })
})

describe('canCustomerCancel', () => {
  it('permite cancelar solo un pedido Pendiente', () => {
    expect(all.filter(canCustomerCancel)).toEqual(['PENDING'])
  })
})

describe('orderStatusLabels', () => {
  it('tiene una etiqueta en español para cada estado', () => {
    for (const status of all) expect(orderStatusLabels[status].length).toBeGreaterThan(0)
  })
})

describe('validateOrderReason', () => {
  it('acepta un motivo vacío o hasta el máximo', () => {
    expect(validateOrderReason('')).toBeUndefined()
    expect(validateOrderReason('x'.repeat(ORDER_REASON_MAX))).toBeUndefined()
  })

  it('rechaza un motivo más largo que el máximo', () => {
    expect(validateOrderReason('x'.repeat(ORDER_REASON_MAX + 1))).toMatch(/200/)
  })
})

describe('vencimiento de pedidos pendientes (spec 012)', () => {
  it('fija la fecha límite desde los segundos del servidor, no desde el reloj local', () => {
    expect(expiryDeadline(90, 1_000)).toBe(91_000)
    expect(expiryDeadline(-5, 1_000)).toBe(1_000)
    expect(expiryDeadline(null, 1_000)).toBeNull()
    expect(expiryDeadline(undefined, 1_000)).toBeNull()
  })

  it('nunca devuelve tiempo negativo', () => {
    expect(remainingMs(10_000, 4_000)).toBe(6_000)
    expect(remainingMs(10_000, 20_000)).toBe(0)
  })

  it('marca próximo a vencer en el último 20 % del plazo', () => {
    const created = '2026-11-22T12:00:00Z'
    const expires = '2026-11-22T12:15:00Z' // 15 min: el 20 % son 3 min
    expect(isNearExpiry(3 * 60_000, created, expires)).toBe(true)
    expect(isNearExpiry(3 * 60_000 + 1, created, expires)).toBe(false)
    expect(isNearExpiry(0, created, expires)).toBe(true)
    expect(isNearExpiry(60_000, created, null)).toBe(false)
  })

  it('formatea m:ss redondeando hacia arriba', () => {
    expect(formatRemaining(15 * 60_000)).toBe('15:00')
    expect(formatRemaining(61_000)).toBe('1:01')
    expect(formatRemaining(500)).toBe('0:01')
    expect(formatRemaining(0)).toBe('0:00')
    expect(formatRemaining(-3_000)).toBe('0:00')
  })
})
