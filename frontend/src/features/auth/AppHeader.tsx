import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from 'react-router'
import { CART_CHANGED, getCart } from '../cart/cart-api'
import { homeAssets } from '../home/home-assets'
import { FoodImage } from '../home/components/FoodImage'
import { badgeText } from '../notifications/notifications-logic'
import { useUnreadCount } from '../notifications/useUnreadCount'
import { AddressDialog } from './AddressDialog'
import { AppIcon, type AppIconName } from './AppIcon'
import { clearSession, getSession } from './session-storage'
import './app-header.css'

interface NavItem { to: string; label: string; icon: AppIconName }

/** Menú principal de las cuentas que trabajan con pedidos; el cliente navega con buscador, carrito y menú de usuario. */
const NAV_BY_ROLE: Record<string, NavItem[]> = {
  RESTAURANT: [
    { to: '/mi-restaurante', label: 'Mi restaurante', icon: 'store' },
    { to: '/mi-restaurante/pedidos', label: 'Pedidos', icon: 'receipt' },
    { to: '/mi-restaurante/productos', label: 'Mi carta', icon: 'box' },
  ],
  COURIER: [
    { to: '/repartidor/entregas', label: 'Entregas', icon: 'scooter' },
  ],
}

const MENU_BY_ROLE: Record<string, NavItem[]> = {
  CUSTOMER: [
    { to: '/restaurantes', label: 'Inicio', icon: 'home' },
    { to: '/mis-direcciones', label: 'Mis direcciones', icon: 'pin' },
    { to: '/historial', label: 'Mis pedidos', icon: 'receipt' },
    { to: '/avisos', label: 'Avisos', icon: 'bell' },
    { to: '/mi-cuenta', label: 'Mi perfil', icon: 'user' },
  ],
  RESTAURANT: [
    { to: '/mi-restaurante/historial', label: 'Ventas', icon: 'chart' },
    { to: '/avisos', label: 'Avisos', icon: 'bell' },
    { to: '/mi-cuenta', label: 'Mi perfil', icon: 'user' },
  ],
  COURIER: [
    { to: '/historial', label: 'Historial', icon: 'receipt' },
    { to: '/avisos', label: 'Avisos', icon: 'bell' },
    { to: '/mi-cuenta', label: 'Mi perfil', icon: 'user' },
  ],
}

const FALLBACK_MENU: NavItem[] = [{ to: '/mi-cuenta', label: 'Mi perfil', icon: 'user' }]

interface CustomerBar { count: number; address: { id: string; street: string; district: string } | null }  // el carrito trae también latitude/longitude

/** Cantidad de unidades en el carrito y dirección de entrega elegida; se renueva cuando cambian el carrito o las direcciones. */
function useCustomerBar(token: string | null): CustomerBar {
  const [bar, setBar] = useState<CustomerBar>({ count: 0, address: null })

  useEffect(() => {
    if (!token) return
    let active = true
    const load = () => {
      getCart(token)
        .then((cart) => {
          if (active) setBar({ count: cart.lines.reduce((sum, line) => sum + line.quantity, 0), address: cart.address })
        })
        .catch(() => { /* el encabezado funciona sin el contador */ })
    }
    load()
    window.addEventListener(CART_CHANGED, load)
    return () => { active = false; window.removeEventListener(CART_CHANGED, load) }
  }, [token])

  return bar
}

function firstName(name: string | undefined): string {
  return name?.trim().split(/\s+/)[0] ?? ''
}

function initials(name: string | undefined): string {
  const parts = name?.trim().split(/\s+/).filter(Boolean) ?? []
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase()
}

function UserMenu({ name, items, onLogout }: { name: string | undefined; items: NavItem[]; onLogout: () => void }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointer = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onPointer); document.removeEventListener('keydown', onKey) }
  }, [open])

  const initial = initials(name)
  return (
    <div className="app-user" ref={rootRef}>
      <button type="button" className="app-user-button" aria-expanded={open} aria-haspopup="true" onClick={() => setOpen((value) => !value)}>
        <span className="app-avatar" aria-hidden="true">{initial || <AppIcon name="user" />}</span>
        <span className="app-user-name">{firstName(name) || 'Mi cuenta'}</span>
        <AppIcon name="chevron" className="app-icon app-user-chevron" />
      </button>
      {open && (
        <ul className="app-menu">
          {items.map((item) => (
            <li key={item.to}>
              <Link to={item.to} className="app-menu-item" onClick={() => setOpen(false)}>
                <AppIcon name={item.icon} />
                {item.label}
              </Link>
            </li>
          ))}
          <li className="app-menu-sep" role="separator" />
          <li>
            <button type="button" className="app-menu-item app-menu-logout" onClick={() => { setOpen(false); onLogout() }}>
              <AppIcon name="logout" />
              Cerrar sesión
            </button>
          </li>
        </ul>
      )}
    </div>
  )
}

