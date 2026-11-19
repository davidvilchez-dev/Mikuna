import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import logo from '../../assets/branding/logo-mikuna.svg'
import { ApiError } from '../auth/auth-api'
import { AppIcon, type AppIconName } from '../auth/AppIcon'
import { clearSession, getSession } from '../auth/session-storage'
import { getMyRestaurant, setAvailability, type Restaurant } from './restaurant-api'
import { listOrders } from './orders-api'
import '../auth/auth.css'
import '../auth/app-shell.css'
import './restaurant.css'
import './restaurant-layout.css'

const NAV: readonly { to: string; label: string; icon: AppIconName; end?: boolean }[] = [
  { to: '/mi-restaurante', label: 'Resumen', icon: 'home', end: true },
  { to: '/mi-restaurante/pedidos', label: 'Pedidos', icon: 'receipt' },
  { to: '/mi-restaurante/productos', label: 'Mi carta', icon: 'box' },
  { to: '/mi-restaurante/historial', label: 'Ventas', icon: 'chart' },
  { to: '/mi-restaurante/negocio', label: 'Mi negocio', icon: 'store' },
]

const PENDING_POLL_MS = 20_000

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  return ((parts[0]?.[0] ?? 'M') + (parts[1]?.[0] ?? '')).toUpperCase()
}

interface RestaurantLayoutProps {
  title: string
  description?: string
  /** Acciones a la derecha del título. */
  actions?: ReactNode
  /** Se acepta por compatibilidad con las pantallas que ya lo enviaban; el panel siempre usa todo el ancho. */
  wide?: boolean
  children: ReactNode
}

/**
 * Marco del panel del restaurante: barra lateral clara en escritorio y barra de pestañas abajo en el móvil.
 * Muestra siempre si el local está abierto (y permite pausarlo) y cuántos pedidos nuevos esperan.
 */
export function RestaurantLayout({ title, description, actions, children }: RestaurantLayoutProps) {
  const navigate = useNavigate()
  const session = getSession()
  const token = session?.token ?? ''
  const [restaurant, setRestaurant] = useState<Restaurant | null>(null)
  const [pending, setPending] = useState(0)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!token) return
    let active = true
    getMyRestaurant(token).then((value) => { if (active) setRestaurant(value) }).catch(() => { /* la pantalla ya explica el error */ })
    return () => { active = false }
  }, [token])

  const approved = restaurant?.status === 'APPROVED'
  useEffect(() => {
    if (!token || !approved) return
    let active = true
    const load = () => {
      listOrders(token, 'PENDING').then((items) => { if (active) setPending(items.length) }).catch(() => { /* el contador es opcional */ })
    }
    load()
    const timer = setInterval(load, PENDING_POLL_MS)
    return () => { active = false; clearInterval(timer) }
  }, [token, approved])

  const toggle = useCallback(async () => {
    if (!restaurant || busy) return
    setBusy(true)
    try {
      setRestaurant(await setAvailability(token, !restaurant.available))
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) { clearSession(); navigate('/iniciar-sesion', { replace: true }) }
    } finally {
      setBusy(false)
    }
  }, [restaurant, busy, token, navigate])

  const logout = () => { clearSession(); navigate('/', { replace: true }) }
  const businessName = restaurant?.name ?? session?.name ?? 'Mi restaurante'
  const open = approved && restaurant.available

  return (
    <div className="rl app-shell">
      <aside className="rl-side" aria-label="Panel del restaurante">
        <Link to="/mi-restaurante" className="rl-brand" aria-label="Mikuna, panel del restaurante">
          <img src={logo} alt="Mikuna" width={3922} height={815} />
        </Link>
        <div className="rl-business">
          <span className="rl-avatar" aria-hidden="true">{initials(businessName)}</span>
          <div>
            <strong>{businessName}</strong>
            <small>Panel del restaurante</small>
          </div>
        </div>
        <nav className="rl-nav" aria-label="Secciones">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end ?? false}>
              <AppIcon name={item.icon} />
              <span>{item.label}</span>
              {item.to.endsWith('/pedidos') && pending > 0 && <em className="rl-badge" aria-label={`${pending} pedidos nuevos`}>{pending}</em>}
            </NavLink>
          ))}
        </nav>
        <div className="rl-side-foot">
          <button type="button" className="rl-logout" onClick={logout}><AppIcon name="logout" />Cerrar sesión</button>
        </div>
      </aside>

      <div className="rl-body">
        <header className="rl-top">
          <Link to="/mi-restaurante" className="rl-top-logo" aria-label="Mikuna"><img src={logo} alt="Mikuna" width={3922} height={815} /></Link>
          <div className="rl-top-right">
            {restaurant && (approved ? (
              <div className={open ? 'rl-open rl-open-on' : 'rl-open'}>
                <span className="rl-open-dot" aria-hidden="true" />
                <span className="rl-open-text">{open ? 'Abierto' : 'En pausa'}</span>
                <button type="button" role="switch" aria-checked={restaurant.available} aria-label="Recibir pedidos"
                  className="rs-switch rs-switch-sm" disabled={busy} onClick={() => void toggle()}>
                  <span className="rs-switch-knob" />
                </button>
              </div>
            ) : (
              <span className={restaurant.status === 'REJECTED' ? 'rs-chip rs-chip-bad' : 'rs-chip'}>
                {restaurant.status === 'REJECTED' ? 'Solicitud rechazada' : 'Pendiente de aprobación'}
              </span>
            ))}
            <Link to="/avisos" className="rl-icon-btn" aria-label="Avisos"><AppIcon name="bell" /></Link>
          </div>
        </header>

        <main className="rl-main">
          <div className="rl-title">
            <div>
              <h1>{title}</h1>
              {description && <p>{description}</p>}
            </div>
            {actions && <div className="rl-actions">{actions}</div>}
          </div>
          {children}
        </main>
      </div>

      <nav className="rl-tabs" aria-label="Secciones">
        {NAV.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end ?? false}>
            <span className="rl-tab-icon"><AppIcon name={item.icon} />{item.to.endsWith('/pedidos') && pending > 0 && <em className="rl-badge">{pending}</em>}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
