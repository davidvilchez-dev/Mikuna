import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import type { OrderStatus } from '../restaurant/orders-logic'
import {
  deliveryDetail,
  listDeliveries,
  listOrders,
  orderDetail,
  type DeliveryDetail,
  type DeliverySummary,
  type OrderDetail,
  type OrderSummary,
  type Page,
  type StatusChange,
} from './admin-api'
import {
  formatDateTime,
  formatMoney,
  orderStatusLabel,
  pageSummary,
  paymentLabel,
  statusOptionsFor,
  type OrdersTab,
} from './admin-logic'
import '../restaurant/restaurant.css'
import './admin.css'

type Row = OrderSummary | DeliverySummary
type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; data: Page<Row> }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Consulta administrativa (RF-33): todos los pedidos y entregas, por estado, con su pago y cambios de estado. Solo lectura. */
export function AdminOrdersPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [tab, setTab] = useState<OrdersTab>('orders')
  const [status, setStatus] = useState<OrderStatus | ''>('')
  const [page, setPage] = useState(0)
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [openId, setOpenId] = useState<string | null>(null)
  const [orderInfo, setOrderInfo] = useState<OrderDetail | null>(null)
  const [deliveryInfo, setDeliveryInfo] = useState<DeliveryDetail | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

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
    const request: Promise<Page<Row>> = tab === 'orders' ? listOrders(token, status, page) : listDeliveries(token, status, page)
    request
      .then((data) => { if (active) setView({ kind: 'ready', data }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setView({ kind: 'forbidden' })
        else setView({ kind: 'error', message: fail(error) })
      })
    return () => { active = false }
  }, [token, tab, status, page, attempt, fail])

  useEffect(() => {
    if (!token || !openId) return
    let active = true
    const load = tab === 'orders'
      ? orderDetail(token, openId).then((value) => { if (active) setOrderInfo(value) })
      : deliveryDetail(token, openId).then((value) => { if (active) setDeliveryInfo(value) })
    load.catch((error: unknown) => { if (active) setNotice(fail(error)) })
    return () => { active = false }
  }, [token, tab, openId, fail])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  function changeTab(next: OrdersTab) {
    setTab(next)
    setStatus('')
    setPage(0)
    setOpenId(null)
    setOrderInfo(null)
    setDeliveryInfo(null)
    setView({ kind: 'loading' })
  }

  function changeStatus(next: OrderStatus | '') {
    setStatus(next)
    setPage(0)
    setOpenId(null)
    setView({ kind: 'loading' })
  }

  if (view.kind === 'forbidden') {
    return (
      <AuthLayout title="Pedidos y entregas">
        <p className="auth-alert" role="alert">Esta sección es solo para administradores.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout wide title="Pedidos y entregas" description="Consulta todos los pedidos y entregas de la plataforma, del más reciente al más antiguo.">
      <div className="admin-tabs" role="tablist" aria-label="Qué consultar">
        <button type="button" role="tab" className="admin-tab" aria-selected={tab === 'orders'} onClick={() => changeTab('orders')}>Pedidos</button>
        <button type="button" role="tab" className="admin-tab" aria-selected={tab === 'deliveries'} onClick={() => changeTab('deliveries')}>Entregas</button>
      </div>
      <div className="admin-filters">
        <label>Estado
          <select value={status} onChange={(e) => changeStatus(e.target.value as OrderStatus | '')}>
            <option value="">Todos</option>
            {statusOptionsFor(tab).map((value) => <option key={value} value={value}>{orderStatusLabel(value)}</option>)}
          </select>
        </label>
      </div>

      {notice && <p className="auth-alert" role="alert">{notice}</p>}
      {view.kind === 'loading' && <p className="auth-description" role="status">Cargando…</p>}
      {view.kind === 'error' && (
        <>
          <p className="auth-alert" role="alert">{view.message}</p>
          <button type="button" className="auth-submit" onClick={() => { setView({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
        </>
      )}
      {view.kind === 'ready' && view.data.items.length === 0 && (
        <p className="auth-description" role="status">No hay {tab === 'orders' ? 'pedidos' : 'entregas'} en ese estado.</p>
      )}
      {view.kind === 'ready' && view.data.items.length > 0 && (
        <ul className="restaurant-list">
          {view.data.items.map((item) => {
            const open = openId === item.id
            return (
              <li key={item.id} className="restaurant-card">
                {'customer' in item ? (
                  <>
                    <h2>{item.restaurant.name} · {formatMoney(item.total)}</h2>
                    <dl>
                      <div><dt>Estado</dt><dd>{orderStatusLabel(item.status)}</dd></div>
                      <div><dt>Cliente</dt><dd>{item.customer.name ?? '—'}</dd></div>
                      <div><dt>Pago</dt><dd>{paymentLabel(item.paymentStatus)}</dd></div>
                      <div><dt>Creado</dt><dd>{formatDateTime(item.createdAt)}</dd></div>
                    </dl>
                  </>
                ) : (
                  <>
                    <h2>{item.restaurantName}</h2>
                    <dl>
                      <div><dt>Estado</dt><dd>{orderStatusLabel(item.status)}</dd></div>
                      <div><dt>Repartidor</dt><dd>{item.courier?.name ?? 'Sin repartidor'}</dd></div>
                      <div><dt>Pedido creado</dt><dd>{formatDateTime(item.createdAt)}</dd></div>
                    </dl>
                  </>
                )}
                <div className="restaurant-actions">
                  <button type="button" className="auth-submit auth-submit-secondary" aria-expanded={open}
                    onClick={() => { setOpenId(open ? null : item.id); setOrderInfo(null); setDeliveryInfo(null); setNotice(null) }}>
                    {open ? 'Ocultar detalle' : 'Ver detalle'}
                  </button>
                </div>
                {open && tab === 'orders' && orderInfo?.id === item.id && <OrderPanel detail={orderInfo} />}
                {open && tab === 'deliveries' && deliveryInfo?.id === item.id && <DeliveryPanel detail={deliveryInfo} />}
              </li>
            )
          })}
        </ul>
      )}
      {view.kind === 'ready' && (
        <div className="admin-pager">
          <span role="status">{pageSummary(view.data.page, view.data.totalPages, view.data.totalItems)}</span>
          <div className="restaurant-actions">
            <button type="button" className="auth-submit auth-submit-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button>
            <button type="button" className="auth-submit auth-submit-secondary" disabled={page + 1 >= view.data.totalPages} onClick={() => setPage(page + 1)}>Siguiente</button>
          </div>
        </div>
      )}
      <Link to="/admin/cuentas" className="auth-switch">Gestionar cuentas</Link>
      <Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link>
    </AuthLayout>
  )
}

function Changes({ changes }: { changes: StatusChange[] }) {
  return (
    <>
      <h3>Cambios de estado</h3>
      <ol className="admin-log">
        {changes.map((change, index) => (
          <li key={index}>
            {orderStatusLabel(change.from)} → {orderStatusLabel(change.to)} · {formatDateTime(change.changedAt)} ·{' '}
            {change.actorName ?? (change.actorRole === 'SYSTEM' ? 'Sistema' : change.actorRole)}
            {change.reason ? ` · ${change.reason}` : ''}
          </li>
        ))}
      </ol>
    </>
  )
}

function OrderPanel({ detail }: { detail: OrderDetail }) {
  return (
    <div className="admin-detail">
      <p>
        Cliente: {detail.customer.name ?? '—'}{detail.customer.email ? ` (${detail.customer.email})` : ''} · Repartidor:{' '}
        {detail.courier?.name ?? 'sin asignar'}
      </p>
      <p>Entrega en {detail.deliveryAddress.street}, {detail.deliveryAddress.district}</p>
      <table className="admin-table">
        <thead><tr><th>Producto</th><th>Cant.</th><th>Precio</th><th>Total</th></tr></thead>
        <tbody>
          {detail.lines.map((line) => (
            <tr key={line.productId + line.name}><td>{line.name}</td><td>{line.quantity}</td><td>{formatMoney(line.unitPrice)}</td><td>{formatMoney(line.lineTotal)}</td></tr>
          ))}
        </tbody>
      </table>
      <p>
        Subtotal {formatMoney(detail.amounts.subtotal)} · Envío {formatMoney(detail.amounts.deliveryFee)} · Comisión{' '}
        {formatMoney(detail.amounts.commission)} · <strong>Total {formatMoney(detail.amounts.total)}</strong>
      </p>
      <p>Pago: {paymentLabel(detail.payment.status)} ({detail.payment.method})</p>
      <Changes changes={detail.statusChanges} />
    </div>
  )
}

function DeliveryPanel({ detail }: { detail: DeliveryDetail }) {
  return (
    <div className="admin-detail">
      <p>
        Listo desde {formatDateTime(detail.readyAt)}
        {detail.assignedAt ? ` · asignado ${formatDateTime(detail.assignedAt)}` : ''} · Repartidor: {detail.courier?.name ?? 'sin asignar'}
      </p>
      <p>
        Pedido de {detail.order.customer.name ?? '—'} · {formatMoney(detail.order.total)} · Pago: {paymentLabel(detail.order.paymentStatus)}
      </p>
      <Changes changes={detail.statusChanges} />
    </div>
  )
}
