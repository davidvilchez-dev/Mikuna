import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { formatPrice } from '../catalog/catalog-validation'
import { canCustomerCancel, orderStatusLabels } from '../restaurant/orders-logic'
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

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; order: Order; notice?: string }
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
      .then((order) => { if (active) setState({ kind: 'ready', order }) })
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

  // La antigüedad de la ubicación se recalcula cada segundo mientras se muestra.
  const showsLocation = tracking?.courierLocation != null
  useEffect(() => {
    if (!showsLocation) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [showsLocation])

  if (!session) return <Navigate to="/iniciar-sesion" replace />
  if (state.kind === 'loading') return <AuthLayout title="Mi pedido" wide><p className="auth-notice" role="status">Cargando pedido...</p></AuthLayout>
  if (state.kind === 'notFound') return <AuthLayout title="Mi pedido" wide><p className="auth-notice" role="status">Pedido no encontrado.</p><Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link></AuthLayout>
  if (state.kind === 'error') return <AuthLayout title="Mi pedido" wide><p className="auth-alert" role="alert">{state.message}</p></AuthLayout>

  const { order, notice } = state
  const status = tracking?.status ?? order.status

  async function cancel() {
    if (busy) return
    setBusy(true)
    try {
      const updated = await cancelOrder(token, id)
      setState({ kind: 'ready', order: updated, notice: 'Pedido cancelado. La reserva se liberó y no se cobró nada.' })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        // El restaurante decidió a la vez: se muestra el estado vigente y el motivo.
        try {
          setState({ kind: 'ready', order: await getOrder(token, id), notice: error.message })
        } catch (reloadError) {
          setState({ kind: 'error', message: handleFailure(reloadError) })
        }
      } else {
        setState({ kind: 'ready', order, notice: handleFailure(error) })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title="Mi pedido" description={`${order.restaurant.name} - ${orderStatusLabels[status]}`} wide>
      {notice && <p className="auth-notice" role="status">{notice}</p>}
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
          <div><dt>Comisión</dt><dd>{formatPrice(order.totals.commission)}</dd></div>
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
    </AuthLayout>
  )
}