/** Encabezado de las pantallas con sesión, al estilo de las apps de delivery: dirección, búsqueda, carrito y cuenta. */
export function AppHeader() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [session] = useState(() => getSession())
  const role = session?.role
  const isCustomer = role === 'CUSTOMER'
  const unread = useUnreadCount(session?.token ?? null)
  const bar = useCustomerBar(isCustomer ? session?.token ?? null : null)
  const [addressOpen, setAddressOpen] = useState(false)
  const [query, setQuery] = useState(() => (location.pathname === '/restaurantes' ? searchParams.get('q') ?? '' : ''))
  const badge = unread === null ? '' : badgeText(unread)
  const items = role ? NAV_BY_ROLE[role] ?? [] : []

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = query.trim()
    navigate(text ? '/restaurantes?q=' + encodeURIComponent(text) : '/restaurantes')
  }

  function logout() {
    clearSession()
    navigate('/', { replace: true })
  }

  const home = isCustomer ? '/restaurantes' : role === 'RESTAURANT' ? '/mi-restaurante' : role === 'COURIER' ? '/repartidor/entregas' : session ? '/mi-cuenta' : '/'
  return (
    <header className="app-header">
      <div className={isCustomer ? 'app-header-inner app-header-customer' : 'app-header-inner'}>
        <Link to={home} className="app-brand" aria-label="Mikuna, ir al inicio">
          <FoodImage asset={homeAssets.logo} className="app-logo" priority />
        </Link>

        {isCustomer && session && (
          <>
            <button type="button" className="app-address" onClick={() => setAddressOpen(true)} aria-haspopup="dialog">
              <AppIcon name="pin" />
              <span className="app-address-text">
                <small>Enviar a</small>
                <strong>{bar.address ? bar.address.street : 'Elige tu dirección'}</strong>
              </span>
              <AppIcon name="chevron" className="app-icon app-address-chevron" />
            </button>
            <form className="app-search" role="search" onSubmit={search}>
              <AppIcon name="search" className="app-icon app-search-icon" />
              <input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Busca restaurantes o platos" aria-label="Buscar restaurantes" maxLength={80} />
            </form>
          </>
        )}

        {!isCustomer && items.length > 0 && (
          <nav className="app-nav" aria-label="Principal">
            {items.map((item) => (
              <NavLink key={item.to} to={item.to} end className="app-nav-link">
                <AppIcon name={item.icon} />
                <span>{item.label}</span>
              </NavLink>
            ))}
          </nav>
        )}

        <div className="app-actions">
          {isCustomer && (
            <NavLink to="/carrito" className="app-icon-button" aria-label={bar.count > 0 ? `Mi carrito, ${bar.count} productos` : 'Mi carrito'}>
              <AppIcon name="cart" />
              {bar.count > 0 && <span className="app-badge app-badge-cart" aria-hidden="true">{bar.count > 99 ? '99+' : bar.count}</span>}
            </NavLink>
          )}
          {unread !== null && (
            <NavLink to="/avisos" className="app-icon-button" aria-label={unread > 0 ? `Mis avisos, ${unread} sin leer` : 'Mis avisos'}>
              <AppIcon name="bell" />
              {badge && <span className="app-badge" aria-hidden="true">{badge}</span>}
            </NavLink>
          )}
          {session && <UserMenu name={session.name} items={role ? MENU_BY_ROLE[role] ?? FALLBACK_MENU : FALLBACK_MENU} onLogout={logout} />}
        </div>
      </div>

      {addressOpen && session && (
        <AddressDialog token={session.token} selectedId={bar.address?.id ?? null} onClose={() => setAddressOpen(false)} />
      )}
    </header>
  )
}
