import { Fragment, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, errorMessage } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { useSession } from '../auth/RequireSession'
import { orderCode, type OrderStatus } from '../restaurant/orders-logic'
import { Modal } from '../ui/Modal'
import { notifySuccess } from '../ui/notify'
import {
  cancelAdminOrder,
  deliveryDetail,
  listDeliveries,
  listOrders,
  orderDetail,
  type DeliveryDetail,
  type DeliverySummary,
  type OrderDetail,
  type OrderSummary,
  type Page,
  type PaymentTransaction,
  type StatusChange,
} from './admin-api'
import { AdminLayout } from './AdminLayout'
import { Badge, EmptyState, ErrorState, LoadingBlock, Pager } from './admin-ui'
import {
  CANCEL_REASON_MAX,
  canAdminCancel,
  formatDateTime,
  formatMoney,
  orderStatusLabel,
  pageSummary,
  statusTone,
  paymentLabel,
  statusOptionsFor,
  transactionStatusLabels,
  transactionTypeLabels,
  validateCancelReason,
  type OrdersTab,
} from './admin-logic'

type Row = OrderSummary | DeliverySummary
type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; data: Page<Row> }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/**
 * Consulta administrativa (RF-33): todos los pedidos y entregas, por estado, con su pago, transacciones y cambios de
 * estado. Desde la spec 014, el administrador cancela un pedido aceptado y no entregado con motivo y reembolso.
 */
