import { describe, expect, it } from 'vitest'
import { MAX_QUANTITY, validateAddress, validateQuantity, type AddressInput } from './cart-validation'

const valid: AddressInput = { label: 'Casa', street: 'Jr. Lima 123', district: 'Ayacucho', reference: 'Frente al parque', contactPhone: '987654321' }

describe('validateAddress', () => {
  it('acepta una dirección válida y los opcionales vacíos', () => {
    expect(validateAddress(valid)).toEqual({})
    expect(validateAddress({ ...valid, label: '', reference: '', contactPhone: '' })).toEqual({})
  })

  it('valida la calle entre 5 y 150 caracteres recortados', () => {
    expect(validateAddress({ ...valid, street: 'abcd' }).street).toBeDefined()
    expect(validateAddress({ ...valid, street: '  abcd  ' }).street).toBeDefined()
    expect(validateAddress({ ...valid, street: 'abcde' }).street).toBeUndefined()
    expect(validateAddress({ ...valid, street: 's'.repeat(150) }).street).toBeUndefined()
    expect(validateAddress({ ...valid, street: 's'.repeat(151) }).street).toBeDefined()
  })

  it('valida el distrito entre 2 y 100 caracteres', () => {
    expect(validateAddress({ ...valid, district: '' }).district).toBeDefined()
    expect(validateAddress({ ...valid, district: 'a' }).district).toBeDefined()
    expect(validateAddress({ ...valid, district: 'ab' }).district).toBeUndefined()
    expect(validateAddress({ ...valid, district: 'd'.repeat(101) }).district).toBeDefined()
  })

  it('limita la etiqueta a 50 y la referencia a 200 caracteres', () => {
    expect(validateAddress({ ...valid, label: 'l'.repeat(50) }).label).toBeUndefined()
    expect(validateAddress({ ...valid, label: 'l'.repeat(51) }).label).toBeDefined()
    expect(validateAddress({ ...valid, reference: 'r'.repeat(200) }).reference).toBeUndefined()
    expect(validateAddress({ ...valid, reference: 'r'.repeat(201) }).reference).toBeDefined()
  })

  it('valida el teléfono: de 7 a 15 dígitos y solo caracteres telefónicos', () => {
    expect(validateAddress({ ...valid, contactPhone: '1234567' }).contactPhone).toBeUndefined()
    expect(validateAddress({ ...valid, contactPhone: '+51 966 123 456' }).contactPhone).toBeUndefined()
    expect(validateAddress({ ...valid, contactPhone: '123456' }).contactPhone).toBeDefined()
    expect(validateAddress({ ...valid, contactPhone: '1'.repeat(16) }).contactPhone).toBeDefined()
    expect(validateAddress({ ...valid, contactPhone: '98765abc' }).contactPhone).toBeDefined()
  })

  it('reporta un mensaje por cada campo inválido', () => {
    const errors = validateAddress({ label: '', street: 'x', district: '', reference: '', contactPhone: 'ab' })
    expect(Object.keys(errors).sort()).toEqual(['contactPhone', 'district', 'street'])
  })
})

describe('validateQuantity', () => {
  it('acepta enteros de 1 a 20', () => {
    expect(validateQuantity(1)).toBeUndefined()
    expect(validateQuantity(MAX_QUANTITY)).toBeUndefined()
  })

  it('rechaza cero, negativos, decimales, NaN y el excedente', () => {
    for (const value of [0, -1, 1.5, Number.NaN, MAX_QUANTITY + 1]) {
      expect(validateQuantity(value)).toBe('La cantidad debe ser un entero entre 1 y 20')
    }
  })
})
