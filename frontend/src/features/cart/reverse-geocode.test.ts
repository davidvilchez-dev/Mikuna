import { describe, expect, it } from 'vitest'
import { matchDistrict } from './address-options'
import { suggestionFrom } from './reverse-geocode'

describe('suggestionFrom', () => {
  it('abrevia el tipo de vía, agrega el número y reconoce el distrito', () => {
    const suggestion = suggestionFrom({
      display_name: 'Jirón Libertad 245, Ayacucho',
      address: { road: 'Jirón Libertad', house_number: '245', city_district: 'Distrito de San Juan Bautista' },
    })
    expect(suggestion).toEqual({ street: 'Jr. Libertad 245', district: 'San Juan Bautista', display: 'Jr. Libertad 245 · San Juan Bautista' })
  })

  it('sin calle usa el barrio o el primer tramo del nombre', () => {
    expect(suggestionFrom({ address: { neighbourhood: 'Asentamiento Humano Andrés Avelino Cáceres', city: 'Ayacucho' } })?.street)
      .toBe('Asentamiento Humano Andrés Avelino Cáceres')
    expect(suggestionFrom({ display_name: 'Colegio Federico Froebel, Avenida Los Pinos', address: { amenity: 'Colegio' } })?.street)
      .toBe('Colegio Federico Froebel')
  })

  it('deja el distrito vacío si no coincide con la zona conocida', () => {
    const suggestion = suggestionFrom({ address: { road: 'Avenida Los Pinos', city: 'Huanta' } })
    expect(suggestion).toEqual({ street: 'Av. Los Pinos', district: null, display: 'Av. Los Pinos' })
  })

  it('sin dirección no sugiere nada', () => {
    expect(suggestionFrom({})).toBeNull()
    expect(suggestionFrom({ address: {} })).toBeNull()
  })
})

describe('matchDistrict', () => {
  it('ignora tildes y mayúsculas y reconoce nombres largos', () => {
    expect(matchDistrict('JESUS NAZARENO')).toBe('Jesús Nazareno')
    expect(matchDistrict('Distrito de Andrés Avelino Cáceres Dorregaray')).toBe('Andrés Avelino Cáceres Dorregaray')
    expect(matchDistrict('Huanta')).toBeNull()
    expect(matchDistrict(undefined)).toBeNull()
  })
})
