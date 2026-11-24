import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { formatPrice } from '../catalog/catalog-validation'
import {
  canCustomerCancel,
  expiryDeadline,
  formatRemaining,
  orderCode,
  orderStatusLabels,
  remainingMs,
} from '../restaurant/orders-logic'
import { useConfirm } from '../ui/useConfirm'
import { cancelOrder, getOrder, paymentStatusLabel, type Order } from './checkout-api'
import { getTracking, type Tracking } from './tracking-api'
import {
  TRACKING_POLL_MS,
  formatAge,
  formatStageTime,
  isFinalStatus,
  locationReasonMessages,
} from './tracking-logic'
import '../restaurant/restaurant.css'
import '../cart/cart.css'
import './checkout.css'

const RouteMap = lazy(() => import('../delivery/RouteMap'))

/** Estado listo con el momento de recepción: la cuenta regresiva parte de los segundos que da el servidor (spec 012). */
function ready(order: Order, notice?: string): ViewState {
  return { kind: 'ready', order, notice, receivedAt: Date.now() }
}

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; order: Order; notice?: string; receivedAt: number }
  | { kind: 'notFound' }
  | { kind: 'error'; message: string }

/** Pedido propio, su estado y el de su pago (RF-17); el cliente puede cancelarlo mientras esté Pendiente (RF-20). */
export function OrderPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [busy, setBusy] = useState(false)
  const [tracking, setTracking] = useState<Tracking | null>(null)
  const [offline, setOffline] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const [confirmDialog, confirm] = useConfirm()
  const token = session?.token ?? ''

  const handleFailure = useCallback((error: unknown): string => {
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
    getOrder(token, id)
      .then((order) => { if (active) setState(ready(order)) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 404) setState({ kind: 'notFound' })
        else setState({ kind: 'error', message: handleFailure(error) })
      })
    return () => { active = false }
  }, [token, id, handleFailure])

  // Seguimiento (RF-27, RF-28): se renueva solo cada pocos segundos, sin recargar, hasta que el pedido termina.
  useEffect(() => {
    if (!token) return
    let active = true
    let timer: ReturnType<typeof setTimeout> | undefined
    const load = async () => {
      if (document.visibilityState === 'visible') {
        try {
          const result = await getTracking(token, id)
          if (!active) return
          setTracking(result)
          setOffline(false)
          if (isFinalStatus(result.status)) return
        } catch (error) {
          if (!active) return
          if (error instanceof ApiError && (error.status === 401 || error.status === 404)) return
          setOffline(true)
        }
      }
      if (active) timer = setTimeout(() => void load(), TRACKING_POLL_MS)
    }
    void load()
    return () => { active = false; if (timer) clearTimeout(timer) }
  }, [token, id])

  // Cuenta regresiva del pedido Pendiente (spec 012): fecha límite según los segundos que dio el servidor.
  const loadedOrder = state.kind === 'ready' ? state.order : null
  const currentStatus = tracking?.status ?? loadedOrder?.status
  const deadline = state.kind === 'ready' && currentStatus === 'PENDING'
    ? expiryDeadline(state.order.secondsToExpire, state.receivedAt) : null

  // La antigüedad de la ubicación y la cuenta regresiva se recalculan cada segundo mientras se muestran.
  const showsLocation = tracking?.courierLocation != null
  const ticking = showsLocation || deadline !== null
  useEffect(() => {
    if (!ticking) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [ticking])

  // Si el seguimiento informa otro estado (por ejemplo, Cancelado por vencimiento), se refresca el pedido y su pago.
  const staleStatus = tracking && loadedOrder && tracking.status !== loadedOrder.status ? tracking.status : null
  useEffect(() => {
    if (!staleStatus || !token) return
    let active = true
    getOrder(token, id).then((order) => { if (active) setState(ready(order)) }).catch(() => undefined)
    return () => { active = false }
  }, [staleStatus, token, id])

  if (!session) return <Navigate to="/iniciar-sesion" replace />
  if (state.kind === 'loading') return <AuthLayout title="Mi pedido" wide><p className="auth-notice" role="status">Cargando pedido...</p></AuthLayout>
  if (state.kind === 'notFound') return <AuthLayout title="Mi pedido" wide><p className="auth-notice" role="status">Pedido no encontrado.</p><Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link></AuthLayout>
  if (state.kind === 'error') return <AuthLayout title="Mi pedido" wide><p className="auth-alert" role="alert">{state.message}</p></AuthLayout>

  const { order, notice } = state
  const status = tracking?.status ?? order.status

  async function cancel() {
    if (busy) return
    const ok = await confirm({
      title: '¿Cancelar el pedido?',
      message: 'El restaurante aún no lo acepta, así que no se te cobrará nada. Esta acción no se puede deshacer.',
      confirmLabel: 'Sí, cancelar pedido',
      cancelLabel: 'Mantener pedido',
      danger: true,
    })
    if (!ok) return
    setBusy(true)
    try {
      const updated = await cancelOrder(token, id)
      setState(ready(updated, 'Pedido cancelado. La reserva se liberó y no se cobró nada.'))
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        // El restaurante decidió a la vez: se muestra el estado vigente y el motivo.
        try {
          setState(ready(await getOrder(token, id), error.message))
        } catch (reloadError) {
          setState({ kind: 'error', message: handleFailure(reloadError) })
        }
      } else {
        setState({ kind: 'ready', order, notice: handleFailure(error), receivedAt: state.kind === 'ready' ? state.receivedAt : Date.now() })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title={`Pedido ${orderCode(order.id)}`} description={`${order.restaurant.name} · ${orderStatusLabels[status]}`} wide>
      {notice && <p className="auth-notice" role="status">{notice}</p>}
      {deadline !== null && (
        <p className="catalog-banner order-expiry" role="timer">
          {remainingMs(deadline, now) > 0
            ? <>Esperando al restaurante. Si no responde, el pedido se cancelará en <strong>{formatRemaining(remainingMs(deadline, now))}</strong> y no se te cobrará nada.</>
            : 'El restaurante no respondió a tiempo. Estamos cancelando el pedido y liberando tu reserva…'}
        </p>
      )}
      {status === 'DELIVERED' && (
        <p className="catalog-banner" role="status">
          Tu pedido fue entregado. <Link to="/historial">Califícalo en tu historial</Link>.
        </p>
      )}
      <div className="cart-layout">
      <div className="cart-main">
      {tracking && (
        <section className="cart-section" aria-labelledby="order-tracking-title">
          <h2 id="order-tracking-title">Seguimiento</h2>
          {offline && <p className="auth-notice" role="status">Sin conexión. Reintentando…</p>}
          <ol className="tracking-stages">
            {tracking.stages.map((stage) => (
              <li key={stage.status}><span>{orderStatusLabels[stage.status]}</span><span>{formatStageTime(stage.at)}</span></li>
            ))}
          </ol>
          {tracking.courierLocation ? (
            <div className="tracking-location tracking-map" role="status">
              <Suspense fallback={<p className="auth-hint">Cargando el mapa…</p>}>
                <RouteMap
                  ariaLabel="Mapa con la ubicación de tu repartidor"
                  points={[
                    { kind: 'courier', latitude: tracking.courierLocation.latitude, longitude: tracking.courierLocation.longitude, label: 'Tu repartidor' },
                    ...(tracking.destination ? [{ kind: 'dropoff' as const, latitude: tracking.destination.latitude, longitude: tracking.destination.longitude, label: 'Tu entrega' }] : []),
                  ]}
                />
              </Suspense>
              <p className="auth-hint">Ubicación actualizada {formatAge(tracking.courierLocation.updatedAt, now)}</p>
            </div>
          ) : tracking.locationUnavailableReason && (
            <p className="auth-hint" role="status">{locationReasonMessages[tracking.locationUnavailableReason]}</p>
          )}
        </section>
      )}
      <section className="cart-section" aria-labelledby="order-lines-title">
        <h2 id="order-lines-title">Productos</h2>
        <ul className="checkout-lines">
          {order.lines.map((line) => (
            <li key={line.productId}><span>{line.quantity} x {line.name} ({formatPrice(line.unitPrice)} c/u)</span><span>{formatPrice(line.lineTotal)}</span></li>
          ))}
        </ul>
        <p className="auth-hint">Entrega en: {order.delivery.street}, {order.delivery.district}{order.delivery.reference ? ` (${order.delivery.reference})` : ''}</p>
        <p className="auth-hint">Horario: {order.options.scheduledFor ? new Date(order.options.scheduledFor).toLocaleString('es-PE', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : 'Entrega inmediata'}</p>
        {order.options.receiver && <p className="auth-hint">Recibe: {order.options.receiver.name} ({order.options.receiver.phone}){order.options.receiver.note ? ` · ${order.options.receiver.note}` : ''}</p>}
        {order.options.storeNote && <p className="auth-hint">Nota para la tienda: {order.options.storeNote}</p>}
      </section>
      </div>
      <aside className="cart-side">
      <section className="cart-section" aria-labelledby="order-totals-title">
        <h2 id="order-totals-title">Importes</h2>
        <dl className="cart-totals">
          <div><dt>Subtotal</dt><dd>{formatPrice(order.totals.subtotal)}</dd></div>
          <div><dt>Envío</dt><dd>{formatPrice(order.totals.deliveryFee)}</dd></div>
          <div><dt>Tarifa de servicio</dt><dd>{formatPrice(order.totals.commission)}</dd></div>
          <div className="cart-total"><dt>Total</dt><dd>{formatPrice(order.totals.total)}</dd></div>
        </dl>
        <p className="auth-hint">Tu pago: {paymentStatusLabel(order.payment.method, order.payment.status)}.</p>
      </section>
      {canCustomerCancel(status) && (
        <button type="button" className="auth-submit auth-submit-secondary cart-cancel" disabled={busy} onClick={() => void cancel()}>
          Cancelar pedido
        </button>
      )}
      <Link to="/restaurantes" className="cart-more">Seguir buscando restaurantes</Link>
      </aside>
      </div>
      {confirmDialog}
    </AuthLayout>
  )
}
