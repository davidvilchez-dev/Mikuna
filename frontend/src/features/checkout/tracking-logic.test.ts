import { describe, expect, it } from 'vitest'
import { orderStatusLabels, type OrderStatus } from '../restaurant/orders-logic'
import {
  TRACKING_POLL_MS,
  formatAge,
  formatCoordinates,
  formatStageTime,
  isFinalStatus,
  locationReasonMessages,
  mapUrl,
} from './tracking-logic'

describe('isFinalStatus', () => {
  it('detiene el sondeo solo en Entregado, Rechazado y Cancelado', () => {
    const all = Object.keys(orderStatusLabels) as OrderStatus[]
    expect(all.filter(isFinalStatus).sort()).toEqual(['CANCELLED', 'DELIVERED', 'REJECTED'])
  })
})

describe('TRACKING_POLL_MS', () => {
  it('es menor que el máximo de 10 s para ver una nueva posición', () => {
    expect(TRACKING_POLL_MS).toBeLessThan(10_000)
  })
})

describe('formatAge', () => {
  const now = Date.parse('2026-11-18T12:00:00Z')

  it('indica la antigüedad de la ubicación', () => {
    expect(formatAge('2026-11-18T11:59:58Z', now)).toBe('ahora mismo')
    expect(formatAge('2026-11-18T11:59:48Z', now)).toBe('hace 12 s')
    expect(formatAge('2026-11-18T11:57:00Z', now)).toBe('hace 3 min')
    expect(formatAge('2026-11-18T09:00:00Z', now)).toBe('hace 3 h')
  })

  it('no falla con una fecha inválida', () => {
    expect(formatAge('no-es-fecha', now)).toBe('')
  })
})

describe('formatStageTime', () => {
  it('devuelve vacío con una fecha inválida y texto con una válida', () => {
    expect(formatStageTime('x')).toBe('')
    expect(formatStageTime('2026-11-18T12:00:00Z')).not.toBe('')
  })
})

describe('coordenadas y mapa', () => {
  it('formatea con 5 decimales', () => {
    expect(formatCoordinates(-13.1588, -74.2236)).toBe('-13.15880, -74.22360')
  })

  it('arma un enlace a un mapa público con las coordenadas', () => {
    expect(mapUrl(-13.1588, -74.2236)).toBe('https://www.openstreetmap.org/?mlat=-13.15880&mlon=-74.22360#map=17/-13.15880/-74.22360')
  })
})

describe('locationReasonMessages', () => {
  it('explica cada motivo por el que no hay ubicación', () => {
    expect(Object.keys(locationReasonMessages).sort()).toEqual(['FINISHED', 'NOT_PICKED_UP', 'NO_LOCATION_YET'])
    Object.values(locationReasonMessages).forEach((message) => expect(message.length).toBeGreaterThan(10))
  })
})
