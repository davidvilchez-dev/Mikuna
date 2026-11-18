import type { Role } from '../auth/auth-api'

/** El contador de avisos sin leer se renueva cada 15 s mientras la pestaña esté visible. */
export const UNREAD_POLL_MS = 15_000

/** Texto de la insignia: sin cantidad con 0 avisos y tope «99+» para no desbordar la cabecera. */
export function badgeText(unread: number): string {
  if (unread <= 0) return ''
  return unread > 99 ? '99+' : String(unread)
}

/** Pantalla donde cada rol ve el pedido de un aviso. */
export function noticeTarget(role: Role | null, orderId: string): string {
  if (role === 'CUSTOMER') return '/pedido/' + orderId
  if (role === 'RESTAURANT') return '/mi-restaurante/pedidos'
  if (role === 'COURIER') return '/repartidor/entregas'
  return '/mi-cuenta'
}

export function formatNoticeTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' })
}
