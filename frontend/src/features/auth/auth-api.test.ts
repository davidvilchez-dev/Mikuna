import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { ApiError, apiRequest, errorMessage, GENERIC_ERROR } from './auth-api'
import { onSessionExpired } from './session-events'
import { getSession, saveSession } from './session-storage'

function problem(status: number, detail: string): Response {
  return new Response(JSON.stringify({ detail }), { status, headers: { 'Content-Type': 'application/problem+json' } })
}

describe('apiRequest ante un 401', () => {
  let expired: Mock<() => void>
  let unsubscribe: () => void

  beforeEach(() => {
    saveSession({ token: 'jwt', expiresAt: Date.now() + 60_000 })
    expired = vi.fn<() => void>()
    unsubscribe = onSessionExpired(expired)
  })

  afterEach(() => {
    unsubscribe()
    vi.unstubAllGlobals()
    window.sessionStorage.clear()
  })

  it('con token borra la sesión y avisa que venció', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problem(401, 'Debes iniciar sesión para acceder')))

    await expect(apiRequest('/api/cart', { headers: { Authorization: 'Bearer jwt' } })).rejects.toMatchObject({ status: 401 })

    expect(getSession()).toBeNull()
    expect(expired).toHaveBeenCalledOnce()
  })

  it('sin token (credenciales incorrectas del login) no toca la sesión', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problem(401, 'Correo o contraseña incorrectos')))

    await expect(apiRequest('/api/auth/login', { method: 'POST', body: '{}' })).rejects.toThrow('Correo o contraseña incorrectos')

    expect(getSession()).not.toBeNull()
    expect(expired).not.toHaveBeenCalled()
  })

  it('un 403 con token no cierra la sesión', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(problem(403, 'La cuenta no está habilitada')))

    await expect(apiRequest('/api/cart', { headers: { Authorization: 'Bearer jwt' } })).rejects.toMatchObject({ status: 403 })

    expect(getSession()).not.toBeNull()
    expect(expired).not.toHaveBeenCalled()
  })
})

describe('errorMessage', () => {
  it('usa el detalle de la API o el mensaje genérico', () => {
    expect(errorMessage(new ApiError(409, 'Los importes cambiaron'))).toBe('Los importes cambiaron')
    expect(errorMessage(new Error('interno'))).toBe(GENERIC_ERROR)
  })
})
