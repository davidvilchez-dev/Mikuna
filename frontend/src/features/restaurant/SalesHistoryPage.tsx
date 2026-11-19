import { useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AppIcon } from '../auth/AppIcon'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { formatPrice } from '../catalog/catalog-validation'
import type { HistoryItem } from '../history/history-api'
import { orderStatusLabels } from './orders-logic'
import { fetchSalesHistory } from './sales-api'
import { LOST_STATUSES, RANGE_LABELS, SALE_STATUSES, rangeFor, summarize, type RangePreset } from './sales-logic'
import { RestaurantLayout } from './RestaurantLayout'
import './restaurant.css'
import './restaurant-dashboard.css'
import './restaurant-sales.css'

const PRESETS: RangePreset[] = ['today', 'week', 'month']
const PAGE = 15

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; items: HistoryItem[] }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

const dateFormat = new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

function shortCode(id: string): string {
  return '#' + id.replace(/-/g, '').slice(-4).toUpperCase()
}

function chipClass(status: HistoryItem['status']): string {
  if (status === 'DELIVERED') return 'rs-chip rs-chip-ok'
  if ((LOST_STATUSES as readonly string[]).includes(status)) return 'rs-chip rs-chip-bad'
  return 'rs-chip'
}

/** Historial de pedidos del restaurante con sus ventas por periodo (RF-30). Solo lectura. */
export function SalesHistoryPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [preset, setPreset] = useState<RangePreset>('today')
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [shown, setShown] = useState(PAGE)
  const token = session?.token ?? ''

  useEffect(() => {
    if (!token) return
    let active = true
    const { from, to } = rangeFor(preset, new Date())
    fetchSalesHistory(token, from, to)
      .then((items) => { if (active) setState({ kind: 'ready', items }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 401) { clearSession(); navigate('/iniciar-sesion', { replace: true }); return }
        if (error instanceof ApiError && (error.status === 403 || error.status === 404)) { setState({ kind: 'forbidden' }); return }
        setState({ kind: 'error', message: error instanceof ApiError ? error.message : GENERIC_ERROR })
      })
    return () => { active = false }
  }, [token, preset, attempt, navigate])

  const items = useMemo(() => (state.kind === 'ready' ? state.items : []), [state])
  const summary = useMemo(() => summarize(items), [items])
  const soldProducts = useMemo(() => {
    const totals = new Map<string, number>()
    for (const item of items) {
      if (!(SALE_STATUSES as readonly string[]).includes(item.status)) continue
      for (const line of item.lines) totals.set(line.name, (totals.get(line.name) ?? 0) + line.quantity)
    }
    return [...totals.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5)
  }, [items])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  if (state.kind === 'forbidden') {
    return (
      <AuthLayout title="Historial">
        <p className="auth-alert" role="alert">Esta sección es solo para cuentas de restaurante habilitadas.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  const top = soldProducts[0]?.[1] ?? 1
  return (
    <RestaurantLayout title="Historial y ventas" description="Cuánto vendiste y qué pedidos atendiste.">
      <div className="rs-tabs" role="group" aria-label="Periodo">
        {PRESETS.map((value) => (
          <button key={value} type="button" className="rs-tab" aria-pressed={preset === value}
            onClick={() => { setPreset(value); setShown(PAGE); setState({ kind: 'loading' }) }}>
            {RANGE_LABELS[value]}
          </button>
        ))}
      </div>

      {state.kind === 'loading' && <p className="auth-description" role="status">Calculando tus ventas…</p>}
      {state.kind === 'error' && (
        <>
          <p className="auth-alert" role="alert">{state.message}</p>
          <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
        </>
      )}

      {state.kind === 'ready' && (
        <>
          <div className="rv-stats">
            <div className="rv-sales">
              <span className="rv-sales-icon"><AppIcon name="chart" /></span>
              <small>Ventas · {RANGE_LABELS[preset].toLowerCase()}</small>
              <strong>{formatPrice(summary.sales)}</strong>
              <span>Solo el valor de los productos; no incluye envío ni comisión del servicio.</span>
            </div>
            <div className="rv-mini"><small>Pedidos atendidos</small><strong>{summary.orders}</strong></div>
            <div className="rv-mini"><small>Ticket promedio</small><strong>{formatPrice(summary.average)}</strong></div>
            <div className="rv-mini"><small>No concretados</small><strong>{summary.lost}</strong></div>
          </div>

          <div className="rv-grid">
            <section className="rs-card" aria-labelledby="rv-orders">
              <h2 id="rv-orders">Pedidos</h2>
              {items.length === 0 ? (
                <div className="rd-quiet">
                  <span className="rs-empty-icon"><AppIcon name="receipt" /></span>
                  <strong>Sin pedidos en este periodo</strong>
                  <p>Cuando recibas pedidos aparecerán aquí con su total.</p>
                </div>
              ) : (
                <>
                  <ul className="rv-list">
                    {items.slice(0, shown).map((item) => (
                      <li key={item.orderId}>
                        <div className="rv-row-main">
                          <strong>{shortCode(item.orderId)}</strong>
                          <span className={chipClass(item.status)}>{orderStatusLabels[item.status]}</span>
                        </div>
                        <p>{item.lines.map((line) => `${line.quantity}× ${line.name}`).join(', ')}</p>
                        <div className="rv-row-foot">
                          <small>{dateFormat.format(new Date(item.createdAt))}{item.customerName ? ` · ${item.customerName}` : ''}</small>
                          <b>{formatPrice(item.totals?.subtotal ?? 0)}</b>
                        </div>
                      </li>
                    ))}
                  </ul>
                  {shown < items.length && (
                    <button type="button" className="auth-submit auth-submit-secondary rv-more" onClick={() => setShown((n) => n + PAGE)}>
                      Ver más ({items.length - shown})
                    </button>
                  )}
                </>
              )}
            </section>

            <section className="rs-card" aria-labelledby="rv-top">
              <h2 id="rv-top">Lo más vendido</h2>
              {soldProducts.length === 0 ? (
                <p className="auth-description">Aún no hay ventas en este periodo.</p>
              ) : (
                <ol className="rv-top">
                  {soldProducts.map(([name, quantity]) => (
                    <li key={name}>
                      <div><span>{name}</span><b>{quantity}</b></div>
                      <span className="rv-bar"><span style={{ width: `${Math.max(8, (quantity / top) * 100)}%` }} /></span>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        </>
      )}
    </RestaurantLayout>
  )
}
