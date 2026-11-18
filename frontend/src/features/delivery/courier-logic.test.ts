import { describe, expect, it } from 'vitest'
import { orderStatusLabels, type OrderStatus } from '../restaurant/orders-logic'
import {
  DELIVERY_REASON_MAX,
  availabilityLabel,
  courierActions,
  courierDoneMessages,
  courierStatusHints,
  courierStatusLabel,
  formatDestination,
  validateDeliveryReason,
} from './courier-logic'

const all = Object.keys(orderStatusLabels) as OrderStatus[]

describe('courierActions', () => {
  it('ofrece recoger o rechazar solo en Asignada', () => {
    expect(courierActions('ASSIGNED').map((a) => a.action)).toEqual(['pickup', 'reject'])
  })

  it('ofrece entregar solo en Recogida, sin saltos ni retrocesos', () => {
    expect(courierActions('PICKED_UP').map((a) => a.action)).toEqual(['complete'])
  })

  it('no ofrece nada en ningún otro estado', () => {
    const others = all.filter((s) => s !== 'ASSIGNED' && s !== 'PICKED_UP')
    for (const status of others) expect(courierActions(status)).toEqual([])
  })
})

describe('etiquetas', () => {
  it('cada estado de reparto tiene una etiqueta en español', () => {
    for (const status of ['READY', 'ASSIGNED', 'PICKED_UP', 'DELIVERED'] as OrderStatus[]) {
      expect(courierStatusLabel(status).length).toBeGreaterThan(0)
    }
    expect(courierStatusHints.ASSIGNED).toBeDefined()
    expect(courierStatusHints.PICKED_UP).toBeDefined()
  })

  it('distingue disponible de no disponible', () => {
    expect(availabilityLabel(true)).not.toBe(availabilityLabel(false))
  })

  it('tiene un mensaje por cada acción', () => {
    expect(Object.keys(courierDoneMessages).sort()).toEqual(['complete', 'pickup', 'reject'])
  })
})

describe('validateDeliveryReason', () => {
  it('acepta un motivo vacío o hasta el máximo', () => {
    expect(validateDeliveryReason('')).toBeUndefined()
    expect(validateDeliveryReason('x'.repeat(DELIVERY_REASON_MAX))).toBeUndefined()
  })

  it('rechaza un motivo más largo que el máximo', () => {
    expect(validateDeliveryReason('x'.repeat(DELIVERY_REASON_MAX + 1))).toMatch(/200/)
  })
})

describe('formatDestination', () => {
  it('une calle y distrito, con la referencia solo si existe', () => {
    expect(formatDestination({ street: 'Jr. Lima 123', district: 'Ayacucho', reference: null })).toBe('Jr. Lima 123, Ayacucho')
    expect(formatDestination({ street: 'Jr. Lima 123', district: 'Ayacucho', reference: 'frente al parque' }))
      .toBe('Jr. Lima 123, Ayacucho (frente al parque)')
  })
})
