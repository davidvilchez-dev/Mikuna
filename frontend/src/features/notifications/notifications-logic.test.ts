import { describe, expect, it } from 'vitest'
import { UNREAD_POLL_MS, badgeText, formatNoticeTime, noticeTarget } from './notifications-logic'

describe('badgeText', () => {
  it('no muestra insignia sin avisos sin leer', () => {
    expect(badgeText(0)).toBe('')
    expect(badgeText(-1)).toBe('')
  })

  it('muestra la cantidad y topa en 99+', () => {
    expect(badgeText(3)).toBe('3')
    expect(badgeText(99)).toBe('99')
    expect(badgeText(100)).toBe('99+')
  })
})

describe('noticeTarget', () => {
  it('lleva a cada rol a la pantalla donde ve el pedido', () => {
    expect(noticeTarget('CUSTOMER', 'abc')).toBe('/pedido/abc')
    expect(noticeTarget('RESTAURANT', 'abc')).toBe('/mi-restaurante/pedidos')
    expect(noticeTarget('COURIER', 'abc')).toBe('/repartidor/entregas')
    expect(noticeTarget(null, 'abc')).toBe('/mi-cuenta')
  })
})

describe('UNREAD_POLL_MS y formatNoticeTime', () => {
  it('sondea cada 15 s', () => {
    expect(UNREAD_POLL_MS).toBe(15_000)
  })

  it('devuelve vacío con una fecha inválida', () => {
    expect(formatNoticeTime('x')).toBe('')
    expect(formatNoticeTime('2026-11-18T12:00:00Z')).not.toBe('')
  })
})
