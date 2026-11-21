import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { loadStripe } from '@stripe/stripe-js'
import { CardCvcElement, CardExpiryElement, CardNumberElement, Elements, useElements, useStripe } from '@stripe/react-stripe-js'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { formatPrice } from '../catalog/catalog-validation'
import { confirmOrder, getCheckout, type Checkout, type PaymentChoice } from './checkout-api'
import { AddressDialog } from '../auth/AddressDialog'
import { describeSlot, validateReceiver } from './checkout-options'
import { DeliveryTimeDialog } from './DeliveryTimeDialog'
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
        <p className="auth-hint">Con tarjeta se reserva el monto y solo se cobra cuando el restaurante acepte el pedido. En efectivo pagas al recibir.</p>
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

  const [method, setMethod] = useState<PaymentChoice>('CARD')
  const [storeNote, setStoreNote] = useState('')
  const [showNote, setShowNote] = useState(false)
  const [scheduledFor, setScheduledFor] = useState('')
  const [forOther, setForOther] = useState(false)
  const [receiver, setReceiver] = useState({ name: '', phone: '', note: '' })
  const [receiverError, setReceiverError] = useState<string | null>(null)
  const [changingAddress, setChangingAddress] = useState(false)
  const [timeOpen, setTimeOpen] = useState(false)

  const total = checkout.cart.totals.total
  const cart = checkout.cart

  /** Al cerrar el panel de direcciones se vuelve a leer el resumen: la dirección elegida cambia el envío. */
  async function addressDialogClosed() {
    setChangingAddress(false)
    try {
      onRefresh(await getCheckout(token))
      setConfirmationKey(newConfirmationKey())
    } catch (failure) {
      setError(onFailure(failure))
    }
  }

  async function submit(event: React.SyntheticEvent) {
    event.preventDefault()
    if (busy || total === null) return
    if (forOther) {
      const problem = validateReceiver(receiver)
      setReceiverError(problem)
      if (problem) return
    }
    let paymentMethodId: string | null = null
    setBusy(true)
    setError(null)
    try {
      if (method === 'CARD') {
        const card = elements?.getElement(CardNumberElement)
        if (!stripe || !elements || !card) return
        const created = await stripe.createPaymentMethod({ type: 'card', card })
        if (created.error || !created.paymentMethod) {
          setError(created.error?.message ?? 'No se pudo leer la tarjeta. Revisa los datos.')
          setConfirmationKey(newConfirmationKey())
          return
        }
        paymentMethodId = created.paymentMethod.id
      }
      const order = await confirmOrder(token, confirmationKey, method, paymentMethodId, total, {
        storeNote: storeNote.trim() || null,
        scheduledFor: scheduledFor || null,
        receiver: forOther ? { name: receiver.name.trim(), phone: receiver.phone.trim(), note: receiver.note.trim() || null } : null,
      })
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
    <form className="checkout-form" onSubmit={submit} noValidate>
      <section className="cart-section checkout-block">
        <h2>Datos de entrega</h2>

        <div className="checkout-row">
          <div>
            <strong>Dirección de entrega</strong>
            <p>{cart.address ? `${cart.address.street}, ${cart.address.district}` : 'Sin dirección'}</p>
          </div>
          <button type="button" className="checkout-chip" disabled={busy} onClick={() => setChangingAddress(true)}>Cambiar</button>
        </div>
        {changingAddress && (
          <AddressDialog token={token} selectedId={cart.address?.id ?? null} onClose={() => void addressDialogClosed()} />
        )}

        <div className="checkout-row">
          <div>
            <strong>Horario de entrega</strong>
            <p>{scheduledFor ? describeSlot(scheduledFor) : 'Entrega inmediata'}</p>
          </div>
          <button type="button" className="checkout-chip" onClick={() => setTimeOpen(true)}>Cambiar</button>
        </div>
        {timeOpen && <DeliveryTimeDialog value={scheduledFor} onConfirm={setScheduledFor} onClose={() => setTimeOpen(false)} />}

        <div className="checkout-row">
          <div>
            <strong>Nota para la tienda</strong>
            {storeNote && !showNote && <p>{storeNote}</p>}
          </div>
          <button type="button" className="checkout-chip" onClick={() => setShowNote((v) => !v)}>{showNote ? 'Listo' : storeNote ? 'Editar' : 'Agregar'}</button>
        </div>
        {showNote && (
          <div className="auth-field">
            <label htmlFor="checkout-note" className="visually-hidden">Nota para la tienda</label>
            <textarea id="checkout-note" rows={3} maxLength={300} value={storeNote} placeholder="Ej. Sin cebolla, por favor" onChange={(e) => setStoreNote(e.target.value)} />
          </div>
        )}

        <div className="checkout-row">
          <div>
            <strong>¿Quién recibirá el pedido?</strong>
            <p>{forOther ? (receiver.name.trim() || 'Otra persona') : 'Yo mismo'}</p>
          </div>
          <button type="button" className="checkout-chip" onClick={() => { setForOther((v) => !v); setReceiverError(null) }}>{forOther ? 'Yo mismo' : 'Cambiar'}</button>
        </div>
        {forOther && (
          <div className="checkout-receiver">
            <div className="auth-field"><label htmlFor="rc-name">Nombre de quien recibe</label>
              <input id="rc-name" maxLength={80} value={receiver.name} onChange={(e) => setReceiver({ ...receiver, name: e.target.value })} /></div>
            <div className="auth-field"><label htmlFor="rc-phone">Teléfono</label>
              <input id="rc-phone" type="tel" maxLength={20} value={receiver.phone} onChange={(e) => setReceiver({ ...receiver, phone: e.target.value })} /></div>
            <div className="auth-field"><label htmlFor="rc-note">Nota para la entrega (opcional)</label>
              <textarea id="rc-note" rows={2} maxLength={300} value={receiver.note} placeholder="Ej. Tocar el timbre del 2.º piso" onChange={(e) => setReceiver({ ...receiver, note: e.target.value })} /></div>
            {receiverError && <p className="auth-alert" role="alert">{receiverError}</p>}
          </div>
        )}
      </section>

      <section className="cart-section checkout-block">
        <h2>Método de pago</h2>
        <div className="checkout-methods" role="radiogroup" aria-label="Método de pago">
          {checkout.paymentMethods.map((m) => (
            <label key={m.code} className={'checkout-method' + (method === m.code ? ' is-selected' : '')}>
              <input type="radio" name="payment-method" value={m.code} checked={method === m.code} onChange={() => setMethod(m.code as PaymentChoice)} />
              <span>{m.label}</span>
            </label>
          ))}
        </div>
        {method === 'CARD' ? (
          <>
            <p className="auth-hint">Los datos de tu tarjeta los procesa Stripe (modo de pruebas); Mikuna nunca los guarda.</p>
            <div className="checkout-card-fields">
              <label className="checkout-card-field checkout-card-wide">
                <span>Número de tarjeta</span>
                <div className="checkout-card"><CardNumberElement options={{ showIcon: true, placeholder: '1234 1234 1234 1234' }} /></div>
              </label>
              <label className="checkout-card-field">
                <span>Fecha de vencimiento</span>
                <div className="checkout-card"><CardExpiryElement options={{ placeholder: 'MM / AA' }} /></div>
              </label>
              <label className="checkout-card-field">
                <span>Código de seguridad</span>
                <div className="checkout-card"><CardCvcElement options={{ placeholder: 'CVC' }} /></div>
              </label>
            </div>
          </>
        ) : (
          <p className="auth-hint">Pagarás en efectivo al repartidor cuando recibas el pedido.</p>
        )}
        {error && <p className="auth-alert" role="alert">{error}</p>}
        <button type="submit" className="auth-submit" disabled={busy || total === null || (method === 'CARD' && !stripe)}>{busy ? 'Procesando...' : 'Ordenar ahora'}</button>
        <Link to="/carrito" className="auth-switch">Volver al carrito</Link>
      </section>
    </form>
  )
}
