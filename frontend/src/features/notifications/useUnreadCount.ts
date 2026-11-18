import { useEffect, useState } from 'react'
import { ApiError } from '../auth/auth-api'
import { getUnreadCount } from './notifications-api'
import { UNREAD_POLL_MS } from './notifications-logic'

/**
 * Cantidad de avisos sin leer, renovada cada 15 s con la pestaña visible. Devuelve {@code null} si no hay sesión
 * o la cuenta no recibe avisos (403), para ocultar la campana. Un fallo de red conserva el último valor.
 */
export function useUnreadCount(token: string | null): number | null {
  const [unread, setUnread] = useState<number | null>(null)

  useEffect(() => {
    if (!token) return
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined

    const load = async () => {
      if (document.visibilityState === 'visible') {
        try {
          const result = await getUnreadCount(token)
          if (!active) return
          setUnread(result.unread)
        } catch (error) {
          if (!active) return
          if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
            setUnread(null)
            return
          }
        }
      }
      if (active) timer = setTimeout(() => void load(), UNREAD_POLL_MS)
    }

    void load()
    return () => {
      active = false
      if (timer) clearTimeout(timer)
    }
  }, [token])

  return token ? unread : null
}
