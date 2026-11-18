import { useState } from 'react'
import { Link } from 'react-router'
import { getSession } from '../auth/session-storage'
import { badgeText } from './notifications-logic'
import { useUnreadCount } from './useUnreadCount'
import './notifications.css'

/** Enlace a los avisos con el contador de no leídos; no aparece sin sesión ni en cuentas que no reciben avisos. */
export function NotificationsBell() {
  const [token] = useState(() => getSession()?.token ?? null)
  const unread = useUnreadCount(token)
  if (unread === null) return null
  const text = badgeText(unread)
  const label = unread > 0 ? `Mis avisos, ${unread} sin leer` : 'Mis avisos'

  // Las cuentas que reciben avisos (cliente, restaurante, repartidor) son las mismas que tienen historial (RF-30).
  return (
    <>
      <Link to="/historial" className="notifications-bell">Historial</Link>
      <Link to="/avisos" className="notifications-bell" aria-label={label}>
        <span aria-hidden="true">Avisos</span>
        {text && <span className="notifications-badge" aria-hidden="true">{text}</span>}
      </Link>
    </>
  )
}
