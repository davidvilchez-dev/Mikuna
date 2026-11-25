import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AppIcon, type AppIconName } from '../auth/AppIcon'
import { AuthLayout } from '../auth/AuthLayout'
import { useSession } from '../auth/RequireSession'
import { listMyProducts } from '../catalog/catalog-api'
import { formatPrice } from '../catalog/catalog-validation'
import { listAssignedOrders, listOrders, type RestaurantOrder } from './orders-api'
import { orderCode as shortCode } from './orders-logic'
import { getMyRestaurant, type Restaurant } from './restaurant-api'
import { fetchSalesHistory } from './sales-api'
import { rangeFor, summarize, type SalesSummary } from './sales-logic'
import { RestaurantLayout } from './RestaurantLayout'
import './restaurant.css'
import './restaurant-dashboard.css'

interface Summary {
  restaurant: Restaurant
  pending: RestaurantOrder[]
  cooking: number
  ready: number
  products: number
  today: SalesSummary
}

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; data: Summary }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

const DAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']
const REFRESH_MS = 20_000

function greeting(): string {
  const hour = new Date().getHours()
  return hour < 12 ? 'Buenos días' : hour < 19 ? 'Buenas tardes' : 'Buenas noches'
}

function todayHours(restaurant: Restaurant): string {
  const today = restaurant.schedule.find((entry) => entry.day === DAYS[new Date().getDay()])
  return today?.opensAt && today.closesAt ? `${today.opensAt.slice(0, 5)} – ${today.closesAt.slice(0, 5)}` : 'Cerrado hoy'
}

function Stat({ icon, label, value, hint, tone, href }: { icon: AppIconName; label: string; value: number | string; hint: string; tone?: 'alert'; href?: string }) {
  const body = (
    <>
      <span className="rd-stat-icon"><AppIcon name={icon} /></span>
      <span className="rd-stat-label">{label}</span>
      <strong>{value}</strong>
      <small>{hint}</small>
    </>
  )
  const className = tone === 'alert' ? 'rd-stat rd-stat-alert' : 'rd-stat'
  return href ? <Link to={href} className={className + ' rd-stat-link'}>{body}</Link> : <div className={className}>{body}</div>
}

