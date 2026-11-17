import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import {
  approveRestaurant,
  listRestaurants,
  rejectRestaurant,
  type AdminRestaurant,
  type RestaurantStatus,
} from './restaurant-api'
import { REJECT_REASON_MAX, validateRejectReason } from './restaurant-validation'
import './restaurant.css'

const filters: { status: RestaurantStatus; label: string }[] = [
  { status: 'PENDING', label: 'Pendientes' },
  { status: 'APPROVED', label: 'Aprobados' },
  { status: 'REJECTED', label: 'Rechazados' },
]

const dateFormat = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' })

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; items: AdminRestaurant[] }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Revisión de solicitudes de restaurantes (RF-06): lista, aprueba y rechaza con motivo. */
export function AdminRequestsPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [status, setStatus] = useState<RestaurantStatus>('PENDING')
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | undefined>()
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
    listRestaurants(token, status)
      .then((items) => { if (active) setState({ kind: 'ready', items }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setState({ kind: 'forbidden' })
        else setState({ kind: 'error', message: fail(error) })
      })
    return () => { active = false }
  }, [token, status, attempt, fail])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  const reload = () => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }

  async function resolve(item: AdminRestaurant, action: () => Promise<AdminRestaurant>, done: string) {
    if (busy) return
    setBusy(true)
    setNotice(null)
    try {
      await action()
      setNotice(`${item.name}: ${done}`)
      setRejectingId(null)
      setReason('')
      reload()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setNotice(`${item.name}: otra persona ya resolvió esta solicitud. Se actualizó la lista.`)
        reload()
      } else {
        setNotice(fail(error))
      }
    } finally {
      setBusy(false)
    }
  }

  function confirmReject(item: AdminRestaurant) {
    const error = validateRejectReason(reason)
    setReasonError(error)
    if (error) return
    void resolve(item, () => rejectRestaurant(token, item.id, reason.trim()), 'solicitud rechazada')
  }

  if (state.kind === 'forbidden') {
    return (
      <AuthLayout title="Solicitudes de restaurantes">
        <p className="auth-alert" role="alert">Esta sección es solo para administradores habilitados.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout wide title="Solicitudes de restaurantes" description="Revisa las solicitudes y decide cuáles pueden recibir pedidos.">
      <div className="restaurant-filter" role="group" aria-label="Filtrar por estado">
        {filters.map((filter) => (
          <button key={filter.status} type="button" aria-pressed={status === filter.status}
            onClick={() => { setStatus(filter.status); setState({ kind: 'loading' }); setRejectingId(null); setNotice(null) }}>
            {filter.label}
          </button>
        ))}
      </div>
      {notice && <p className="auth-notice" role="status">{notice}</p>}

      {state.kind === 'loading' && <p className="auth-description" role="status">Cargando solicitudes…</p>}
      {state.kind === 'error' && (
        <>
          <p className="auth-alert" role="alert">{state.message}</p>
          <button type="button" className="auth-submit" onClick={reload}>Reintentar</button>
        </>
      )}
      {state.kind === 'ready' && state.items.length === 0 && (
        <p className="auth-description" role="status">No hay restaurantes en este estado.</p>
      )}
      {state.kind === 'ready' && state.items.length > 0 && (
        <ul className="restaurant-list">
          {state.items.map((item) => (
            <li key={item.id} className="restaurant-card">
              <h2>{item.name}</h2>
              <dl>
                <div><dt>Categoría</dt><dd>{item.category}</dd></div>
                <div><dt>Zona</dt><dd>{item.zone}</dd></div>
                <div><dt>Dirección</dt><dd>{item.address}</dd></div>
                <div><dt>Teléfono</dt><dd>{item.phone}</dd></div>
                <div><dt>Titular</dt><dd>{item.ownerName} ({item.ownerEmail})</dd></div>
                <div><dt>Enviada</dt><dd>{dateFormat.format(new Date(item.submittedAt))}</dd></div>
                {item.description && <div><dt>Descripción</dt><dd>{item.description}</dd></div>}
                {item.status === 'APPROVED' && (
                  <div><dt>Habilitado para recibir pedidos</dt><dd>{item.acceptingOrders ? 'Sí' : 'No'} · {item.available ? 'Disponible' : 'En pausa'}</dd></div>
                )}
                {item.status === 'REJECTED' && <div><dt>Motivo del rechazo</dt><dd>{item.rejectionReason}</dd></div>}
              </dl>

              {item.status === 'PENDING' && rejectingId !== item.id && (
                <div className="restaurant-actions">
                  <button type="button" className="auth-submit" disabled={busy}
                    onClick={() => void resolve(item, () => approveRestaurant(token, item.id), 'solicitud aprobada')}>Aprobar</button>
                  <button type="button" className="auth-submit auth-submit-secondary" disabled={busy}
                    onClick={() => { setRejectingId(item.id); setReason(''); setReasonError(undefined) }}>Rechazar</button>
                </div>
              )}
              {item.status === 'PENDING' && rejectingId === item.id && (
                <div className="auth-form">
                  <div className="restaurant-field-textarea">
                    <label htmlFor={'reason-' + item.id}>Motivo del rechazo (obligatorio)</label>
                    <textarea id={'reason-' + item.id} value={reason} maxLength={REJECT_REASON_MAX + 50}
                      aria-invalid={reasonError ? true : undefined} onChange={(e) => setReason(e.target.value)} />
                    {reasonError && <p className="auth-error">{reasonError}</p>}
                  </div>
                  <div className="restaurant-actions">
                    <button type="button" className="auth-submit" disabled={busy} onClick={() => confirmReject(item)}>Confirmar rechazo</button>
                    <button type="button" className="auth-submit auth-submit-secondary" disabled={busy} onClick={() => setRejectingId(null)}>Cancelar</button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      <Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link>
    </AuthLayout>
  )
}
