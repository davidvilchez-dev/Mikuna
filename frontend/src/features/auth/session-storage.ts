export interface StoredSession {
  token: string
  /** Vencimiento en milisegundos desde epoch. */
  expiresAt: number
  /** Rol de la cuenta, para adaptar el menú; las sesiones anteriores no lo traen. */
  role?: string
  /** Nombre de la cuenta, para saludar en el menú; las sesiones anteriores no lo traen. */
  name?: string
}

const SESSION_KEY = 'mikuna.session'

function storage(): Storage | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

export function saveSession(session: StoredSession): void {
  try {
    storage()?.setItem(SESSION_KEY, JSON.stringify(session))
  } catch {
    // Sin almacenamiento disponible la sesión solo dura mientras la pantalla siga abierta.
  }
}

export function clearSession(): void {
  try {
    storage()?.removeItem(SESSION_KEY)
    storage()?.removeItem('mikuna.branch')
  } catch {
    // Nada que limpiar.
  }
}

/** Devuelve la sesión vigente; una sesión vencida o corrupta se descarta y nunca se presenta como válida. */
export function getSession(now: number = Date.now()): StoredSession | null {
  let raw: string | null
  try {
    raw = storage()?.getItem(SESSION_KEY) ?? null
  } catch {
    return null
  }
  if (!raw) return null

  try {
    const parsed = JSON.parse(raw) as Partial<StoredSession>
    if (typeof parsed.token === 'string' && parsed.token && typeof parsed.expiresAt === 'number' && parsed.expiresAt > now) {
      const session: StoredSession = { token: parsed.token, expiresAt: parsed.expiresAt }
      if (typeof parsed.role === 'string') session.role = parsed.role
      if (typeof parsed.name === 'string') session.name = parsed.name
      return session
    }
  } catch {
    // JSON corrupto: se descarta abajo.
  }
  clearSession()
  return null
}

export function hasActiveSession(now: number = Date.now()): boolean {
  return getSession(now) !== null
}
