import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession, getSession, hasActiveSession, saveSession } from './session-storage'

const KEY = 'mikuna.session'

afterEach(() => {
  sessionStorage.clear()
  vi.restoreAllMocks()
})

describe('session-storage', () => {
  it('guarda y recupera una sesión vigente', () => {
    saveSession({ token: 'abc', expiresAt: 2_000 })
    expect(getSession(1_000)).toEqual({ token: 'abc', expiresAt: 2_000 })
    expect(hasActiveSession(1_000)).toBe(true)
  })

  it('descarta y borra una sesión vencida', () => {
    saveSession({ token: 'abc', expiresAt: 1_000 })
    expect(getSession(1_000)).toBeNull()
    expect(sessionStorage.getItem(KEY)).toBeNull()
    expect(hasActiveSession(1_000)).toBe(false)
  })

  it('descarta un JSON corrupto o incompleto', () => {
    sessionStorage.setItem(KEY, '{no-json')
    expect(getSession(0)).toBeNull()
    expect(sessionStorage.getItem(KEY)).toBeNull()

    sessionStorage.setItem(KEY, JSON.stringify({ token: '', expiresAt: 5 }))
    expect(getSession(0)).toBeNull()
  })

  it('devuelve null si no hay sesión', () => {
    expect(getSession()).toBeNull()
  })

  it('clearSession vacía la sesión', () => {
    saveSession({ token: 'abc', expiresAt: Date.now() + 60_000 })
    clearSession()
    expect(getSession()).toBeNull()
  })

  it('tolera un almacenamiento que falla', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('lleno') })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('bloqueado') })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('bloqueado') })
    expect(() => saveSession({ token: 'abc', expiresAt: 10 })).not.toThrow()
    expect(getSession(0)).toBeNull()
    expect(() => clearSession()).not.toThrow()
  })
})
