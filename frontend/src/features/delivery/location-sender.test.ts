import { describe, expect, it, vi } from 'vitest'
import {
  LOCATION_INTERVAL_MS,
  createLocationThrottle,
  startLocationSharing,
  type Position,
  type SharingState,
} from './location-sender'

const position: Position = { latitude: -13.15, longitude: -74.22, recordedAt: '2026-11-18T12:00:00.000Z' }

describe('createLocationThrottle', () => {
  it('envía la primera posición y descarta las que llegan antes del intervalo', async () => {
    let clock = 0
    const send = vi.fn().mockResolvedValue(undefined)
    const offer = createLocationThrottle(LOCATION_INTERVAL_MS, send, () => clock)

    expect(await offer(position)).toBe(true)
    clock = LOCATION_INTERVAL_MS - 1
    expect(await offer(position)).toBe(false)
    clock = LOCATION_INTERVAL_MS
    expect(await offer(position)).toBe(true)
    expect(send).toHaveBeenCalledTimes(2)
  })

  it('no inicia un envío mientras otro sigue en curso', async () => {
    let clock = 0
    let finish: () => void = () => undefined
    const send = vi.fn().mockReturnValue(new Promise<void>((resolve) => { finish = resolve }))
    const offer = createLocationThrottle(1, send, () => clock)

    const first = offer(position)
    clock = 1_000
    expect(await offer(position)).toBe(false)
    finish()
    expect(await first).toBe(true)
    expect(send).toHaveBeenCalledTimes(1)
  })

  it('permite reintentar tras un fallo de envío una vez pasado el intervalo', async () => {
    let clock = 0
    const send = vi.fn().mockRejectedValueOnce(new Error('red')).mockResolvedValue(undefined)
    const offer = createLocationThrottle(LOCATION_INTERVAL_MS, send, () => clock)

    await expect(offer(position)).rejects.toThrow('red')
    clock = LOCATION_INTERVAL_MS
    expect(await offer(position)).toBe(true)
  })
})

describe('startLocationSharing', () => {
  it('avisa cuando el navegador no admite geolocalización', () => {
    const states: SharingState[] = []

    startLocationSharing(vi.fn(), (s) => states.push(s), undefined)

    expect(states).toEqual(['unsupported'])
  })

  it('envía la posición del navegador y se detiene al cancelar', async () => {
    const states: SharingState[] = []
    const send = vi.fn().mockResolvedValue(undefined)
    let success: (p: { coords: { latitude: number; longitude: number }; timestamp: number }) => void = () => undefined
    const geolocation = {
      watchPosition: vi.fn((ok) => { success = ok; return 7 }),
      clearWatch: vi.fn(),
    }

    const stop = startLocationSharing(send, (s) => states.push(s), geolocation, () => 0)
    success({ coords: { latitude: -13.15, longitude: -74.22 }, timestamp: Date.parse('2026-11-18T12:00:00Z') })
    await Promise.resolve()
    stop()

    expect(send).toHaveBeenCalledWith({ latitude: -13.15, longitude: -74.22, recordedAt: '2026-11-18T12:00:00.000Z' })
    expect(geolocation.clearWatch).toHaveBeenCalledWith(7)
    expect(states).toEqual(['sharing', 'idle'])
  })

  it('distingue el permiso denegado de otros errores', () => {
    const states: SharingState[] = []
    let fail: (e: { code: number }) => void = () => undefined
    const geolocation = { watchPosition: vi.fn((_ok, error) => { fail = error; return 1 }), clearWatch: vi.fn() }

    startLocationSharing(vi.fn(), (s) => states.push(s), geolocation)
    fail({ code: 1 })
    fail({ code: 2 })

    expect(states).toEqual(['denied', 'error'])
  })
})
