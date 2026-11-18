import { describe, expect, it } from 'vitest'
import {
  ORDER_REASON_MAX,
  canCustomerCancel,
  orderStatusLabels,
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
