import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR, fetchMe, type Role } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { listNotices, markAllNoticesRead, markNoticeRead, type Notice } from './notifications-api'
import { formatNoticeTime, noticeTarget } from './notifications-logic'
import '../restaurant/restaurant.css'
import './notifications.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; items: Notice[]; page: number; totalPages: number }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Avisos propios (RF-29): del más reciente al más antiguo, con los no leídos destacados. */
export function NotificationsPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [role, setRole] = useState<Role | null>(null)
  const [busy, setBusy] = useState(false)
  const token = session?.token ?? ''

  const fail = useCallback((error: unknown): string => {
    if (error instanceof ApiError && error.status === 401) {
      clearSession()
      navigate('/iniciar-sesion', { replace: true })
      return 'Tu sesión venció'
    }
    return error instanceof ApiError ? error.message : GENERIC_ERROR
  }, [navigate])

  useEffect(() => {
    if (!token) return
    let active = true
    listNotices(token, 0, unreadOnly)
      .then((result) => { if (active) setView({ kind: 'ready', items: result.items, page: result.page, totalPages: result.totalPages }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setView({ kind: 'forbidden' })
        else setView({ kind: 'error', message: fail(error) })
      })
    fetchMe(token).then((me) => { if (active) setRole(me.role) }).catch(() => undefined)
    return () => { active = false }
  }, [token, unreadOnly, fail])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  async function loadMore(current: Extract<ViewState, { kind: 'ready' }>) {
    if (busy) return
    setBusy(true)
    try {
      const result = await listNotices(token, current.page + 1, unreadOnly)
      setView({ kind: 'ready', items: [...current.items, ...result.items], page: result.page, totalPages: result.totalPages })
    } catch (error) {
      setView({ kind: 'error', message: fail(error) })
    } finally {
      setBusy(false)
    }
  }

  async function open(notice: Notice) {
    if (!notice.read) {
      try {
        await markNoticeRead(token, notice.id)
      } catch {
        // Abrir el pedido no depende de poder marcar el aviso.
      }
    }
    navigate(noticeTarget(role, notice.orderId))
  }

  async function markAll() {
    if (busy) return
    setBusy(true)
    try {
      await markAllNoticesRead(token)
      setView((current) => (current.kind === 'ready'
        ? { ...current, items: unreadOnly ? [] : current.items.map((n) => ({ ...n, read: true })) }
        : current))
    } catch (error) {
      setView({ kind: 'error', message: fail(error) })
    } finally {
      setBusy(false)
    }
  }

  if (view.kind === 'forbidden') {
    return (
      <AuthLayout title="Mis avisos">
        <p className="auth-alert" role="alert">Tu cuenta no recibe avisos.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout wide title="Mis avisos" description="Cambios de tus pedidos y entregas, del más reciente al más antiguo.">
      {view.kind === 'loading' && <p className="auth-description" role="status">Cargando…</p>}
      {view.kind === 'error' && <p className="auth-alert" role="alert">{view.message}</p>}
      {view.kind === 'ready' && (
        <>
          <div className="notice-actions">
            <label className="notices-filter">
              <input type="checkbox" checked={unreadOnly} onChange={(e) => { setView({ kind: 'loading' }); setUnreadOnly(e.target.checked) }} />
              Solo no leídos
            </label>
            <button type="button" className="auth-submit auth-submit-secondary" disabled={busy} onClick={() => void markAll()}>
              Marcar todos como leídos
            </button>
          </div>
          {view.items.length === 0 && <p className="auth-description" role="status">{unreadOnly ? 'No tienes avisos sin leer.' : 'Aún no tienes avisos.'}</p>}
          <ul className="notices-list">
            {view.items.map((notice) => (
              <li key={notice.id} className={notice.read ? 'notice' : 'notice notice-unread'}>
                <span>{notice.read ? '' : 'Nuevo · '}{notice.message}</span>
                <span className="notice-time">{formatNoticeTime(notice.createdAt)}</span>
                <div className="notice-actions">
                  <button type="button" className="auth-submit auth-submit-secondary" onClick={() => void open(notice)}>
                    {notice.read ? 'Ver pedido' : 'Marcar como leído y ver pedido'}
                  </button>
                </div>
              </li>
            ))}
          </ul>
          {view.page + 1 < view.totalPages && (
            <button type="button" className="auth-submit" disabled={busy} onClick={() => void loadMore(view)}>Ver más avisos</button>
          )}
        </>
      )}
      <Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link>
    </AuthLayout>
  )
}
