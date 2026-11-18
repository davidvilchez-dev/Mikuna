import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { formatPrice } from '../catalog/catalog-validation'
import { actOnOrder, listOrders, type RestaurantOrder } from './orders-api'
import {
  ORDER_REASON_MAX,
  orderStatusLabels,
  restaurantActions,
  validateOrderReason,
  type OrderStatus,
  type RestaurantAction,
} from './orders-logic'
import './restaurant.css'

const tabs: { status: OrderStatus; label: string }[] = [
  { status: 'PENDING', label: 'Pendientes' },
  { status: 'ACCEPTED', label: 'Aceptados' },
  { status: 'PREPARING', label: 'En preparación' },
  { status: 'READY', label: 'Listos para recoger' },
]

const dateFormat = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' })

const doneMessages: Record<RestaurantAction, string> = {
  accept: 'Pedido aceptado y cobrado',
  reject: 'Pedido rechazado; la reserva se liberó sin cobro',
  'start-preparation': 'Pedido en preparación',
  'mark-ready': 'Pedido listo para recoger',
}

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; items: RestaurantOrder[] }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Pedidos del restaurante (RF-18, RF-19): acepta o rechaza los pendientes y avanza los aceptados. */
export function OrdersPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [status, setStatus] = useState<OrderStatus>('PENDING')
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
    listOrders(token, status)
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

  async function run(order: RestaurantOrder, action: RestaurantAction, reasonText?: string) {
    if (busy) return
    setBusy(true)
    setNotice(null)
    try {
      await actOnOrder(token, order.id, action, reasonText)
      setNotice(doneMessages[action])
      setRejectingId(null)
      setReason('')
      reload()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        // El estado cambió (p. ej. el cliente canceló a la vez): se muestra el motivo y se recarga la lista.
        setNotice(error.message)
        reload()
      } else {
        setNotice(fail(error))
      }
    } finally {
      setBusy(false)
    }
  }

  function confirmReject(order: RestaurantOrder) {
    const error = validateOrderReason(reason)
    setReasonError(error)
    if (error) return
    void run(order, 'reject', reason)
  }

  if (state.kind === 'forbidden') {
    return (
      <AuthLayout title="Pedidos del restaurante">
        <p className="auth-alert" role="alert">Esta sección es solo para restaurantes aprobados y habilitados.</p>
        <Link to="/mi-restaurante" className="auth-submit auth-submit-secondary">Volver a mi restaurante</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout wide title="Pedidos del restaurante" description="Acepta o rechaza los pedidos nuevos y avanza su preparación.">
      <div className="restaurant-filter" role="group" aria-label="Filtrar por estado">
        {tabs.map((tab) => (
          <button key={tab.status} type="button" aria-pressed={status === tab.status}
            onClick={() => { setStatus(tab.status); setState({ kind: 'loading' }); setRejectingId(null); setNotice(null) }}>
            {tab.label}
          </button>
        ))}
      </div>
      {notice && <p className="auth-notice" role="status">{notice}</p>}

      {state.kind === 'loading' && <p className="auth-description" role="status">Cargando pedidos…</p>}
      {state.kind === 'error' && (
        <>
          <p className="auth-alert" role="alert">{state.message}</p>
          <button type="button" className="auth-submit" onClick={reload}>Reintentar</button>
        </>
      )}
      {state.kind === 'ready' && state.items.length === 0 && (
        <p className="auth-description" role="status">No hay pedidos en este estado.</p>
      )}
      {state.kind === 'ready' && state.items.length > 0 && (
        <ul className="restaurant-list">
          {state.items.map((order) => (
            <li key={order.id} className="restaurant-card">
              <h2>{orderStatusLabels[order.status]}</h2>
              <dl>
                <div><dt>Recibido</dt><dd>{dateFormat.format(new Date(order.createdAt))}</dd></div>
                <div>
                  <dt>Productos</dt>
                  <dd>{order.lines.map((line) => `${line.quantity} x ${line.name}`).join(', ')}</dd>
                </div>
                <div>
                  <dt>Entrega</dt>
                  <dd>{order.delivery.street}, {order.delivery.district}{order.delivery.reference ? ` (${order.delivery.reference})` : ''}</dd>
                </div>
                <div><dt>Total</dt><dd>{formatPrice(order.totals.total)}</dd></div>
              </dl>

              {rejectingId !== order.id && (
                <div className="restaurant-actions">
                  {restaurantActions(order.status).map((option) => (
                    <button key={option.action} type="button"
                      className={option.action === 'reject' ? 'auth-submit auth-submit-secondary' : 'auth-submit'}
                      disabled={busy}
                      onClick={() => {
                        if (option.action === 'reject') { setRejectingId(order.id); setReason(''); setReasonError(undefined) }
                        else void run(order, option.action)
                      }}>
                      {option.label}
                    </button>
                  ))}
                </div>
              )}
              {rejectingId === order.id && (
                <div className="auth-form">
                  <div className="restaurant-field-textarea">
                    <label htmlFor={'reason-' + order.id}>Motivo del rechazo (opcional)</label>
                    <textarea id={'reason-' + order.id} value={reason} maxLength={ORDER_REASON_MAX + 50}
                      aria-invalid={reasonError ? true : undefined} onChange={(e) => setReason(e.target.value)} />
                    {reasonError && <p className="auth-error">{reasonError}</p>}
                  </div>
                  <div className="restaurant-actions">
                    <button type="button" className="auth-submit" disabled={busy} onClick={() => confirmReject(order)}>Confirmar rechazo</button>
                    <button type="button" className="auth-submit auth-submit-secondary" disabled={busy} onClick={() => setRejectingId(null)}>Cancelar</button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      <Link to="/mi-restaurante" className="auth-switch">Volver a mi restaurante</Link>
    </AuthLayout>
  )
}
