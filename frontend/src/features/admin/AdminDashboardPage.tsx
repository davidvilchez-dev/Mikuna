import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, errorMessage } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { useSession } from '../auth/RequireSession'
import { listPending } from '../delivery/admin-deliveries-api'
import { listOrders, countOf, type OrderSummary } from './admin-api'
import { AdminLayout } from './AdminLayout'
import { Badge, EmptyState, ErrorState, Icon, LoadingBlock, type IconName } from './admin-ui'
import { formatDateTime, formatMoney, ORDER_STATUSES, orderStatusLabel, statusTone } from './admin-logic'
import { orderCode, type OrderStatus } from '../restaurant/orders-logic'

interface Metrics {
  users: number
  customers: number
  restaurantsApproved: number
  restaurantsPending: number
  couriersActive: number
  orders: number
  byStatus: Record<OrderStatus, number>
  pendingDeliveries: number
  recent: OrderSummary[]
}

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; metrics: Metrics }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

const BAR_TONE: Partial<Record<OrderStatus, string>> = { DELIVERED: 'ok', CANCELLED: 'bad', REJECTED: 'bad', PENDING: 'accent' }
const number = new Intl.NumberFormat('es-PE')

async function loadMetrics(token: string): Promise<Metrics> {
  // Todo sale de los listados que el backend ya expone: se pide una fila por consulta y se lee el total.
  const [users, customers, restaurantsApproved, restaurantsPending, couriersActive, orders, deliveries, recent, ...statuses] = await Promise.all([
    countOf(token, '/api/admin/users'),
    countOf(token, '/api/admin/users', { role: 'CUSTOMER' }),
    countOf(token, '/api/admin/restaurants/search', { registration: 'APPROVED' }),
    countOf(token, '/api/admin/restaurants/search', { registration: 'PENDING' }),
    countOf(token, '/api/admin/couriers', { operation: 'ACTIVE' }),
    countOf(token, '/api/admin/orders'),
    listPending(token),
    listOrders(token, '', 0),
    ...ORDER_STATUSES.map((status) => countOf(token, '/api/admin/orders', { status })),
  ])
  const byStatus = Object.fromEntries(ORDER_STATUSES.map((status, index) => [status, statuses[index]])) as Record<OrderStatus, number>
  return {
    users, customers, restaurantsApproved, restaurantsPending, couriersActive, orders, byStatus,
    pendingDeliveries: deliveries.length,
    recent: recent.items.slice(0, 6),
  }
}