export function AdminOrdersPage() {
  const session = useSession()
  const [tab, setTab] = useState<OrdersTab>('orders')
  const [status, setStatus] = useState<OrderStatus | ''>('')
  const [page, setPage] = useState(0)
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [openId, setOpenId] = useState<string | null>(null)
  const [orderInfo, setOrderInfo] = useState<OrderDetail | null>(null)
  const [deliveryInfo, setDeliveryInfo] = useState<DeliveryDetail | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState<OrderDetail | null>(null)

  const token = session.token

  useEffect(() => {
    let active = true
    const request: Promise<Page<Row>> = tab === 'orders' ? listOrders(token, status, page) : listDeliveries(token, status, page)
    request
      .then((data) => { if (active) setView({ kind: 'ready', data }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setView({ kind: 'forbidden' })
        else setView({ kind: 'error', message: errorMessage(error) })
      })
    return () => { active = false }
  }, [token, tab, status, page, attempt])

  useEffect(() => {
    if (!token || !openId) return
    let active = true
    const load = tab === 'orders'
      ? orderDetail(token, openId).then((value) => { if (active) setOrderInfo(value) })
      : deliveryDetail(token, openId).then((value) => { if (active) setDeliveryInfo(value) })
    load.catch((error: unknown) => { if (active) setNotice(errorMessage(error)) })
    return () => { active = false }
  }, [token, tab, openId])


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

  const reload = () => { setView({ kind: 'loading' }); setAttempt((n) => n + 1) }
  const columns = tab === 'orders' ? 6 : 4

  return (
    <AdminLayout title="Pedidos y entregas" description="Todos los pedidos y entregas de la plataforma, del más reciente al más antiguo.">
      <div className="adm-seg" role="tablist" aria-label="Qué consultar">
        <button type="button" role="tab" aria-selected={tab === 'orders'} onClick={() => changeTab('orders')}>Pedidos</button>
        <button type="button" role="tab" aria-selected={tab === 'deliveries'} onClick={() => changeTab('deliveries')}>Entregas</button>
      </div>
      <div className="adm-toolbar">
        <label>Estado
          <select value={status} onChange={(e) => changeStatus(e.target.value as OrderStatus | '')}>
            <option value="">Todos</option>
            {statusOptionsFor(tab).map((value) => <option key={value} value={value}>{orderStatusLabel(value)}</option>)}
          </select>
        </label>
      </div>

      {notice && <p className="adm-alert" role="alert">{notice}</p>}
      {view.kind === 'loading' && <LoadingBlock />}
      {view.kind === 'error' && <ErrorState message={view.message} onRetry={reload} />}
      {view.kind === 'ready' && view.data.items.length === 0 && (
        <EmptyState title={`Sin ${tab === 'orders' ? 'pedidos' : 'entregas'}`} text="No hay registros en ese estado." />
      )}
      {view.kind === 'ready' && view.data.items.length > 0 && (
        <div className="adm-table-wrap">
          <table className="adm-table">
            <thead>
              <tr>
                <th scope="col">Restaurante</th>
                {tab === 'orders' ? <th scope="col">Cliente</th> : <th scope="col">Repartidor</th>}
                <th scope="col">Estado</th>
                {tab === 'orders' && <th scope="col">Pago</th>}
                {tab === 'orders' && <th scope="col" className="adm-num">Total</th>}
                <th scope="col">Creado</th>
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {view.data.items.map((item) => {
                const open = openId === item.id
                const isOrder = 'customer' in item
                return (
                  <Fragment key={item.id}>
                    <tr className={open ? 'adm-row-open' : undefined}>
                      <td>
                        <div className="adm-cell-main">
                          <strong>{isOrder ? item.restaurant.name : item.restaurantName}</strong>
                          <span>Pedido {orderCode(isOrder ? item.id : item.orderId)}</span>
                        </div>
                      </td>
                      <td>{isOrder ? (item.customer.name ?? '—') : (item.courier?.name ?? 'Sin repartidor')}</td>
                      <td><Badge tone={statusTone(item.status)}>{orderStatusLabel(item.status)}</Badge></td>
                      {isOrder && <td>{paymentLabel(item.paymentStatus)}</td>}
                      {isOrder && <td className="adm-num"><strong>{formatMoney(item.total)}</strong></td>}
                      <td>{formatDateTime(item.createdAt)}</td>
                      <td className="adm-actions-cell">
                        <button type="button" className="adm-btn adm-btn-ghost adm-btn-sm" aria-expanded={open}
                          onClick={() => { setOpenId(open ? null : item.id); setOrderInfo(null); setDeliveryInfo(null); setNotice(null) }}>
                          {open ? 'Ocultar' : 'Ver detalle'}
                        </button>
                      </td>
                    </tr>
                    {open && (
                      <tr>
                        <td colSpan={columns + 1} className="adm-detail-cell">
                          {tab === 'orders' && (orderInfo?.id === item.id ? <OrderPanel detail={orderInfo} onCancel={() => setCancelling(orderInfo)} /> : <div className="adm-detail"><LoadingBlock /></div>)}
                          {tab === 'deliveries' && (deliveryInfo?.id === item.id ? <DeliveryPanel detail={deliveryInfo} /> : <div className="adm-detail"><LoadingBlock /></div>)}
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {cancelling && (
        <CancelOrderDialog token={token} order={cancelling} onClose={() => setCancelling(null)}
          onCancelled={(updated) => {
            setCancelling(null)
            setOrderInfo(updated)
            notifySuccess(updated.payment.status === 'REFUNDED' ? 'Pedido cancelado y reembolsado' : 'Pedido cancelado')
            setAttempt((n) => n + 1)
          }} />
      )}
      {view.kind === 'ready' && (
        <Pager summary={pageSummary(view.data.page, view.data.totalPages, view.data.totalItems)} page={page} totalPages={view.data.totalPages} onChange={setPage} />
      )}
    </AdminLayout>
  )
}

function Changes({ changes }: { changes: StatusChange[] }) {
  return (
    <>
      <h3>Cambios de estado</h3>
      <ol className="adm-timeline">
        {changes.map((change, index) => (
          <li key={index}>
            <strong>{orderStatusLabel(change.from)} → {orderStatusLabel(change.to)}</strong>
            <small>
              {formatDateTime(change.changedAt)} · {change.actorName ?? (change.actorRole === 'SYSTEM' ? 'Sistema' : change.actorRole)}
              {change.reason ? ` · ${change.reason}` : ''}
            </small>
          </li>
        ))}
      </ol>
    </>
  )
}

function OrderPanel({ detail, onCancel }: { detail: OrderDetail; onCancel: () => void }) {
  return (
    <div className="adm-detail">
      {canAdminCancel(detail.status) && (
        <div className="adm-detail-actions">
          <button type="button" className="adm-btn adm-btn-danger adm-btn-sm" onClick={onCancel}>Cancelar pedido</button>
        </div>
      )}
      <dl className="adm-facts">
        <div><dt>Cliente</dt><dd>{detail.customer.name ?? '—'}{detail.customer.email ? ` (${detail.customer.email})` : ''}</dd></div>
        <div><dt>Repartidor</dt><dd>{detail.courier?.name ?? 'Sin asignar'}</dd></div>
        <div><dt>Entrega en</dt><dd>{detail.deliveryAddress.street}, {detail.deliveryAddress.district}</dd></div>
        <div><dt>Pago</dt><dd>{paymentLabel(detail.payment.status)} ({detail.payment.method})</dd></div>
      </dl>
      <table className="adm-table-inner">
        <thead><tr><th>Producto</th><th className="adm-num">Cant.</th><th className="adm-num">Precio</th><th className="adm-num">Total</th></tr></thead>
        <tbody>
          {detail.lines.map((line) => (
            <tr key={line.productId + line.name}>
              <td>{line.name}</td><td className="adm-num">{line.quantity}</td>
              <td className="adm-num">{formatMoney(line.unitPrice)}</td><td className="adm-num">{formatMoney(line.lineTotal)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="adm-totals">
        <span>Subtotal {formatMoney(detail.amounts.subtotal)}</span>
        <span>Envío {formatMoney(detail.amounts.deliveryFee)}</span>
        <span>Comisión {formatMoney(detail.amounts.commission)}</span>
        <strong>Total {formatMoney(detail.amounts.total)}</strong>
      </p>
      <Changes changes={detail.statusChanges} />
      <Transactions items={detail.transactions ?? []} />
    </div>
  )
}

function Transactions({ items }: { items: PaymentTransaction[] }) {
  if (items.length === 0) return null
  return (
    <>
      <h3>Transacciones de pago</h3>
      <table className="adm-table-inner">
        <thead><tr><th>Operación</th><th>Resultado</th><th className="adm-num">Importe</th><th>Fecha</th></tr></thead>
        <tbody>
          {items.map((item, index) => (
            <tr key={index}>
              <td>{transactionTypeLabels[item.type] ?? item.type}</td>
              <td>{transactionStatusLabels[item.status] ?? item.status}{item.code ? ` (${item.code})` : ''}</td>
              <td className="adm-num">{formatMoney(item.amount)}</td>
              <td>{formatDateTime(item.at)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

interface CancelDialogProps {
  token: string
  order: OrderDetail
  onClose: () => void
  onCancelled: (updated: OrderDetail) => void
}

/** Motivo obligatorio y confirmación; si la pasarela falla, el pedido no cambia y se puede reintentar (spec 014). */
function CancelOrderDialog({ token, order, onClose, onCancelled }: CancelDialogProps) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const card = order.payment.method === 'CARD' && order.payment.status === 'CAPTURED'

  async function submit() {
    const invalid = validateCancelReason(reason)
    if (invalid) { setError(invalid); return }
    setBusy(true)
    setError(null)
    try {
      onCancelled(await cancelAdminOrder(token, order.id, reason))
    } catch (failure) {
      setError(errorMessage(failure))
      setBusy(false)
    }
  }

  return (
    <Modal title={'Cancelar pedido ' + orderCode(order.id)} onClose={onClose}>
      <p className="mk-confirm-text">
        {card
          ? `Se devolverá al cliente el total cobrado (${formatMoney(order.amounts.total)}).`
          : 'El pedido se pagaba en efectivo: no hay cobro que devolver.'}
        {' '}El cliente, el restaurante y el repartidor (si lo hay) recibirán un aviso con el motivo.
      </p>
      <div className="auth-field adm-cancel-field">
        <label htmlFor="adm-cancel-reason">Motivo de la cancelación</label>
        <textarea id="adm-cancel-reason" rows={3} value={reason} maxLength={CANCEL_REASON_MAX + 50}
          aria-invalid={error ? true : undefined} aria-describedby="adm-cancel-count"
          onChange={(e) => setReason(e.target.value)} disabled={busy} />
        <small id="adm-cancel-count">{reason.trim().length}/{CANCEL_REASON_MAX}</small>
        {error && <p className="auth-error" role="alert">{error}</p>}
      </div>
      <div className="mk-confirm-actions">
        <button type="button" className="mk-confirm-btn mk-confirm-cancel" onClick={onClose} disabled={busy}>Volver</button>
        <button type="button" className="mk-confirm-btn mk-confirm-danger" onClick={() => void submit()} disabled={busy}>
          {busy ? 'Cancelando…' : card ? 'Cancelar y reembolsar' : 'Cancelar pedido'}
        </button>
      </div>
    </Modal>
  )
}

function DeliveryPanel({ detail }: { detail: DeliveryDetail }) {
  return (
    <div className="adm-detail">
      <dl className="adm-facts">
        <div><dt>Listo desde</dt><dd>{formatDateTime(detail.readyAt)}</dd></div>
        {detail.assignedAt && <div><dt>Asignado</dt><dd>{formatDateTime(detail.assignedAt)}</dd></div>}
        <div><dt>Repartidor</dt><dd>{detail.courier?.name ?? 'Sin asignar'}</dd></div>
        <div><dt>Pedido de</dt><dd>{detail.order.customer.name ?? '—'} · {formatMoney(detail.order.total)}</dd></div>
        <div><dt>Pago</dt><dd>{paymentLabel(detail.order.paymentStatus)}</dd></div>
      </dl>
      <Changes changes={detail.statusChanges} />
    </div>
  )
}
