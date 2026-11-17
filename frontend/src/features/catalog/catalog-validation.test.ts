import { describe, expect, it } from 'vitest'
import { MAX_IMAGE_BYTES, formatPrice, validateImageFile, validateProductInput, type ProductInput } from './catalog-validation'

const valid: ProductInput = { name: 'Ají de gallina', category: 'Fondos', description: 'Con arroz', price: '18.50' }

describe('validateProductInput', () => {
  it('acepta un producto válido', () => {
    expect(validateProductInput(valid)).toEqual({})
  })

  it('valida el nombre entre 2 y 100 caracteres recortados', () => {
    expect(validateProductInput({ ...valid, name: 'a' }).name).toBeDefined()
    expect(validateProductInput({ ...valid, name: '  a  ' }).name).toBeDefined()
    expect(validateProductInput({ ...valid, name: 'ab' }).name).toBeUndefined()
    expect(validateProductInput({ ...valid, name: 'n'.repeat(100) }).name).toBeUndefined()
    expect(validateProductInput({ ...valid, name: 'n'.repeat(101) }).name).toBeDefined()
  })

  it('valida la categoría entre 2 y 50 caracteres', () => {
    expect(validateProductInput({ ...valid, category: 'c' }).category).toBeDefined()
    expect(validateProductInput({ ...valid, category: 'c'.repeat(50) }).category).toBeUndefined()
    expect(validateProductInput({ ...valid, category: 'c'.repeat(51) }).category).toBeDefined()
  })

  it('limita la descripción a 300 caracteres y es opcional', () => {
    expect(validateProductInput({ ...valid, description: '' }).description).toBeUndefined()
    expect(validateProductInput({ ...valid, description: 'd'.repeat(300) }).description).toBeUndefined()
    expect(validateProductInput({ ...valid, description: 'd'.repeat(301) }).description).toBeDefined()
  })

  it('exige un precio de 0.01 a 9999.99 con hasta dos decimales', () => {
    for (const price of ['0.01', '1', '9999.99', '18.5', ' 12.00 ']) {
      expect(validateProductInput({ ...valid, price }).price).toBeUndefined()
    }
    for (const price of ['', 'abc', '-5', '1.234', '10000', '12,50', '0', '0.00']) {
      expect(validateProductInput({ ...valid, price }).price).toBeDefined()
    }
  })

  it('distingue el mensaje de precio cero y de precio excedido', () => {
    expect(validateProductInput({ ...valid, price: '0.00' }).price).toContain('mayor que cero')
    expect(validateProductInput({ ...valid, price: '10000' }).price).toBeDefined()
  })
})

describe('validateImageFile', () => {
  it('exige imagen', () => {
    expect(validateImageFile(null)).toBe('La imagen es obligatoria')
    expect(validateImageFile({ type: 'image/png', size: 0 })).toBe('La imagen es obligatoria')
  })

  it('acepta JPG, PNG y WebP hasta 2 MB', () => {
    for (const type of ['image/jpeg', 'image/png', 'image/webp']) {
      expect(validateImageFile({ type, size: 1000 })).toBeUndefined()
    }
    expect(validateImageFile({ type: 'image/png', size: MAX_IMAGE_BYTES })).toBeUndefined()
  })

  it('rechaza otros tipos y tamaños mayores', () => {
    expect(validateImageFile({ type: 'image/gif', size: 1000 })).toBe('La imagen debe ser JPG, PNG o WebP')
    expect(validateImageFile({ type: 'application/pdf', size: 1000 })).toBeDefined()
    expect(validateImageFile({ type: 'image/png', size: MAX_IMAGE_BYTES + 1 })).toBe('La imagen supera los 2 MB')
  })
})

describe('formatPrice', () => {
  it('muestra soles con dos decimales', () => {
    expect(formatPrice(18.5)).toBe('S/ 18.50')
    expect(formatPrice(5)).toBe('S/ 5.00')
  })
})
