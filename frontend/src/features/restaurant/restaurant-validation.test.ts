import { describe, expect, it } from 'vitest'
import type { RestaurantInput } from './restaurant-api'
import {
  toDayForms,
  toOpeningHours,
  validateRejectReason,
  validateRestaurantInput,
  validateSchedule,
  type DayForm,
} from './restaurant-validation'

const valid: RestaurantInput = {
  name: 'La Picantería',
  category: 'Criolla',
  description: 'Comida casera',
  address: 'Jr. Lima 123',
  zone: 'Ayacucho',
  phone: '+51 966 123 456',
}

describe('validateRestaurantInput', () => {
  it('acepta datos válidos y una descripción vacía', () => {
    expect(validateRestaurantInput(valid)).toEqual({})
    expect(validateRestaurantInput({ ...valid, description: '' })).toEqual({})
  })

  it('valida el nombre (2 a 100, recortado)', () => {
    expect(validateRestaurantInput({ ...valid, name: ' A ' }).name).toBeDefined()
    expect(validateRestaurantInput({ ...valid, name: 'AB' }).name).toBeUndefined()
    expect(validateRestaurantInput({ ...valid, name: 'x'.repeat(100) }).name).toBeUndefined()
    expect(validateRestaurantInput({ ...valid, name: 'x'.repeat(101) }).name).toBeDefined()
  })

  it('valida la categoría (2 a 50)', () => {
    expect(validateRestaurantInput({ ...valid, category: 'C' }).category).toBeDefined()
    expect(validateRestaurantInput({ ...valid, category: 'x'.repeat(50) }).category).toBeUndefined()
    expect(validateRestaurantInput({ ...valid, category: 'x'.repeat(51) }).category).toBeDefined()
  })

  it('valida la descripción (hasta 300)', () => {
    expect(validateRestaurantInput({ ...valid, description: 'x'.repeat(300) }).description).toBeUndefined()
    expect(validateRestaurantInput({ ...valid, description: 'x'.repeat(301) }).description).toBeDefined()
  })

  it('valida la dirección (5 a 200)', () => {
    expect(validateRestaurantInput({ ...valid, address: 'Jr.' }).address).toBeDefined()
    expect(validateRestaurantInput({ ...valid, address: 'x'.repeat(5) }).address).toBeUndefined()
    expect(validateRestaurantInput({ ...valid, address: 'x'.repeat(201) }).address).toBeDefined()
  })

  it('valida la zona (2 a 100)', () => {
    expect(validateRestaurantInput({ ...valid, zone: 'Z' }).zone).toBeDefined()
    expect(validateRestaurantInput({ ...valid, zone: 'x'.repeat(101) }).zone).toBeDefined()
  })

  it('valida el teléfono (+ opcional, 7 a 15 dígitos o espacios)', () => {
    for (const phone of ['', '123456', '1234567890123456', '96612a456', '++51966123456']) {
      expect(validateRestaurantInput({ ...valid, phone }).phone).toBeDefined()
    }
    for (const phone of ['1234567', '+51966123456', ' 966 123 456 ']) {
      expect(validateRestaurantInput({ ...valid, phone }).phone).toBeUndefined()
    }
  })
})

const week = (overrides: Partial<DayForm> = {}): DayForm[] =>
  toDayForms([]).map((entry, index) => (index === 0 ? { ...entry, ...overrides } : entry))

describe('validateSchedule', () => {
  it('acepta franjas válidas y días cerrados', () => {
    expect(validateSchedule(week())).toEqual({})
    expect(validateSchedule(week({ closed: true, opensAt: '', closesAt: '' }))).toEqual({})
  })

  it('exige apertura y cierre en formato HH:mm', () => {
    expect(validateSchedule(week({ opensAt: '' })).MONDAY).toBeDefined()
    expect(validateSchedule(week({ closesAt: '9:00' })).MONDAY).toBeDefined()
    expect(validateSchedule(week({ closesAt: '24:00' })).MONDAY).toBeDefined()
  })

  it('exige cierre posterior a la apertura', () => {
    expect(validateSchedule(week({ opensAt: '21:00', closesAt: '09:00' })).MONDAY).toBeDefined()
    expect(validateSchedule(week({ opensAt: '09:00', closesAt: '09:00' })).MONDAY).toBeDefined()
    expect(validateSchedule(week({ opensAt: '09:00', closesAt: '09:01' })).MONDAY).toBeUndefined()
  })
})

describe('conversión del horario', () => {
  it('sin horario guardado propone los siete días de 09:00 a 21:00', () => {
    const forms = toDayForms([])
    expect(forms).toHaveLength(7)
    expect(forms.every((entry) => !entry.closed && entry.opensAt === '09:00' && entry.closesAt === '21:00')).toBe(true)
    expect(forms[0].day).toBe('MONDAY')
    expect(forms[6].day).toBe('SUNDAY')
  })

  it('respeta el horario guardado, incluidos los días cerrados', () => {
    const forms = toDayForms([
      { day: 'MONDAY', opensAt: '08:00', closesAt: '12:00' },
      { day: 'TUESDAY', opensAt: null, closesAt: null },
    ])
    expect(forms[0]).toEqual({ day: 'MONDAY', closed: false, opensAt: '08:00', closesAt: '12:00' })
    expect(forms[1].closed).toBe(true)
  })

  it('envía los días cerrados con horas nulas', () => {
    const hours = toOpeningHours(week({ closed: true }))
    expect(hours).toHaveLength(7)
    expect(hours[0]).toEqual({ day: 'MONDAY', opensAt: null, closesAt: null })
    expect(hours[1]).toEqual({ day: 'TUESDAY', opensAt: '09:00', closesAt: '21:00' })
  })
})

describe('validateRejectReason', () => {
  it('exige un motivo de hasta 300 caracteres', () => {
    expect(validateRejectReason('   ')).toBeDefined()
    expect(validateRejectReason('Faltan datos')).toBeUndefined()
    expect(validateRejectReason('x'.repeat(300))).toBeUndefined()
    expect(validateRejectReason('x'.repeat(301))).toBeDefined()
  })
})
