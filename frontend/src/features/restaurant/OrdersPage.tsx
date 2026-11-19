import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AppIcon } from '../auth/AppIcon'
import { RestaurantLayout } from './RestaurantLayout'
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
  { status: 'PENDING', label: 'Nuevos' },
  { status: 'ACCEPTED', label: 'Aceptados' },
  { status: 'PREPARING', label: 'Preparando' },
  { status: 'READY', label: 'Listos' },
]

const POLL_MS = 15_000

const doneMessages: Record<RestaurantAction, string> = {
  accept: 'Pedido aceptado y cobrado',
  reject: 'Pedido rechazado; la reserva se liberó sin cobro',
  'start-preparation': 'Pedido en preparación',
  'mark-ready': 'Pedido listo para recoger',
}

const emptyTexts: Partial<Record<OrderStatus, string>> = {
  PENDING: 'Cuando llegue un pedido nuevo aparecerá aquí. Mantén esta pantalla abierta.',
  ACCEPTED: 'Los pedidos que aceptes esperan aquí hasta que empieces a prepararlos.',
  PREPARING: 'Aquí verás lo que tienes en cocina.',
  READY: 'Los pedidos listos esperan aquí a que llegue el repartidor.',
}

/** Código corto para nombrar el pedido en voz alta con el repartidor o el cliente. */
function shortCode(id: string): string {
  return '#' + id.replace(/-/g, '').slice(-4).toUpperCase()
}

function ago(iso: string, now: number): string {
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60_000))
  if (minutes < 1) return 'Ahora mismo'
  if (minutes < 60) return `Hace ${minutes} min`
  const hours = Math.floor(minutes / 60)
  return hours < 24 ? `Hace ${hours} h` : new Date(iso).toLocaleDateString('es-PE', { day: 'numeric', month: 'short' })
}

