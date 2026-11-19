import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { loadStripe } from '@stripe/stripe-js'
import { CardElement, Elements, useElements, useStripe } from '@stripe/react-stripe-js'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { formatPrice } from '../catalog/catalog-validation'
import { confirmOrder, getCheckout, type Checkout } from './checkout-api'
import { blockerLabels, confirmationErrorMessage, newConfirmationKey } from './checkout-validation'
import '../restaurant/restaurant.css'
import '../cart/cart.css'
import './checkout.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; checkout: Checkout }
  | { kind: 'forbidden'; message: string }
  | { kind: 'error'; message: string }

/** Resumen de confirmación (RF-14), método de pago (RF-15) y reserva del monto con Stripe en pruebas (RF-16). */
export function CheckoutPage() {
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
    getCheckout(token)
      .then((checkout) => { if (active) setState({ kind: 'ready', checkout }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setState({ kind: 'forbidden', message: 'La confirmación de pedidos es solo para cuentas de cliente.' })
        else setState({ kind: 'error', message: handleFailure(error) })
      })
    return () => { active = false }
  }, [token, handleFailure])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  if (state.kind === 'loading') return <AuthLayout title="Confirmar pedido" wide><p className="auth-notice" role="status">Cargando resumen...</p></AuthLayout>
  if (state.kind === 'forbidden') return <AuthLayout title="Confirmar pedido" wide><p className="auth-notice" role="status">{state.message}</p><Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link></AuthLayout>
  if (state.kind === 'error') return <AuthLayout title="Confirmar pedido" wide><p className="auth-alert" role="alert">{state.message}</p></AuthLayout>

  return <CheckoutView checkout={state.checkout} token={token} onRefresh={(checkout) => setState({ kind: 'ready', checkout })} onFailure={handleFailure} />
}

interface ViewProps {
  checkout: Checkout
  token: string
  onRefresh: (checkout: Checkout) => void
  onFailure: (error: unknown) => string
}

function CheckoutView({ checkout, token, onRefresh, onFailure }: ViewProps) {
  // La clave publicable viene del backend (vive solo en backend/.env); Stripe.js se carga una vez por clave.
  const stripePromise = useMemo(() => loadStripe(checkout.publishableKey), [checkout.publishableKey])
  const { cart } = checkout
  const { totals } = cart

  return (
    <AuthLayout title="Confirmar pedido" description={cart.restaurant ? `Pedido en ${cart.restaurant.name}` : undefined} wide>
      <div className="cart-layout">
      <div className="cart-main">
      <section className="cart-section" aria-labelledby="checkout-lines-title">
        <h2 id="checkout-lines-title">Tu pedido</h2>
        <ul className="checkout-lines">
          {cart.lines.map((line) => (
            <li key={line.id}><span>{line.quantity} x {line.name}</span><span>{line.lineTotal === null ? '-' : formatPrice(line.lineTotal)}</span></li>
          ))}
        </ul>
        {cart.address && <p className="auth-hint">Entrega en: {cart.address.street}, {cart.address.district}</p>}
      </section>

      {!checkout.ready ? (
        <section className="cart-section">
          {checkout.blockers.map((blocker) => <p className="auth-alert" role="alert" key={blocker}>{blockerLabels[blocker]}</p>)}
          <Link to="/carrito" className="auth-switch">Volver al carrito</Link>
        </section>
      ) : (
        <Elements stripe={stripePromise} options={{ locale: 'es' }}>
          <PaymentForm checkout={checkout} token={token} onRefresh={onRefresh} onFailure={onFailure} />
        </Elements>
      )}
      </div>

      <aside className="cart-side">
      <section className="cart-section" aria-labelledby="checkout-totals-title">
        <h2 id="checkout-totals-title">Importes</h2>
        <dl className="cart-totals">
          <div><dt>Subtotal</dt><dd>{formatPrice(totals.subtotal)}</dd></div>
          <div><dt>Envío</dt><dd>{totals.deliveryFee === null ? '-' : formatPrice(totals.deliveryFee)}</dd></div>
          <div><dt>Comisión</dt><dd>{totals.commission === null ? '-' : formatPrice(totals.commission)}</dd></div>
          <div className="cart-total"><dt>Total</dt><dd>{totals.total === null ? '-' : formatPrice(totals.total)}</dd></div>
        </dl>
        <p className="auth-hint">Al confirmar se reserva el monto en tu tarjeta; solo se cobra cuando el restaurante acepte el pedido.</p>
      </section>
      </aside>
      </div>
    </AuthLayout>
  )
}

function PaymentForm({ checkout, token, onRefresh, onFailure }: ViewProps) {
  const navigate = useNavigate()
  const stripe = useStripe()
  const elements = useElements()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Misma clave en los reintentos del mismo intento; nueva solo tras cambiar los importes o la tarjeta.
  const [confirmationKey, setConfirmationKey] = useState(() => newConfirmationKey())

  const total = checkout.cart.totals.total

  async function submit(event: React.SyntheticEvent) {
    event.preventDefault()
    if (busy || !stripe || !elements || total === null) return
    const card = elements.getElement(CardElement)
    if (!card) return
    setBusy(true)
    setError(null)
    try {
      const created = await stripe.createPaymentMethod({ type: 'card', card })
      if (created.error || !created.paymentMethod) {
        setError(created.error?.message ?? 'No se pudo leer la tarjeta. Revisa los datos.')
        setConfirmationKey(newConfirmationKey())
        return
      }
      const order = await confirmOrder(token, confirmationKey, created.paymentMethod.id, total)
      navigate('/pedido/' + order.id, { replace: true })
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409 && failure.code === 'AMOUNTS_CHANGED') {
        // Se muestra el resumen vigente y se pide confirmar de nuevo.
        const refreshed = await getCheckout(token).catch(() => null)
        if (refreshed) onRefresh(refreshed)
        setConfirmationKey(newConfirmationKey())
      }
      if (failure instanceof ApiError && failure.status === 402) setConfirmationKey(newConfirmationKey())
      const message = failure instanceof ApiError ? confirmationErrorMessage(failure.status, failure.code, failure.message) : onFailure(failure)
      setError(message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="cart-section checkout-form" onSubmit={submit} noValidate>
      <h2>Método de pago</h2>
      <p className="auth-hint">{checkout.paymentMethods.map((m) => m.label).join(', ')}. Los datos de tu tarjeta los procesa Stripe (modo de pruebas); Mikuna nunca los guarda.</p>
      <div className="checkout-card"><CardElement options={{ hidePostalCode: true }} /></div>
      {error && <p className="auth-alert" role="alert">{error}</p>}
      <button type="submit" className="auth-submit" disabled={busy || !stripe || total === null}>{busy ? 'Procesando...' : 'Confirmar pedido'}</button>
      <Link to="/carrito" className="auth-switch">Volver al carrito</Link>
    </form>
  )
}
