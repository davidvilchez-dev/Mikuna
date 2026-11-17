import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { formatPrice } from '../catalog/catalog-validation'
import {
  changeQuantity,
  clearCart,
  getCart,
  lineReasonLabels,
  listAddresses,
  removeLine,
  selectAddress,
  type Address,
  type Cart,
} from './cart-api'
import { MAX_QUANTITY, validateQuantity } from './cart-validation'
import '../restaurant/restaurant.css'
import './cart.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; cart: Cart; addresses: Address[] }
  | { kind: 'forbidden'; message: string }
  | { kind: 'error'; message: string }

/** Carrito del cliente (RF-11), selección de dirección (RF-12) e importes calculados por el servidor (RF-13). */
export function CartPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

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
    Promise.all([getCart(token), listAddresses(token)])
      .then(([cart, addresses]) => { if (active) setState({ kind: 'ready', cart, addresses }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setState({ kind: 'forbidden', message: 'El carrito es solo para cuentas de cliente.' })
        else setState({ kind: 'error', message: handleFailure(error) })
      })
    return () => { active = false }
  }, [token, attempt, handleFailure])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  /** Ejecuta una acción que devuelve el carrito ya recalculado por el servidor y lo muestra. */
  async function run(action: () => Promise<Cart>) {
    if (busy || state.kind !== 'ready') return
    setBusy(true)
    setNotice(null)
    try {
      const cart = await action()
      setState({ kind: 'ready', cart, addresses: state.addresses })
    } catch (error) {
      setNotice(handleFailure(error))
    } finally {
      setBusy(false)
    }
  }

  function updateQuantity(lineId: string, value: string) {
    const quantity = Number(value)
    const problem = validateQuantity(quantity)
    if (problem) {
      setNotice(problem)
      return
    }
    void run(() => changeQuantity(token, lineId, quantity))
  }

  if (state.kind === 'loading') {
    return <AuthLayout title="Mi carrito" wide><p className="auth-description" role="status">Cargando tu carrito…</p></AuthLayout>
  }
  if (state.kind === 'forbidden') {
    return <AuthLayout title="Mi carrito" wide><p className="auth-notice" role="status">{state.message}</p><Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link></AuthLayout>
  }
  if (state.kind === 'error') {
    return (
      <AuthLayout title="Mi carrito" wide>
        <p className="auth-alert" role="alert">{state.message}</p>
        <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
      </AuthLayout>
    )
  }

  const { cart, addresses } = state
  const { totals } = cart

  return (
    <AuthLayout title="Mi carrito" description={cart.restaurant ? `Pedido en ${cart.restaurant.name}` : undefined} wide>
      {notice && <p className="auth-alert" role="alert">{notice}</p>}
      {cart.notices.map((message) => <p className="catalog-banner" role="status" key={message}>{message}</p>)}

      {cart.lines.length === 0 ? (
        <p className="auth-notice" role="status">Tu carrito está vacío. Explora los restaurantes y agrega productos.</p>
      ) : (
        <ul className="cart-list">
          {cart.lines.map((line) => (
            <li className={'cart-line' + (line.available ? '' : ' cart-line-unavailable')} key={line.id}>
              <img className="cart-thumb" src={line.imageUrl} alt={line.name} loading="lazy" />
              <div className="cart-line-body">
                <h2>{line.name}</h2>
                <p>{formatPrice(line.unitPrice)} c/u</p>
                {line.reason && <p className="auth-error" role="status">{lineReasonLabels[line.reason]}: no se incluye en el total.</p>}
                <label className="cart-quantity">
                  Cantidad
                  <input type="number" min={1} max={MAX_QUANTITY} step={1} defaultValue={line.quantity} key={line.id + ':' + line.quantity}
                    disabled={busy} onBlur={(e) => { if (Number(e.target.value) !== line.quantity) updateQuantity(line.id, e.target.value) }} />
                </label>
              </div>
              <div className="cart-line-side">
                <p className="catalog-price">{line.lineTotal === null ? '—' : formatPrice(line.lineTotal)}</p>
                <button type="button" className="auth-submit auth-submit-secondary" disabled={busy} onClick={() => run(() => removeLine(token, line.id))}>Quitar</button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {cart.lines.length > 0 && (
        <button type="button" className="auth-submit auth-submit-secondary cart-clear" disabled={busy}
          onClick={() => { if (window.confirm('¿Vaciar el carrito?')) void run(() => clearCart(token)) }}>Vaciar carrito</button>
      )}

      <section className="cart-section" aria-labelledby="cart-address-title">
        <h2 id="cart-address-title">Dirección de entrega</h2>
        {addresses.length === 0 ? (
          <p className="auth-notice" role="status">Aún no tienes direcciones. <Link to="/mis-direcciones">Agrega una</Link> para calcular el envío.</p>
        ) : (
          <>
            <div className="auth-field">
              <label htmlFor="cart-address">Elige dónde recibir tu pedido</label>
              <select id="cart-address" value={cart.address?.id ?? ''} disabled={busy}
                onChange={(e) => { if (e.target.value) void run(() => selectAddress(token, e.target.value)) }}>
                <option value="">Selecciona una dirección</option>
                {addresses.map((address) => <option key={address.id} value={address.id}>{address.label ? address.label + ' · ' : ''}{address.street} ({address.district})</option>)}
              </select>
            </div>
            <Link to="/mis-direcciones" className="auth-switch">Administrar mis direcciones</Link>
          </>
        )}
      </section>

      <section className="cart-section" aria-labelledby="cart-totals-title">
        <h2 id="cart-totals-title">Resumen</h2>
        <dl className="cart-totals">
          <div><dt>Subtotal</dt><dd>{formatPrice(totals.subtotal)}</dd></div>
          {totals.pending === 'ADDRESS' ? (
            <p className="auth-notice" role="status">Selecciona una dirección para calcular el envío y el total.</p>
          ) : totals.pending === 'EMPTY' ? null : (
            <>
              <div><dt>Envío</dt><dd>{totals.deliveryFee === null ? '—' : formatPrice(totals.deliveryFee)}</dd></div>
              <div><dt>Comisión</dt><dd>{totals.commission === null ? '—' : formatPrice(totals.commission)}</dd></div>
              <div className="cart-total"><dt>Total</dt><dd>{totals.total === null ? '—' : formatPrice(totals.total)}</dd></div>
            </>
          )}
        </dl>
        <p className="auth-hint">Los importes los calcula Mikuna con los precios vigentes. La confirmación del pedido y el pago llegarán en un próximo incremento.</p>
      </section>

      <Link to="/restaurantes" className="auth-switch">Seguir buscando restaurantes</Link>
    </AuthLayout>
  )
}
