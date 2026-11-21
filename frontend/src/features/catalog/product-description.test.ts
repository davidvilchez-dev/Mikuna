import { describe, expect, it } from 'vitest'
import { parseDescription } from './product-description'

describe('parseDescription', () => {
  it('separa la frase corta de las viñetas', () => {
    expect(parseDescription('Pizza familiar.\n- Mozarela\n- Jamón')).toEqual({ summary: 'Pizza familiar.', items: ['Mozarela', 'Jamón'] })
  })
  it('sin viñetas es solo frase corta', () => {
    expect(parseDescription('Solo texto')).toEqual({ summary: 'Solo texto', items: [] })
  })
  it('tolera vacío, nulo y saltos de línea de Windows', () => {
    expect(parseDescription(null)).toEqual({ summary: '', items: [] })
    expect(parseDescription('')).toEqual({ summary: '', items: [] })
    expect(parseDescription('A\r\n- B\r\n\r\n• C')).toEqual({ summary: 'A', items: ['B', 'C'] })
  })
})
