import type { PublicRole } from './auth-api'

/** Reglas espejo del backend (que vuelve a validarlas); solo mejoran la respuesta inmediata. */
export const NAME_MAX = 100
export const EMAIL_MAX = 254
export const PASSWORD_MIN = 12
export const PASSWORD_MAX = 128

const EMAIL_FORMAT = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PUBLIC_ROLES: readonly PublicRole[] = ['CUSTOMER', 'RESTAURANT', 'COURIER']

export interface RegistrationForm {
  name: string
  email: string
  password: string
  passwordConfirmation: string
  role: string
}

export type FieldErrors = Partial<Record<keyof RegistrationForm, string>>

export function isValidEmail(email: string): boolean {
  const normalized = email.trim().toLowerCase()
  return normalized.length > 0 && normalized.length <= EMAIL_MAX && EMAIL_FORMAT.test(normalized)
}

export function validateRegistration(form: RegistrationForm): FieldErrors {
  const errors: FieldErrors = {}
  const name = form.name.trim()
  if (!name || name.length > NAME_MAX) errors.name = 'El nombre es obligatorio y admite hasta 100 caracteres'
  if (!isValidEmail(form.email)) errors.email = 'Ingresa un correo válido de hasta 254 caracteres'
  if (form.password.length < PASSWORD_MIN || form.password.length > PASSWORD_MAX) {
    errors.password = 'La contraseña debe tener entre 12 y 128 caracteres'
  }
  if (form.passwordConfirmation !== form.password) errors.passwordConfirmation = 'Las contraseñas no coinciden'
  if (!PUBLIC_ROLES.includes(form.role as PublicRole)) errors.role = 'Elige un tipo de cuenta válido'
  return errors
}

export function validateLogin(form: { email: string; password: string }): { email?: string; password?: string } {
  const errors: { email?: string; password?: string } = {}
  if (!isValidEmail(form.email)) errors.email = 'Ingresa un correo válido'
  if (!form.password) errors.password = 'Ingresa tu contraseña'
  return errors
}

const ROLE_BY_QUERY: Record<string, PublicRole> = {
  cliente: 'CUSTOMER',
  restaurante: 'RESTAURANT',
  repartidor: 'COURIER',
}

/** Traduce ?tipo= de la URL al rol público; cualquier otro valor (incluido «admin») es cliente. */
export function roleFromQuery(tipo: string | null): PublicRole {
  return (tipo && Object.hasOwn(ROLE_BY_QUERY, tipo)) ? ROLE_BY_QUERY[tipo] : 'CUSTOMER'
}