/** Resumen de la plataforma: lo que requiere atención, la actividad de pedidos y los últimos movimientos. */
export function AdminDashboardPage() {
  const session = useSession()
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const token = session.token

  useEffect(() => {
    let active = true
    loadMetrics(token)
      .then((metrics) => { if (active) setView({ kind: 'ready', metrics }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setView({ kind: 'forbidden' })
        else setView({ kind: 'error', message: errorMessage(error) })
      })
    return () => { active = false }
  }, [token, attempt])


  if (view.kind === 'forbidden') {
    return (
      <AuthLayout title="Panel administrativo">
        <p className="auth-alert" role="alert">Esta sección es solo para administradores.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  const reload = () => { setView({ kind: 'loading' }); setAttempt((n) => n + 1) }

  return (
    <AdminLayout
      title="Panel"
      description="Qué está pasando en Mikuna y qué necesita tu atención."
      actions={<button type="button" className="adm-btn adm-btn-ghost" onClick={reload}><Icon name="refresh" />Actualizar</button>}
    >
      {view.kind === 'loading' && (<><LoadingBlock label="Cargando métricas" /><LoadingBlock label="Cargando pedidos" /></>)}
      {view.kind === 'error' && <ErrorState message={view.message} onRetry={reload} />}
      {view.kind === 'ready' && <Content metrics={view.metrics} />}
    </AdminLayout>
  )
}

function Content({ metrics }: { metrics: Metrics }) {
  const attention = metrics.restaurantsPending + metrics.pendingDeliveries
  const max = Math.max(1, ...Object.values(metrics.byStatus))
  return (
    <>
      {attention > 0 && (
        <p className="adm-note" role="status">
          <Icon name="alert" />
          Hay {attention} {attention === 1 ? 'asunto' : 'asuntos'} por resolver:{' '}
          {metrics.restaurantsPending > 0 && <Link to="/admin/solicitudes"><u>{metrics.restaurantsPending} {metrics.restaurantsPending === 1 ? 'solicitud' : 'solicitudes'} de restaurante</u></Link>}
          {metrics.restaurantsPending > 0 && metrics.pendingDeliveries > 0 && ' y '}
          {metrics.pendingDeliveries > 0 && <Link to="/admin/entregas"><u>{metrics.pendingDeliveries} {metrics.pendingDeliveries === 1 ? 'entrega' : 'entregas'} sin repartidor</u></Link>}.
        </p>
      )}

      <dl className="adm-stats">
        <Stat icon="inbox" label="Solicitudes pendientes" value={metrics.restaurantsPending} to="/admin/solicitudes" tone={metrics.restaurantsPending > 0 ? 'accent' : undefined} hint="Restaurantes por aprobar" />
        <Stat icon="truck" label="Entregas por asignar" value={metrics.pendingDeliveries} to="/admin/entregas" tone={metrics.pendingDeliveries > 0 ? 'accent' : undefined} hint="Sin repartidor o inhabilitado" />
        <Stat icon="receipt" label="Pedidos totales" value={metrics.orders} to="/admin/pedidos" hint={`${number.format(metrics.byStatus.DELIVERED)} entregados`} />
        <Stat icon="store" label="Restaurantes aprobados" value={metrics.restaurantsApproved} to="/admin/cuentas" hint="Publicados en la plataforma" />
        <Stat icon="scooter" label="Repartidores activos" value={metrics.couriersActive} to="/admin/cuentas" hint="Candidatos a entregas" />
        <Stat icon="users" label="Usuarios" value={metrics.users} to="/admin/cuentas" hint={`${number.format(metrics.customers)} clientes`} />
      </dl>

      <div className="adm-grid-2">
        <section className="adm-card" aria-labelledby="by-status">
          <div className="adm-card-head"><h2 id="by-status">Pedidos por estado</h2><Link to="/admin/pedidos">Ver todos</Link></div>
          {metrics.orders === 0 ? <EmptyState title="Aún no hay pedidos" text="Cuando los clientes empiecen a pedir, verás aquí su avance." /> : (
            <ul className="adm-bars">
              {ORDER_STATUSES.map((status) => (
                <li key={status} className="adm-bar">
                  <span>{orderStatusLabel(status)}</span>
                  <span className="adm-bar-track" aria-hidden="true">
                    <span className="adm-bar-fill" data-tone={BAR_TONE[status]} style={{ width: `${(metrics.byStatus[status] / max) * 100}%`, opacity: metrics.byStatus[status] ? 1 : 0.15 }} />
                  </span>
                  <output>{number.format(metrics.byStatus[status])}</output>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="adm-card" aria-labelledby="recent">
          <div className="adm-card-head"><h2 id="recent">Últimos pedidos</h2><Link to="/admin/pedidos">Ver todos</Link></div>
          {metrics.recent.length === 0 ? <EmptyState title="Sin pedidos recientes" /> : (
            <ul className="adm-timeline">
              {metrics.recent.map((order) => (
                <li key={order.id}>
                  <span><strong>{orderCode(order.id)} · {order.restaurant.name ?? 'Restaurante'}</strong> · {formatMoney(order.total)}</span>
                  <span><Badge tone={statusTone(order.status)}>{orderStatusLabel(order.status)}</Badge></span>
                  <small>{order.customer.name ?? 'Cliente'} · {formatDateTime(order.createdAt)}</small>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  )
}

function Stat({ icon, label, value, hint, to, tone }: { icon: IconName; label: string; value: number; hint?: string; to: string; tone?: 'accent' }) {
  return (
    <div className="adm-stat" data-tone={tone}>
      <span className="adm-stat-icon" aria-hidden="true"><Icon name={icon} /></span>
      <dt>{label}</dt>
      <dd>{number.format(value)}</dd>
      {hint && <small>{hint}</small>}
      <Link to={to} aria-label={`${label}: ${value}. Abrir`} />
    </div>
  )
}
