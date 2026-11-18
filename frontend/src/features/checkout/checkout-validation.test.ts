import { describe, expect, it } from 'vitest'
import { blockerLabels, confirmationErrorMessage, isValidConfirmationKey, isValidPaymentMethodId, newConfirmationKey } from './checkout-validation'

describe('isValidPaymentMethodId', () => {
  it('acepta identificadores de Stripe y rechaza números de tarjeta u otros textos', () => {
    expect(isValidPaymentMethodId('pm_1Abc_def')).toBe(true)
    expect(isValidPaymentMethodId('4242424242424242')).toBe(false)
    expect(isValidPaymentMethodId('pm_')).toBe(false)
    expect(isValidPaymentMethodId('pm_con espacio')).toBe(false)
    expect(isValidPaymentMethodId('pm_' + 'a'.repeat(253))).toBe(false)
  })
})

describe('isValidConfirmationKey / newConfirmationKey', () => {
  it('valida el formato y las claves generadas lo cumplen', () => {
    expect(isValidConfirmationKey('abc-123_X')).toBe(true)
    expect(isValidConfirmationKey('')).toBe(false)
    expect(isValidConfirmationKey('con espacios')).toBe(false)
    expect(isValidConfirmationKey('a'.repeat(65))).toBe(false)
    expect(isValidConfirmationKey(newConfirmationKey())).toBe(true)
    expect(newConfirmationKey()).not.toBe(newConfirmationKey())
  })
})

describe('confirmationErrorMessage', () => {
  it('traduce los errores de pago y de importes', () => {
    expect(confirmationErrorMessage(402, 'PAYMENT_DECLINED', 'x')).toContain('rechazada')
    expect(confirmationErrorMessage(402, 'PAYMENT_ACTION_REQUIRED', 'x')).toContain('verificación adicional')
    expect(confirmationErrorMessage(503, 'PAYMENT_UNAVAILABLE', 'x')).toContain('ningún cobro')
    expect(confirmationErrorMessage(409, 'AMOUNTS_CHANGED', 'x')).toContain('cambiaron')
    expect(confirmationErrorMessage(409, 'NOT_ORDERABLE', 'detalle del servidor')).toBe('detalle del servidor')
  })

  it('tiene un mensaje para cada bloqueo del resumen', () => {
    expect(Object.keys(blockerLabels)).toHaveLength(4)
  })
})
