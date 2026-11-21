/** Opciones de la confirmación: horarios de entrega programada y validación de quien recibe. */

const MIN_LEAD_MINUTES = 45
const MAX_LEAD_HOURS = 47
const FIRST_HOUR = 7
const LAST_START_HOUR = 21

export interface Slot {
  /** Inicio de la franja en ISO; es lo que recibe el servidor. */
  value: string
  label: string
}

export interface SlotDay {
  key: string
  title: string
  short: string
  slots: Slot[]
}

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

function clock(d: Date): string {
  const h = d.getHours()
  return `${pad(h % 12 === 0 ? 12 : h % 12)}:${pad(d.getMinutes())} ${h < 12 ? 'AM' : 'PM'}`
}

/** Franjas de una hora (de 7:00 a 22:00) agrupadas por día; el servidor acepta de 25 minutos a 48 horas. */
export function buildSlotDays(now: Date): SlotDay[] {
  const earliest = now.getTime() + MIN_LEAD_MINUTES * 60_000
  const latest = now.getTime() + MAX_LEAD_HOURS * 3_600_000
  const days: SlotDay[] = []
  for (let offset = 0; offset < 3; offset++) {
    const day = new Date(now.getFullYear(), now.getMonth(), now.getDate() + offset)
    const slots: Slot[] = []
    for (let hour = FIRST_HOUR; hour <= LAST_START_HOUR; hour++) {
      const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), hour)
      if (start.getTime() < earliest || start.getTime() > latest) continue
      const end = new Date(start.getTime() + 3_600_000)
      slots.push({ value: start.toISOString(), label: `${clock(start)} - ${clock(end)}` })
    }
    if (slots.length === 0) continue
    days.push({
      key: day.toDateString(),
      title: `${pad(day.getDate())}/${pad(day.getMonth() + 1)}/${day.getFullYear()}`,
      short: `${pad(day.getDate())}/${pad(day.getMonth() + 1)}`,
      slots,
    })
  }
  return days
}

/** Texto de una franja ya elegida, p. ej. «22/11 · 07:00 AM - 08:00 AM». */
export function describeSlot(value: string): string {
  const start = new Date(value)
  const end = new Date(start.getTime() + 3_600_000)
  return `${pad(start.getDate())}/${pad(start.getMonth() + 1)} · ${clock(start)} - ${clock(end)}`
}

const PHONE = /^[0-9+ ()-]{6,20}$/

export function validateReceiver(r: { name: string; phone: string }): string | null {
  if (!r.name.trim()) return 'Indica el nombre de quien recibe.'
  if (!PHONE.test(r.phone.trim())) return 'Indica un teléfono válido de quien recibe.'
  return null
}