type Buckets = Partial<Record<OrderStatus, RestaurantOrder[]>>

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; buckets: Buckets }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Pedidos del restaurante (RF-18, RF-19): acepta o rechaza los nuevos y avanza los aceptados. Se actualiza sola. */
export function OrdersPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [status, setStatus] = useState<OrderStatus>('PENDING')
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<{ text: string; ok: boolean } | null>(null)
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | undefined>()
  const [busy, setBusy] = useState(false)
  const [now, setNow] = useState(() => Date.now())

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
      Promise.all(tabs.map((tab) => listOrders(token, tab.status)))
        .then((lists) => {
          if (!active) return
          setNow(Date.now())
          setState({ kind: 'ready', buckets: Object.fromEntries(tabs.map((tab, i) => [tab.status, lists[i]])) })
        })
        .catch((error: unknown) => {
          if (!active) return
          if (error instanceof ApiError && error.status === 403) setState({ kind: 'forbidden' })
          else {
            const message = fail(error)
            // Un fallo de una actualización periódica no borra lo que ya se muestra.
            setState((current) => (current.kind === 'ready' ? current : { kind: 'error', message }))
          }
        })
    }
    load()
    const timer = setInterval(load, POLL_MS)
    return () => { active = false; clearInterval(timer) }
  }, [token, attempt, fail])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  const reload = () => setAttempt((n) => n + 1)

  async function run(order: RestaurantOrder, action: RestaurantAction, reasonText?: string) {
    if (busy) return
    setBusy(true)
    setNotice(null)
    try {
      await actOnOrder(token, order.id, action, reasonText)
      setNotice({ text: doneMessages[action], ok: true })
      setRejectingId(null)
      setReason('')
      // Tras avanzar, la vista sigue al pedido a su nueva pestaña.
      if (action === 'accept') setStatus('ACCEPTED')
      if (action === 'start-preparation') setStatus('PREPARING')
      if (action === 'mark-ready') setStatus('READY')
      reload()
    } catch (error) {
      // 409: el estado cambió (p. ej. el cliente canceló a la vez): se muestra el motivo y se recarga.
      setNotice({ text: fail(error), ok: false })
      if (error instanceof ApiError && error.status === 409) reload()
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
      <RestaurantLayout title="Pedidos">
        <p className="auth-alert" role="alert">Esta sección es solo para restaurantes aprobados y habilitados.</p>
        <Link to="/mi-restaurante" className="auth-submit auth-submit-secondary">Volver a mi restaurante</Link>
      </RestaurantLayout>
    )
  }

  const items = state.kind === 'ready' ? state.buckets[status] ?? [] : []
  return (
    <RestaurantLayout wide title="Pedidos" description="Acepta los nuevos y avanza su preparación. Se actualiza solo.">
      {state.kind === 'ready' && (
        <div className="rs-tabs" role="tablist" aria-label="Estado de los pedidos">
          {tabs.map((tab) => {
            const count = state.buckets[tab.status]?.length ?? 0
            return (
              <button key={tab.status} type="button" role="tab" aria-selected={status === tab.status}
                className={tab.status === 'PENDING' && count > 0 ? 'rs-tab rs-tab-alert' : 'rs-tab'}
                onClick={() => { setStatus(tab.status); setRejectingId(null); setNotice(null) }}>
                {tab.label}<span className="rs-count">{count}</span>
              </button>
            )
          })}
        </div>
      )}
      {notice && (notice.ok
        ? <p className="app-success" role="status"><AppIcon name="check" />{notice.text}</p>
        : <p className="auth-alert" role="alert">{notice.text}</p>)}

      {state.kind === 'loading' && <p className="auth-description" role="status">Cargando pedidos…</p>}
      {state.kind === 'error' && (
        <>
          <p className="auth-alert" role="alert">{state.message}</p>
          <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); reload() }}>Reintentar</button>
        </>
      )}
      {state.kind === 'ready' && items.length === 0 && (
        <div className="rs-empty" role="status">
          <span className="rs-empty-icon"><AppIcon name="receipt" /></span>
          <strong>No hay pedidos en esta etapa</strong>
          <p>{emptyTexts[status]}</p>
        </div>
      )}
      {state.kind === 'ready' && items.length > 0 && (
        <ul className="rs-orders">
          {items.map((order) => (
            <li key={order.id} className={order.status === 'PENDING' ? 'rs-order rs-order-new' : 'rs-order'}>
              <header className="rs-order-head">
                <div>
                  <strong className="rs-order-code">{shortCode(order.id)}</strong>
                  <small>{ago(order.createdAt, now)}</small>
                </div>
                <span className="rs-chip">{orderStatusLabels[order.status]}</span>
              </header>

              <ul className="rs-lines">
                {order.lines.map((line) => (
                  <li key={line.productId}><b>{line.quantity}×</b><span>{line.name}</span><em>{formatPrice(line.lineTotal)}</em></li>
                ))}
              </ul>

              <div className="rs-order-meta">
                <div className="rs-meta-block">
                  <AppIcon name="pin" />
                  <span>{order.delivery.street}, {order.delivery.district}{order.delivery.reference ? ` · ${order.delivery.reference}` : ''}</span>
                </div>
                <div className="rs-total"><small>Total</small><strong>{formatPrice(order.totals.total)}</strong></div>
              </div>

              {rejectingId !== order.id && restaurantActions(order.status).length > 0 && (
                <div className="rs-actions">
                  {restaurantActions(order.status).map((option) => (
                    <button key={option.action} type="button"
                      className={option.action === 'reject' ? 'rs-reject' : 'auth-submit rs-primary'}
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
                <div className="rs-reject-panel">
                  <label htmlFor={'reason-' + order.id}>Motivo del rechazo <span className="addr-optional">(opcional)</span></label>
                  <textarea id={'reason-' + order.id} rows={3} value={reason} maxLength={ORDER_REASON_MAX + 50}
                    aria-invalid={reasonError ? true : undefined} onChange={(e) => setReason(e.target.value)} />
                  {reasonError && <p className="auth-error">{reasonError}</p>}
                  <div className="rs-actions">
                    <button type="button" className="auth-submit rs-primary" disabled={busy} onClick={() => confirmReject(order)}>Confirmar rechazo</button>
                    <button type="button" className="rs-reject" disabled={busy} onClick={() => setRejectingId(null)}>Cancelar</button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </RestaurantLayout>
  )
}
