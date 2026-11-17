export interface AddressInput {
  label: string
  street: string
  district: string
  reference: string
  contactPhone: string
}

export type AddressErrors = Partial<Record<keyof AddressInput, string>>

export const MAX_QUANTITY = 20

const PHONE_CHARS = /^[0-9 +()-]+$/

function length(value: string): number {
  return value.trim().length
}

/** Replica las reglas del backend (que siempre las vuelve a validar) para avisar antes de enviar. */
export function validateAddress(input: AddressInput): AddressErrors {
  const errors: AddressErrors = {}
  if (length(input.label) > 50) errors.label = 'La etiqueta admite hasta 50 caracteres'
  if (length(input.street) < 5 || length(input.street) > 150) errors.street = 'La dirección admite de 5 a 150 caracteres'
  if (length(input.district) < 2 || length(input.district) > 100) errors.district = 'El distrito admite de 2 a 100 caracteres'
  if (length(input.reference) > 200) errors.reference = 'La referencia admite hasta 200 caracteres'

  const phone = input.contactPhone.trim()
  if (phone) {
    const digits = phone.replace(/\D/g, '').length
    if (!PHONE_CHARS.test(phone) || digits < 7 || digits > 15) errors.contactPhone = 'El teléfono debe tener de 7 a 15 dígitos'
  }
  return errors
}

/** La cantidad es un entero entre 1 y 20; devuelve el mensaje de error o `undefined` si es válida. */
export function validateQuantity(value: number): string | undefined {
  if (!Number.isInteger(value) || value < 1 || value > MAX_QUANTITY) {
    return `La cantidad debe ser un entero entre 1 y ${MAX_QUANTITY}`
  }
  return undefined
}
