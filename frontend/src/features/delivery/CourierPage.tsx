import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import {
  actOnDelivery,
  getAvailability,
  listDeliveries,
  sendLocation,
  setAvailability,
  type Availability,
  type CourierDelivery,
} from './courier-api'
import {
  DELIVERY_POLL_MS,
  DELIVERY_REASON_MAX,
  availabilityLabel,
  courierActions,
  courierDoneMessages,
  courierStatusHints,
  courierStatusLabel,
  formatDestination,
  validateDeliveryReason,
  type CourierAction,
} from './courier-logic'
import { sharingMessages, startLocationSharing, type SharingState } from './location-sender'
import '../restaurant/restaurant.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; availability: Availability; deliveries: CourierDelivery[] }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Disponibilidad y entrega actual del repartidor (RF-21, RF-23, RF-25). Se actualiza sola cada pocos segundos. */
export function CourierPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | undefined>()
  const [busy, setBusy] = useState(false)
  const [sharing, setSharing] = useState<SharingState>('idle')

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
    const load = () => {
      Promise.all([getAvailability(token), listDeliveries(token)])
        .then(([availability, deliveries]) => { if (active) setView({ kind: 'ready', availability, deliveries }) })
        .catch((error: unknown) => {
          if (!active) return
          if (error instanceof ApiError && error.status === 403) setView({ kind: 'forbidden' })
          else {
            const message = fail(error)
            // Un fallo de una actualización periódica no borra lo que ya se muestra.
            setView((current) => (current.kind === 'ready' ? current : { kind: 'error', message }))
          }
        })
    }
    load()
    const timer = setInterval(load, DELIVERY_POLL_MS)
    return () => { active = false; clearInterval(timer) }
  }, [token, attempt, fail])

  // Mientras haya una entrega Asignada o Recogida se comparte la ubicación del navegador (RF-26).
  const hasActiveDelivery = view.kind === 'ready' && view.deliveries.length > 0
  useEffect(() => {
    if (!token || !hasActiveDelivery) return
    return startLocationSharing((position) => sendLocation(token, position), setSharing)
  }, [token, hasActiveDelivery])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  const reload = () => setAttempt((n) => n + 1)

  async function toggle(available: boolean) {
    if (busy) return
    setBusy(true)
    setNotice(null)
    try {
      await setAvailability(token, available)
      setNotice(available ? 'Ahora estás disponible para recibir entregas' : 'Ya no recibirás nuevas entregas')
      reload()
    } catch (error) {
      setNotice(fail(error))
    } finally {
      setBusy(false)
    }
  }

  async function run(delivery: CourierDelivery, action: CourierAction, reasonText?: string) {
    if (busy) return
    setBusy(true)
    setNotice(null)
    try {
      await actOnDelivery(token, delivery.orderId, action, reasonText)
      setNotice(courierDoneMessages[action])
      setRejecting(false)
      setReason('')
      reload()
    } catch (error) {
      // 404/409: la entrega cambió (reasignada o ya registrada); se muestra el motivo y se recarga.
      setNotice(fail(error))
      reload()
    } finally {
      setBusy(false)
    }
  }

  function confirmReject(delivery: CourierDelivery) {
    const error = validateDeliveryReason(reason)
    setReasonError(error)
    if (error) return
    void run(delivery, 'reject', reason)
  }

  if (view.kind === 'forbidden') {
    return (
      <AuthLayout title="Mis entregas">
        <p className="auth-alert" role="alert">Esta sección es solo para repartidores con la cuenta habilitada.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout wide title="Mis entregas" description="Indica si estás disponible y gestiona la entrega que tengas asignada.">
      {notice && <p className="auth-notice" role="status">{notice}</p>}
      {view.kind === 'loading' && <p className="auth-description" role="status">Cargando…</p>}
      {view.kind === 'error' && (
        <>
          <p className="auth-alert" role="alert">{view.message}</p>
          <button type="button" className="auth-submit" onClick={() => { setView({ kind: 'loading' }); reload() }}>Reintentar</button>
        </>
      )}
      {view.kind === 'ready' && (
        <>
          <div className="restaurant-card">
            <h2>{availabilityLabel(view.availability.available)}</h2>
            <div className="restaurant-actions">
              <button type="button" className={view.availability.available ? 'auth-submit auth-submit-secondary' : 'auth-submit'}
                disabled={busy} onClick={() => void toggle(!view.availability.available)}>
                {view.availability.available ? 'Dejar de estar disponible' : 'Estoy disponible'}
              </button>
            </div>
          </div>

          {view.deliveries.length === 0 && (
            <p className="auth-description" role="status">
              {view.availability.available
                ? 'No tienes entregas asignadas. Te asignaremos la siguiente en cuanto haya un pedido listo.'
                : 'No tienes entregas asignadas. Activa tu disponibilidad para recibir pedidos.'}
            </p>
          )}
          {hasActiveDelivery && <p className="auth-description" role="status">{sharingMessages[sharing]}</p>}
          <ul className="restaurant-list">
            {view.deliveries.map((delivery) => (
              <li key={delivery.orderId} className="restaurant-card">
                <h2>{courierStatusLabel(delivery.status)}</h2>
                {courierStatusHints[delivery.status] && <p className="auth-description">{courierStatusHints[delivery.status]}</p>}
                <dl>
                  <div><dt>Recoger en</dt><dd>{delivery.restaurant.name}</dd></div>
                  <div><dt>Productos</dt><dd>{delivery.lines.map((line) => `${line.quantity} x ${line.productName}`).join(', ')}</dd></div>
                  <div><dt>Entregar en</dt><dd>{formatDestination(delivery.delivery)}</dd></div>
                  {delivery.delivery.phone && <div><dt>Contacto</dt><dd>{delivery.delivery.phone}</dd></div>}
                </dl>
                {!rejecting && (
                  <div className="restaurant-actions">
                    {courierActions(delivery.status).map((option) => (
                      <button key={option.action} type="button"
                        className={option.action === 'reject' ? 'auth-submit auth-submit-secondary' : 'auth-submit'}
                        disabled={busy}
                        onClick={() => {
                          if (option.action === 'reject') { setRejecting(true); setReason(''); setReasonError(undefined) }
                          else void run(delivery, option.action)
                        }}>
                        {option.label}
                      </button>
                    ))}
                  </div>
                )}
                {rejecting && (
                  <div className="auth-form">
                    <div className="restaurant-field-textarea">
                      <label htmlFor="delivery-reason">Motivo del rechazo (opcional)</label>
                      <textarea id="delivery-reason" value={reason} maxLength={DELIVERY_REASON_MAX + 50}
                        aria-invalid={reasonError ? true : undefined} onChange={(e) => setReason(e.target.value)} />
                      {reasonError && <p className="auth-error">{reasonError}</p>}
                    </div>
                    <div className="restaurant-actions">
                      <button type="button" className="auth-submit" disabled={busy} onClick={() => confirmReject(delivery)}>Confirmar rechazo</button>
                      <button type="button" className="auth-submit auth-submit-secondary" disabled={busy} onClick={() => setRejecting(false)}>Cancelar</button>
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </>
      )}
      <Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link>
    </AuthLayout>
  )
}
