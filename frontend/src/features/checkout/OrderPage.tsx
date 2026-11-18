import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { formatPrice } from '../catalog/catalog-validation'
import { getOrder, paymentStatusLabels, type Order } from './checkout-api'
import '../restaurant/restaurant.css'
import '../cart/cart.css'
import './checkout.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; order: Order }
  | { kind: 'notFound' }
  | { kind: 'error'; message: string }

/** Pedido propio y estado de su pago (RF-17). No incluye aceptación, cancelación ni historial (specs 007 y 010). */
export function OrderPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
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

  if (!session) return <Navigate to="/iniciar-sesion" replace />
  if (state.kind === 'loading') return <AuthLayout title="Mi pedido" wide><p className="auth-notice" role="status">Cargando pedido...</p></AuthLayout>
  if (state.kind === 'notFound') return <AuthLayout title="Mi pedido" wide><p className="auth-notice" role="status">Pedido no encontrado.</p><Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link></AuthLayout>
  if (state.kind === 'error') return <AuthLayout title="Mi pedido" wide><p className="auth-alert" role="alert">{state.message}</p></AuthLayout>

  const { order } = state
  return (
    <AuthLayout title="Pedido registrado" description={`${order.restaurant.name} - Pendiente de aceptación`} wide>
      <p className="catalog-banner" role="status">El restaurante revisará tu pedido. Tu pago: {paymentStatusLabels[order.payment.status]}.</p>
      <section className="cart-section" aria-labelledby="order-lines-title">
        <h2 id="order-lines-title">Productos</h2>
        <ul className="checkout-lines">
          {order.lines.map((line) => (
            <li key={line.productId}><span>{line.quantity} x {line.name} ({formatPrice(line.unitPrice)} c/u)</span><span>{formatPrice(line.lineTotal)}</span></li>
          ))}
        </ul>
        <p className="auth-hint">Entrega en: {order.delivery.street}, {order.delivery.district}{order.delivery.reference ? ` (${order.delivery.reference})` : ''}</p>
      </section>
      <section className="cart-section" aria-labelledby="order-totals-title">
        <h2 id="order-totals-title">Importes</h2>
        <dl className="cart-totals">
          <div><dt>Subtotal</dt><dd>{formatPrice(order.totals.subtotal)}</dd></div>
          <div><dt>Envío</dt><dd>{formatPrice(order.totals.deliveryFee)}</dd></div>
          <div><dt>Comisión</dt><dd>{formatPrice(order.totals.commission)}</dd></div>
          <div className="cart-total"><dt>Total</dt><dd>{formatPrice(order.totals.total)}</dd></div>
        </dl>
      </section>
      <Link to="/restaurantes" className="auth-switch">Seguir buscando restaurantes</Link>
    </AuthLayout>
  )
}
