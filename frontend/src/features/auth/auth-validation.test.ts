import { describe, expect, it } from 'vitest'
import { isValidEmail, roleFromQuery, validateLogin, validateRegistration, type RegistrationForm } from './auth-validation'

const valid: RegistrationForm = {
  name: 'Ana Quispe',
  email: 'ana@mail.com',
  password: 'contraseña-segura',
  passwordConfirmation: 'contraseña-segura',
  role: 'CUSTOMER',
}

describe('validateRegistration', () => {
  it('acepta un formulario válido', () => {
    expect(validateRegistration(valid)).toEqual({})
  })

  it('valida el nombre recortado: obligatorio y hasta 100', () => {
    expect(validateRegistration({ ...valid, name: '   ' }).name).toBeDefined()
    expect(validateRegistration({ ...valid, name: 'x'.repeat(100) }).name).toBeUndefined()
    expect(validateRegistration({ ...valid, name: ' ' + 'x'.repeat(100) + ' ' }).name).toBeUndefined()
    expect(validateRegistration({ ...valid, name: 'x'.repeat(101) }).name).toBeDefined()
  })

  it('valida formato y longitud del correo (254/255)', () => {
    const at254 = 'a'.repeat(245) + '@mail.com'
    expect(at254).toHaveLength(254)
    expect(validateRegistration({ ...valid, email: at254 }).email).toBeUndefined()
    expect(validateRegistration({ ...valid, email: 'a' + at254 }).email).toBeDefined()
    expect(validateRegistration({ ...valid, email: 'sin-arroba' }).email).toBeDefined()
    expect(validateRegistration({ ...valid, email: 'a b@mail.com' }).email).toBeDefined()
    expect(validateRegistration({ ...valid, email: '  Ana@Mail.com ' }).email).toBeUndefined()
  })

  it('valida la contraseña entre 12 y 128 caracteres', () => {
    for (const [length, ok] of [[11, false], [12, true], [128, true], [129, false]] as const) {
      const password = 'x'.repeat(length)
      const errors = validateRegistration({ ...valid, password, passwordConfirmation: password })
      expect(errors.password === undefined).toBe(ok)
    }
  })

  it('exige confirmación coincidente', () => {
    expect(validateRegistration({ ...valid, passwordConfirmation: 'otra-contraseña' }).passwordConfirmation).toBeDefined()
  })

  it('rechaza roles no públicos', () => {
    expect(validateRegistration({ ...valid, role: 'ADMIN' }).role).toBeDefined()
    expect(validateRegistration({ ...valid, role: '' }).role).toBeDefined()
    expect(validateRegistration({ ...valid, role: 'COURIER' }).role).toBeUndefined()
  })
})

describe('validateLogin', () => {
  it('exige correo válido y contraseña', () => {
    expect(validateLogin({ email: 'ana@mail.com', password: 'x' })).toEqual({})
    expect(validateLogin({ email: '', password: '' })).toEqual({
      email: 'Ingresa un correo válido',
      password: 'Ingresa tu contraseña',
    })
  })
})

describe('isValidEmail', () => {
  it('rechaza vacío', () => {
    expect(isValidEmail('   ')).toBe(false)
  })
})

describe('roleFromQuery', () => {
  it('traduce los tipos públicos', () => {
    expect(roleFromQuery('cliente')).toBe('CUSTOMER')
    expect(roleFromQuery('restaurante')).toBe('RESTAURANT')
    expect(roleFromQuery('repartidor')).toBe('COURIER')
  })

  it('usa cliente para valores ausentes o desconocidos', () => {
    expect(roleFromQuery(null)).toBe('CUSTOMER')
    expect(roleFromQuery('admin')).toBe('CUSTOMER')
    expect(roleFromQuery('toString')).toBe('CUSTOMER')
  })
})
