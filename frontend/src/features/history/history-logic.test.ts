import { describe, expect, it } from 'vitest'
import {
  HISTORY_PAGE_SIZE,
  buildQuery,
  emptyFilter,
  formatHistoryDate,
  hasHistory,
  historyPath,
  isValidRating,
  mergePages,
  ratingErrorMessage,
  statusOptions,
  validateRange,
} from './history-logic'

describe('isValidRating', () => {
  it('acepta solo enteros de 1 a 5', () => {
    for (const value of [1, 2, 3, 4, 5]) expect(isValidRating(value)).toBe(true)
    for (const value of [0, 6, -1, 3.5, Number.NaN, Number.POSITIVE_INFINITY]) expect(isValidRating(value)).toBe(false)
  })
})

describe('ratingErrorMessage', () => {
  it('traduce los códigos del servidor', () => {
    expect(ratingErrorMessage(409, 'RATING_ALREADY_EXISTS', 'x')).toBe('Este pedido ya fue calificado.')
    expect(ratingErrorMessage(409, 'ORDER_NOT_DELIVERED', 'x')).toBe('Solo puedes calificar un pedido que ya te entregaron.')
    expect(ratingErrorMessage(404, null, 'x')).toBe('No encontramos ese pedido.')
    expect(ratingErrorMessage(500, null, 'falló')).toBe('falló')
  })
})

describe('buildQuery', () => {
  it('sin filtros solo envía la página y el tamaño', () => {
    expect(buildQuery(emptyFilter, 0)).toBe(`?page=0&size=${HISTORY_PAGE_SIZE}`)
  })

  it('incluye los filtros con valor y omite los vacíos', () => {
    expect(buildQuery({ status: 'DELIVERED', from: '2026-11-01', to: '' }, 2))
      .toBe(`?page=2&size=${HISTORY_PAGE_SIZE}&status=DELIVERED&from=2026-11-01`)
    expect(buildQuery({ status: '', from: '2026-11-01', to: '2026-11-18' }, 0))
      .toBe(`?page=0&size=${HISTORY_PAGE_SIZE}&from=2026-11-01&to=2026-11-18`)
  })
})

describe('validateRange', () => {
  it('rechaza un rango invertido y acepta el resto', () => {
    expect(validateRange('2026-11-18', '2026-11-17')).toMatch(/inicial/)
    expect(validateRange('2026-11-17', '2026-11-18')).toBeUndefined()
    expect(validateRange('2026-11-18', '2026-11-18')).toBeUndefined()
    expect(validateRange('', '2026-11-18')).toBeUndefined()
    expect(validateRange('2026-11-18', '')).toBeUndefined()
  })
})

describe('mergePages', () => {
  it('añade la página nueva sin duplicar ids', () => {
    const merged = mergePages([{ orderId: 'a' }, { orderId: 'b' }], [{ orderId: 'b' }, { orderId: 'c' }])
    expect(merged.map((item) => item.orderId)).toEqual(['a', 'b', 'c'])
  })

  it('conserva el orden y no altera la lista previa', () => {
    const previous = [{ orderId: 'a' }]
    expect(mergePages(previous, [])).toEqual([{ orderId: 'a' }])
    expect(mergePages([], [{ orderId: 'z' }])).toEqual([{ orderId: 'z' }])
    expect(previous).toHaveLength(1)
  })
})

describe('roles', () => {
  it('elige el endpoint según el rol', () => {
    expect(historyPath('CUSTOMER')).toBe('/api/customer/orders/history')
    expect(historyPath('RESTAURANT')).toBe('/api/restaurant/orders/history')
    expect(historyPath('COURIER')).toBe('/api/courier/deliveries/history')
  })

  it('el administrador no tiene historial y el repartidor solo filtra sus estados', () => {
    expect(hasHistory('ADMIN')).toBe(false)
    expect(hasHistory(null)).toBe(false)
    expect(hasHistory('CUSTOMER')).toBe(true)
    expect(statusOptions('COURIER')).toEqual(['ASSIGNED', 'PICKED_UP', 'DELIVERED'])
    expect(statusOptions('CUSTOMER')).toContain('REJECTED')
    expect(statusOptions('RESTAURANT')).toHaveLength(9)
  })
})

describe('formatHistoryDate', () => {
  it('formatea fechas válidas y devuelve vacío si no lo son', () => {
    expect(formatHistoryDate('2026-11-18T15:04:05Z')).not.toBe('')
    expect(formatHistoryDate('no-es-fecha')).toBe('')
  })
})
