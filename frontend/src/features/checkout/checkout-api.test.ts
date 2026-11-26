import { describe, expect, it } from 'vitest'
import { paymentStatusLabel } from './checkout-api'

describe('paymentStatusLabel (spec 014)', () => {
  it('con tarjeta muestra el reembolso', () => {
    expect(paymentStatusLabel('CARD', 'REFUNDED')).toBe('Reembolsado (te devolvimos el total)')
    expect(paymentStatusLabel('CARD', 'CAPTURED')).toBe('Cobrado')
  })

  it('el efectivo queda pendiente hasta la entrega', () => {
    expect(paymentStatusLabel('CASH', 'AUTHORIZED')).toBe('Efectivo: pagarás al recibir el pedido')
    expect(paymentStatusLabel('CASH', 'CAPTURED')).toBe('Pagado en efectivo')
    expect(paymentStatusLabel('CASH', 'RELEASED')).toBe('Efectivo: pedido sin cobro')
  })
})
