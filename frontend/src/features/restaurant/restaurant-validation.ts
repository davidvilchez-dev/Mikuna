import type { OpeningHour, RestaurantInput } from './restaurant-api'

export type RestaurantErrors = Partial<Record<keyof RestaurantInput, string>>

const PHONE = /^\+?[0-9 ]{7,15}$/
const HOUR = /^([01][0-9]|2[0-3]):[0-5][0-9]$/

export const REJECT_REASON_MAX = 300

function lengthError(value: string, min: number, max: number, message: string): string | undefined {
  const length = value.trim().length
  return length < min || length > max ? message : undefined
}

/** Replica las reglas del backend (que siempre las vuelve a validar) para avisar antes de enviar. */
export function validateRestaurantInput(input: RestaurantInput): RestaurantErrors {
  const errors: RestaurantErrors = {}
  const name = lengthError(input.name, 2, 100, 'El nombre admite de 2 a 100 caracteres')
  const category = lengthError(input.category, 2, 50, 'La categoría admite de 2 a 50 caracteres')
  const address = lengthError(input.address, 5, 200, 'La dirección admite de 5 a 200 caracteres')
  const zone = lengthError(input.zone, 2, 100, 'La zona admite de 2 a 100 caracteres')
  if (name) errors.name = name
  if (category) errors.category = category
  if (address) errors.address = address
  if (zone) errors.zone = zone
  if (input.description.trim().length > 300) errors.description = 'La descripción admite hasta 300 caracteres'
  if (!PHONE.test(input.phone.trim())) errors.phone = 'Ingresa un teléfono válido (7 a 15 dígitos)'
  return errors
}

export const WEEK_DAYS = [
  { day: 'MONDAY', label: 'Lunes' },
  { day: 'TUESDAY', label: 'Martes' },
  { day: 'WEDNESDAY', label: 'Miércoles' },
  { day: 'THURSDAY', label: 'Jueves' },
  { day: 'FRIDAY', label: 'Viernes' },
  { day: 'SATURDAY', label: 'Sábado' },
  { day: 'SUNDAY', label: 'Domingo' },
] as const

/** Un día del editor: «cerrado» o una franja de apertura y cierre HH:mm. */
export interface DayForm {
  day: string
  closed: boolean
  opensAt: string
  closesAt: string
}

export type ScheduleErrors = Record<string, string>

export function validateSchedule(days: DayForm[]): ScheduleErrors {
  const errors: ScheduleErrors = {}
  for (const entry of days) {
    if (entry.closed) continue
    if (!HOUR.test(entry.opensAt) || !HOUR.test(entry.closesAt)) {
      errors[entry.day] = 'Indica apertura y cierre'
    } else if (entry.closesAt <= entry.opensAt) {
      errors[entry.day] = 'El cierre debe ser posterior a la apertura'
    }
  }
  return errors
}

/** Siete días en orden; un día cerrado se envía con ambas horas nulas. */
export function toOpeningHours(days: DayForm[]): OpeningHour[] {
  return days.map((entry) => ({
    day: entry.day,
    opensAt: entry.closed ? null : entry.opensAt,
    closesAt: entry.closed ? null : entry.closesAt,
  }))
}

/** Formulario inicial: el horario guardado o, si no hay, todos los días de 09:00 a 21:00. */
export function toDayForms(schedule: OpeningHour[]): DayForm[] {
  return WEEK_DAYS.map(({ day }) => {
    const saved = schedule.find((entry) => entry.day === day)
    if (!saved) return { day, closed: false, opensAt: '09:00', closesAt: '21:00' }
    const closed = saved.opensAt === null || saved.closesAt === null
    return { day, closed, opensAt: saved.opensAt ?? '09:00', closesAt: saved.closesAt ?? '21:00' }
  })
}

export function validateRejectReason(reason: string): string | undefined {
  const length = reason.trim().length
  if (length === 0) return 'El motivo es obligatorio'
  if (length > REJECT_REASON_MAX) return 'El motivo admite hasta 300 caracteres'
  return undefined
}
