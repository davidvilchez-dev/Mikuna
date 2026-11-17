export interface ProductInput {
  name: string
  category: string
  description: string
  /** Texto del campo de precio; se convierte al enviarlo. */
  price: string
}

export type ProductErrors = Partial<Record<keyof ProductInput | 'image', string>>

export const MAX_IMAGE_BYTES = 2 * 1024 * 1024
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

const PRICE = /^\d{1,4}(\.\d{1,2})?$/
const MIN_PRICE = 0.01
const MAX_PRICE = 9999.99

function lengthError(value: string, min: number, max: number, message: string): string | undefined {
  const length = value.trim().length
  return length < min || length > max ? message : undefined
}

/** Replica las reglas del backend (que siempre las vuelve a validar) para avisar antes de enviar. */
export function validateProductInput(input: ProductInput): ProductErrors {
  const errors: ProductErrors = {}
  const name = lengthError(input.name, 2, 100, 'El nombre admite de 2 a 100 caracteres')
  const category = lengthError(input.category, 2, 50, 'La categoría admite de 2 a 50 caracteres')
  if (name) errors.name = name
  if (category) errors.category = category
  if (input.description.trim().length > 300) errors.description = 'La descripción admite hasta 300 caracteres'

  const price = input.price.trim()
  if (!PRICE.test(price)) {
    errors.price = 'Ingresa un precio válido con hasta 2 decimales (por ejemplo 18.50)'
  } else if (Number(price) < MIN_PRICE) {
    errors.price = 'El precio debe ser mayor que cero'
  } else if (Number(price) > MAX_PRICE) {
    errors.price = 'El precio máximo es 9999.99'
  }
  return errors
}

/** Imagen de producto: JPG, PNG o WebP, no vacía y de hasta 2 MB. Devuelve el mensaje o undefined. */
export function validateImageFile(file: { type: string; size: number } | null | undefined): string | undefined {
  if (!file || file.size === 0) return 'La imagen es obligatoria'
  if (!(IMAGE_TYPES as readonly string[]).includes(file.type)) return 'La imagen debe ser JPG, PNG o WebP'
  if (file.size > MAX_IMAGE_BYTES) return 'La imagen supera los 2 MB'
  return undefined
}

/** Formatea un precio en soles para mostrarlo («S/ 18.50»). */
export function formatPrice(value: number): string {
  return 'S/ ' + value.toFixed(2)
}
