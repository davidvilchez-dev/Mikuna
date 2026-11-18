/** Reglas espejo del backend (que siempre las vuelve a validar) para avisar antes de enviar. */

const PAYMENT_METHOD_ID = /^pm_[A-Za-z0-9_]+$/
const CONFIRMATION_KEY = /^[A-Za-z0-9_-]{1,64}$/

/** El identificador de método de pago lo crea Stripe.js; nunca se envían números de tarjeta. */
export function isValidPaymentMethodId(value: string): boolean {
  return value.length <= 255 && PAYMENT_METHOD_ID.test(value)
}

/** Clave de idempotencia de un intento de confirmación (1 a 64 letras, números, guiones o guiones bajos). */
export function isValidConfirmationKey(value: string): boolean {
  return CONFIRMATION_KEY.test(value)
}

export function newConfirmationKey(): string {
  return crypto.randomUUID()
}

export type CheckoutBlocker = 'CART_EMPTY' | 'NO_ADDRESS' | 'RESTAURANT_UNAVAILABLE' | 'LINES_UNAVAILABLE'

export const blockerLabels: Record<CheckoutBlocker, string> = {
  CART_EMPTY: 'Tu carrito está vacío.',
  NO_ADDRESS: 'Selecciona una dirección de entrega en tu carrito.',
  RESTAURANT_UNAVAILABLE: 'El restaurante no está recibiendo pedidos ahora.',
  LINES_UNAVAILABLE: 'Hay productos que ya no están disponibles; quítalos de tu carrito.',
}

/** Mensaje en español para cada código de error de la confirmación; `null` si no se distingue. */
export function confirmationErrorMessage(status: number, code: string | null, detail: string): string {
  if (status === 402) return code === 'PAYMENT_ACTION_REQUIRED' ? 'Tu tarjeta exige una verificación adicional que aún no admitimos. Usa otra tarjeta.' : 'Tu tarjeta fue rechazada. Revisa los datos o usa otra tarjeta.'
  if (status === 503) return 'No pudimos procesar el pago ahora y no se hizo ningún cobro. Intenta de nuevo.'
  if (status === 409 && code === 'AMOUNTS_CHANGED') return 'Los importes cambiaron. Revisa el resumen actualizado y confirma de nuevo.'
  return detail
}
