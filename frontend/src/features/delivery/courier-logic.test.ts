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
  currentStep,
  navigationUrl,
  phoneHref,
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

describe('currentStep', () => {
  it('es recoger hasta que el pedido se recoge y entregar después', () => {
    expect(currentStep('ASSIGNED')).toBe(0)
    expect(currentStep('PICKED_UP')).toBe(1)
  })
})

describe('navigationUrl', () => {
  it('usa el punto exacto cuando el cliente lo marcó', () => {
    expect(navigationUrl({ street: 'Jr. Lima 123', district: 'Ayacucho', latitude: -13.1588, longitude: -74.2233 }))
      .toBe('https://www.google.com/maps/dir/?api=1&destination=-13.1588,-74.2233&travelmode=driving')
  })

  it('sin punto busca la dirección escrita dentro de Ayacucho', () => {
    const url = navigationUrl({ street: 'Jr. Lima 123', district: 'San Juan Bautista', latitude: null, longitude: null })
    expect(url).toContain('/maps/search/')
    expect(decodeURIComponent(url)).toContain('Jr. Lima 123, San Juan Bautista, Ayacucho')
  })
})

describe('phoneHref', () => {
  it('limpia el teléfono para marcarlo y no inventa uno', () => {
    expect(phoneHref('+51 966 123 456')).toBe('tel:+51966123456')
    expect(phoneHref('(066) 31-2345')).toBe('tel:066312345')
    expect(phoneHref(null)).toBeNull()
    expect(phoneHref('sin teléfono')).toBeNull()
  })
})
