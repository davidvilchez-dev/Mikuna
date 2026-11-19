import { describe, expect, it } from 'vitest'
import type { HistoryItem } from '../history/history-api'
import { isoDate, rangeFor, summarize } from './sales-logic'

const item = (status: HistoryItem['status'], subtotal: number): HistoryItem => ({
  orderId: status + subtotal, status, createdAt: '2026-11-19T10:00:00Z', lines: [], canRate: false,
  totals: { subtotal, deliveryFee: 5, commission: 1, total: subtotal + 6 },
})

describe('summarize', () => {
  it('suma solo los productos de pedidos aceptados o entregados', () => {
    const result = summarize([item('DELIVERED', 40), item('PREPARING', 20.5), item('PENDING', 99), item('REJECTED', 30), item('CANCELLED', 10)])
    expect(result).toEqual({ sales: 60.5, orders: 2, average: 30.25, lost: 2 })
  })

  it('sin pedidos todo queda en cero', () => {
    expect(summarize([])).toEqual({ sales: 0, orders: 0, average: 0, lost: 0 })
  })
})

describe('rangeFor', () => {
  const now = new Date(2026, 10, 19, 15, 0)
  it('hoy es un solo día', () => expect(rangeFor('today', now)).toEqual({ from: '2026-11-19', to: '2026-11-19' }))
  it('7 días incluye hoy', () => expect(rangeFor('week', now)).toEqual({ from: '2026-11-13', to: '2026-11-19' }))
  it('30 días cruza de mes', () => expect(rangeFor('month', now)).toEqual({ from: '2026-10-21', to: '2026-11-19' }))
  it('formatea con ceros', () => expect(isoDate(new Date(2026, 0, 5))).toBe('2026-01-05'))
})