/** Resumen del día del restaurante: cuánto hay por atender, cuántos platos tiene y su horario de hoy. */
export function RestaurantDashboard() {
  const navigate = useNavigate()
  const session = useSession()
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const token = session.token

  useEffect(() => {
    let active = true
    const load = async () => {
      try {
        const restaurant = await getMyRestaurant(token)
        if (restaurant.status !== 'APPROVED') { if (active) setState({ kind: 'ready', data: { restaurant, pending: [], cooking: 0, ready: 0, products: 0, today: summarize([]) } }); return }
        const day = rangeFor('today', new Date())
        const [pending, accepted, preparing, ready, assigned, products, sold] = await Promise.all([
          listOrders(token, 'PENDING'), listOrders(token, 'ACCEPTED'), listOrders(token, 'PREPARING'), listOrders(token, 'READY'), listAssignedOrders(token), listMyProducts(token, 0), fetchSalesHistory(token, day.from, day.to),
        ])
        // Un pedido con repartidor asignado sigue en el local hasta que lo recogen.
        if (active) setState({ kind: 'ready', data: { restaurant, pending, cooking: accepted.length + preparing.length, ready: ready.length + assigned.length, products: products.totalItems, today: summarize(sold) } })
      } catch (error) {
        if (!active) return
        if (error instanceof ApiError && error.status === 401) return
        if (error instanceof ApiError && error.status === 404) { navigate('/mi-restaurante/negocio', { replace: true }); return }
        if (error instanceof ApiError && error.status === 403) { setState({ kind: 'forbidden' }); return }
        // Un fallo de una actualización periódica no borra lo que ya se muestra.
        setState((current) => (current.kind === 'ready' ? current : { kind: 'error', message: error instanceof ApiError ? error.message : GENERIC_ERROR }))
      }
    }
    void load()
    const timer = setInterval(() => void load(), REFRESH_MS)
    return () => { active = false; clearInterval(timer) }
  }, [token, navigate])


  if (state.kind === 'forbidden') {
    return (
      <AuthLayout title="Mi restaurante">
        <p className="auth-alert" role="alert">Esta sección es solo para cuentas de restaurante habilitadas.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  const name = session.name?.split(' ')[0]
  return (
    <RestaurantLayout title={`${greeting()}${name ? `, ${name}` : ''}`} description="Así va tu restaurante hoy.">
      {state.kind === 'loading' && <p className="auth-description" role="status">Cargando tu panel…</p>}
      {state.kind === 'error' && <p className="auth-alert" role="alert">{state.message}</p>}
      {state.kind === 'ready' && (() => {
        const { restaurant, pending, cooking, ready, products, today } = state.data
        if (restaurant.status !== 'APPROVED') {
          return (
            <div className="rd-wait">
              <span className="rs-empty-icon"><AppIcon name="store" /></span>
              <strong>{restaurant.status === 'REJECTED' ? 'Tu solicitud fue rechazada' : 'Estamos revisando tu restaurante'}</strong>
              <p>
                {restaurant.status === 'REJECTED'
                  ? `Motivo: ${restaurant.rejectionReason}. Corrige tus datos y vuelve a enviarla.`
                  : 'Un administrador aprobará tu solicitud pronto. Mientras tanto puedes revisar tus datos.'}
              </p>
              <Link to="/mi-restaurante/negocio" className="auth-submit">{restaurant.status === 'REJECTED' ? 'Corregir mi solicitud' : 'Ver mis datos'}</Link>
            </div>
          )
        }
        return (
          <>
            <div className="rd-stats">
              <Stat icon="receipt" label="Pedidos nuevos" value={pending.length} hint={pending.length ? 'Esperan tu respuesta' : 'Todo al día'} tone={pending.length ? 'alert' : undefined} />
              <Stat icon="store" label="En cocina" value={cooking} hint="Aceptados y en preparación" />
              <Stat icon="scooter" label="Por recoger" value={ready} hint="Listos, esperando al repartidor" />
              <Stat icon="chart" label="Ventas de hoy" value={formatPrice(today.sales)} hint={`${today.orders} ${today.orders === 1 ? 'pedido' : 'pedidos'} · ver detalle`} href="/mi-restaurante/historial" />
            </div>

            <div className="rd-grid">
              <section className="rs-card" aria-labelledby="rd-new">
                <div className="rd-card-head">
                  <h2 id="rd-new">Pedidos por atender</h2>
                  <Link to="/mi-restaurante/pedidos" className="rd-more">Ver todos<AppIcon name="arrow" /></Link>
                </div>
                {pending.length === 0 ? (
                  <div className="rd-quiet">
                    <span className="rs-empty-icon"><AppIcon name="check" /></span>
                    <strong>No tienes pedidos nuevos</strong>
                    <p>Cuando llegue uno, lo verás aquí y en el número naranja del menú.</p>
                  </div>
                ) : (
                  <ul className="rd-list">
                    {pending.slice(0, 4).map((order) => (
                      <li key={order.id}>
                        <Link to="/mi-restaurante/pedidos">
                          <strong>{shortCode(order.id)}</strong>
                          <span>{order.lines.map((line) => `${line.quantity}× ${line.name}`).join(', ')}</span>
                          <em>{formatPrice(order.totals.subtotal)}</em>
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              <div className="rd-side">
                <section className="rs-card" aria-labelledby="rd-today">
                  <h2 id="rd-today">Tu horario de hoy</h2>
                  <p className="rd-hours">{todayHours(restaurant)}</p>
                  <p className="rd-note">
                    {restaurant.acceptingOrders ? 'Los clientes te ven abierto y pueden pedirte.' : restaurant.available ? 'Estás fuera de tu horario: por ahora no recibes pedidos.' : 'Estás en pausa: no recibes pedidos.'}
                  </p>
                  <Link to="/mi-restaurante/negocio" className="rd-more">Cambiar horario<AppIcon name="arrow" /></Link>
                </section>

                {products === 0 && (
                  <section className="rd-tip">
                    <strong>Agrega tu primer plato</strong>
                    <p>Sin productos los clientes no pueden pedirte. Sube una foto y el precio.</p>
                    <Link to="/mi-restaurante/productos" className="auth-submit">Ir a mi carta</Link>
                  </section>
                )}
              </div>
            </div>
          </>
        )
      })()}
    </RestaurantLayout>
  )
}
