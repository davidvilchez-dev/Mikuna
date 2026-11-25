import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { onSessionExpired } from './session-events'
import { getSession, type StoredSession } from './session-storage'

const SessionContext = createContext<StoredSession | null>(null)

/**
 * Puerta de las rutas con sesión: sin sesión vigente lleva a iniciar sesión, y si el servidor la rechaza después
 * (401, ver apiRequest) también. El permiso por rol (403) lo resuelve cada página con su propio mensaje.
 */
export function RequireSession({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())

  useEffect(() => onSessionExpired(() => navigate('/iniciar-sesion', { replace: true })), [navigate])

  if (!session) return <Navigate to="/iniciar-sesion" replace />
  return <SessionContext.Provider value={session}>{children}</SessionContext.Provider>
}

/** Sesión de la ruta protegida actual; solo se usa dentro de {@link RequireSession}. */
// eslint-disable-next-line react-refresh/only-export-components
export function useSession(): StoredSession {
  const session = useContext(SessionContext)
  if (!session) throw new Error('useSession se usa fuera de RequireSession')
  return session
}
