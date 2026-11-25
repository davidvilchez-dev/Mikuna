import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { errorMessage, fetchMe, type Role } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { useSession } from '../auth/RequireSession'
import { formatPrice } from '../catalog/catalog-validation'
import { orderCode, orderStatusLabels } from '../restaurant/orders-logic'
import { paymentStatusLabel, type PaymentStatus } from '../checkout/checkout-api'
import { fetchHistory, type HistoryItem } from './history-api'
import {
  emptyFilter,
  formatHistoryDate,
  hasHistory,
  mergePages,
  statusOptions,
  validateRange,
  type HistoryFilter,
} from './history-logic'
import { RatingControl } from './RatingControl'
import '../restaurant/restaurant.css'
import './history.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; items: HistoryItem[]; page: number; totalPages: number; totalItems: number }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

const TITLES: Partial<Record<Role, string>> = { CUSTOMER: 'Mis pedidos', COURIER: 'Mis entregas', RESTAURANT: 'Historial de pedidos' }

/** Historial propio de pedidos o entregas (RF-30), paginado, del más reciente al más antiguo, con filtros y calificación (RF-31). */
export function HistoryPage() {
  const session = useSession()
  const token = session.token
  const [role, setRole] = useState<Role | null>(null)
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [draft, setDraft] = useState<HistoryFilter>(emptyFilter)
  const [filter, setFilter] = useState<HistoryFilter>(emptyFilter)
  const [rangeError, setRangeError] = useState<string | undefined>()
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    fetchMe(token)
      .then((me) => {
        if (!active) return
        if (!hasHistory(me.role)) setView({ kind: 'forbidden' })
        else setRole(me.role)
      })
      .catch((error: unknown) => { if (active) setView({ kind: 'error', message: errorMessage(error) }) })
    return () => { active = false }
  }, [token])

  useEffect(() => {
    if (!token || !role) return
    let active = true
    fetchHistory(role, token, filter, 0)
      .then((result) => { if (active) setView({ kind: 'ready', items: result.items, page: result.page, totalPages: result.totalPages, totalItems: result.totalItems }) })
      .catch((error: unknown) => { if (active) setView({ kind: 'error', message: errorMessage(error) }) })
    return () => { active = false }
  }, [token, role, filter])


  function apply() {
    const problem = validateRange(draft.from, draft.to)
    setRangeError(problem)
    if (problem) return
    setView({ kind: 'loading' })
    setFilter(draft)
  }

  function clear() {
    setRangeError(undefined)
    setDraft(emptyFilter)
    setView({ kind: 'loading' })
    setFilter(emptyFilter)
  }

  async function loadMore(current: Extract<ViewState, { kind: 'ready' }>) {
    if (busy || !role) return
    setBusy(true)
    try {
      const result = await fetchHistory(role, token, filter, current.page + 1)
      setView({ kind: 'ready', items: mergePages(current.items, result.items), page: result.page, totalPages: result.totalPages, totalItems: result.totalItems })
    } catch (error) {
      setView({ kind: 'error', message: errorMessage(error) })
    } finally {
      setBusy(false)
    }
  }

  function markRated(orderId: string, value: number) {
    setView((current) => current.kind !== 'ready' ? current : {
      ...current,
      items: current.items.map((item) => item.orderId === orderId
        ? { ...item, canRate: false, rating: value > 0 ? value : item.rating }
        : item),
    })
  }

  if (view.kind === 'forbidden') {
    return (
      <AuthLayout title="Mi historial">
        <p className="auth-alert" role="alert">Tu cuenta no tiene historial.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  const subject = role === 'COURIER' ? 'entregas' : 'pedidos'

  return (
    <AuthLayout wide title={(role && TITLES[role]) ?? 'Mi historial'} description={`Tus ${subject}, del más reciente al más antiguo.`}>
      {role && (
        <form className="history-filters" onSubmit={(event) => { event.preventDefault(); apply() }}>
          <label>Estado
            <select value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value as HistoryFilter['status'] })}>
              <option value="">Todos</option>
              {statusOptions(role).map((status) => <option key={status} value={status}>{orderStatusLabels[status]}</option>)}
            </select>
          </label>
          <label>Desde
            <input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
          </label>
          <label>Hasta
            <input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
          </label>
          <button type="submit" className="auth-submit">Filtrar</button>
          <button type="button" className="auth-submit auth-submit-secondary" onClick={clear}>Quitar filtros</button>
        </form>
      )}
      {rangeError && <p className="auth-alert" role="alert">{rangeError}</p>}
      {view.kind === 'loading' && <p className="auth-description" role="status">Cargando…</p>}
      {view.kind === 'error' && <p className="auth-alert" role="alert">{view.message}</p>}
      {view.kind === 'ready' && (
        <>
          {view.items.length === 0 && <p className="auth-description" role="status">No hay {subject} para mostrar.</p>}
          <ul className="history-list">
            {view.items.map((item) => (
              <li key={item.orderId} className="history-item">
                <div className="history-head">
                  <strong>{item.restaurant?.name ?? `Pedido de ${item.customerName ?? 'cliente'}`} <small>{orderCode(item.orderId)}</small></strong>
                  <span>{orderStatusLabels[item.status]}</span>
                  <span className="history-time">{formatHistoryDate(item.createdAt)}</span>
                </div>
                {item.restaurant && item.customerName && <p className="auth-hint">Cliente: {item.customerName}</p>}
                <ul className="checkout-lines">
                  {item.lines.map((line) => (
                    <li key={line.name + line.quantity}>
                      <span>{line.quantity} x {line.name}{line.unitPrice != null ? ` (${formatPrice(line.unitPrice)} c/u)` : ''}</span>
                      {line.lineTotal != null && <span>{formatPrice(line.lineTotal)}</span>}
                    </li>
                  ))}
                </ul>
                {item.deliveryAddress && (
                  <p className="auth-hint">Entrega en: {item.deliveryAddress.street}, {item.deliveryAddress.district}{item.deliveryAddress.reference ? ` (${item.deliveryAddress.reference})` : ''}</p>
                )}
                {item.totals && <p>Total: {formatPrice(item.totals.total)} (envío {formatPrice(item.totals.deliveryFee)}, tarifa de servicio {formatPrice(item.totals.commission)})</p>}
                {item.deliveryFee != null && <p>Costo de envío: {formatPrice(item.deliveryFee)}</p>}
                {item.payment && <p className="auth-hint">Pago: {paymentStatusLabel(item.payment.method, item.payment.status as PaymentStatus)}</p>}
                <p className="history-rating">{item.rating != null ? `Calificación: ${item.rating} de 5` : 'Sin calificar'}</p>
                {role === 'CUSTOMER' && item.canRate && (
                  <RatingControl token={token} orderId={item.orderId} onRated={(value) => markRated(item.orderId, value)} />
                )}
                {role === 'CUSTOMER' && <Link to={`/pedido/${item.orderId}`} className="auth-switch">Ver pedido</Link>}
              </li>
            ))}
          </ul>
          {view.page + 1 < view.totalPages && (
            <button type="button" className="auth-submit" disabled={busy} onClick={() => void loadMore(view)}>Ver más</button>
          )}
        </>
      )}
    </AuthLayout>
  )
}
