import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, errorMessage } from '../auth/auth-api'
import { AppIcon } from '../auth/AppIcon'
import { RestaurantLayout } from './RestaurantLayout'
import { useSession } from '../auth/RequireSession'
import { formatPrice } from '../catalog/catalog-validation'
import { actOnOrder, listAssignedOrders, listOrders, type RestaurantOrder } from './orders-api'
import {
  ORDER_REASON_MAX,
  expiryDeadline,
  formatRemaining,
  isNearExpiry,
  orderCode as shortCode,
  remainingMs,
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
/** Con un pedido ya vencido en pantalla, se vuelve a pedir la lista como mucho cada tantos ms hasta que el servidor lo cancele. */
const EXPIRED_RELOAD_MS = 5_000

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
  READY: 'Los pedidos listos esperan aquí hasta que el repartidor los recoge.',
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
  | { kind: 'ready'; buckets: Buckets; loadedAt: number }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Pedidos del restaurante (RF-18, RF-19): acepta o rechaza los nuevos y avanza los aceptados. Se actualiza sola. */
export function OrdersPage() {
  const session = useSession()
  const [status, setStatus] = useState<OrderStatus>('PENDING')
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<{ text: string; ok: boolean } | null>(null)
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | undefined>()
  const [busy, setBusy] = useState(false)
  const [now, setNow] = useState(() => Date.now())

  const token = session.token

  useEffect(() => {
    let active = true
    const load = () => {
      Promise.all([...tabs.map((tab) => listOrders(token, tab.status)), listAssignedOrders(token)])
        .then((lists) => {
          if (!active) return
          const loadedAt = Date.now()
          setNow(loadedAt)
          const buckets: Buckets = Object.fromEntries(tabs.map((tab, i) => [tab.status, lists[i]]))
          // Con repartidor asignado el pedido sigue en el local hasta que lo recogen: se muestra en «Listos».
          buckets.READY = [...(buckets.READY ?? []), ...lists[tabs.length]]
          setState({ kind: 'ready', buckets, loadedAt })
        })
        .catch((error: unknown) => {
          if (!active) return
          if (error instanceof ApiError && error.status === 403) setState({ kind: 'forbidden' })
          else {
            const message = errorMessage(error)
            // Un fallo de una actualización periódica no borra lo que ya se muestra.
            setState((current) => (current.kind === 'ready' ? current : { kind: 'error', message }))
          }
        })
    }
    load()
    const timer = setInterval(load, POLL_MS)
    return () => { active = false; clearInterval(timer) }
  }, [token, attempt])

  // Reloj de 1 s para la cuenta regresiva de los pedidos nuevos (spec 012).
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  const loadedAt = state.kind === 'ready' ? state.loadedAt : 0
  const deadlineOf = (order: RestaurantOrder) =>
    order.status === 'PENDING' ? expiryDeadline(order.secondsToExpire, loadedAt) : null
  const pending = state.kind === 'ready' ? state.buckets.PENDING ?? [] : []
  const someExpired = pending.some((order) => {
    const deadline = deadlineOf(order)
    return deadline !== null && deadline <= now
  })
  const shouldRefreshExpired = someExpired && now - loadedAt >= EXPIRED_RELOAD_MS

  // Un pedido que llegó a 0 se vuelve a pedir para mostrarlo Cancelado en cuanto el servidor lo registra.
  useEffect(() => {
    if (!shouldRefreshExpired) return
    const timer = setTimeout(() => setAttempt((n) => n + 1), 0)
    return () => clearTimeout(timer)
  }, [shouldRefreshExpired])


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
      setNotice({ text: errorMessage(error), ok: false })
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
          {items.map((order) => {
            const deadline = deadlineOf(order)
            const remaining = deadline === null ? null : remainingMs(deadline, now)
            const expired = remaining === 0
            const near = remaining !== null && isNearExpiry(remaining, order.createdAt, order.expiresAt)
            return (
            <li key={order.id} className={order.status === 'PENDING' ? 'rs-order rs-order-new' : 'rs-order'}>
              <header className="rs-order-head">
                <div>
                  <strong className="rs-order-code">{shortCode(order.id)}</strong>
                  <small>{ago(order.createdAt, now)}</small>
                </div>
                <span className="rs-order-tags">
                  {remaining !== null && (
                    <span className={near ? 'rs-expiry rs-expiry-near' : 'rs-expiry'} role="timer"
                      aria-label={expired ? 'Plazo vencido' : `Quedan ${formatRemaining(remaining)} para responder`}>
                      <AppIcon name="clock" />
                      {expired ? 'Vencido' : `Responde en ${formatRemaining(remaining)}`}
                    </span>
                  )}
                  <span className="rs-chip">{orderStatusLabels[order.status]}</span>
                </span>
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
                  {order.options.scheduledFor && <span><strong>Programado:</strong> {new Date(order.options.scheduledFor).toLocaleString('es-PE', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</span>}
                  {order.options.storeNote && <span><strong>Nota del cliente:</strong> {order.options.storeNote}</span>}
                  {order.options.receiver && <span><strong>Recibe:</strong> {order.options.receiver.name} ({order.options.receiver.phone})</span>}
                  {order.payment.method === 'CASH'
                    ? <span><strong>Pago:</strong> en efectivo; el repartidor cobra {formatPrice(order.totals.total)} al cliente</span>
                    : <span><strong>Pago:</strong> con tarjeta</span>}
                  {order.status === 'ASSIGNED' && <span><strong>Repartidor:</strong> asignado, en camino a recogerlo</span>}
                </div>
                <div className="rs-total"><small>Tus productos</small><strong>{formatPrice(order.totals.subtotal)}</strong></div>
              </div>

              {expired && (
                <p className="rs-expired-note">No se respondió a tiempo: el pedido se cancelará y la reserva se liberará sin cobro.</p>
              )}
              {!expired && rejectingId !== order.id && restaurantActions(order.status).length > 0 && (
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
              {!expired && rejectingId === order.id && (
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
            )
          })}
        </ul>
      )}
    </RestaurantLayout>
  )
}
