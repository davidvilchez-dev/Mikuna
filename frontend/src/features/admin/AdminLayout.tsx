import { useEffect, useState, type ReactNode } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import { fetchMe, type AccountResponse } from '../auth/auth-api'
import { clearSession, getSession } from '../auth/session-storage'
import logo from '../../assets/branding/logo-mikuna.svg'
import logoLight from '../../assets/branding/logo-mikuna-light.svg'
import { Icon, type IconName } from './admin-ui'
import './admin.css'

const NAV_GROUPS: readonly { label: string; items: readonly { to: string; label: string; icon: IconName; end?: boolean }[] }[] = [
  { label: 'General', items: [{ to: '/admin', label: 'Panel', icon: 'home', end: true }] },
  {
    label: 'Gestión',
    items: [
      { to: '/admin/solicitudes', label: 'Solicitudes', icon: 'inbox' },
      { to: '/admin/cuentas', label: 'Cuentas', icon: 'users' },
    ],
  },
  {
    label: 'Operación',
    items: [
      { to: '/admin/pedidos', label: 'Pedidos y entregas', icon: 'receipt' },
      { to: '/admin/entregas', label: 'Entregas pendientes', icon: 'truck' },
    ],
  },
]

function initials(name?: string): string {
  const parts = (name ?? 'Administrador').trim().split(/\s+/).filter(Boolean)
  return ((parts[0]?.[0] ?? 'A') + (parts[1]?.[0] ?? '')).toUpperCase()
}

interface AdminLayoutProps {
  title: string
  description?: string
  /** Acciones a la derecha del título. */
  actions?: ReactNode
  children: ReactNode
}

/** Marco del panel administrativo: barra lateral fija en escritorio y menú deslizable en móvil. */
export function AdminLayout({ title, description, actions, children }: AdminLayoutProps) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [me, setMe] = useState<AccountResponse | null>(null)
  const token = getSession()?.token

  useEffect(() => {
    if (!token) return
    let active = true
    fetchMe(token).then((value) => { if (active) setMe(value) }).catch(() => { /* el nombre es opcional */ })
    return () => { active = false }
  }, [token])

  useEffect(() => {
    if (!open) return
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpen(false) }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [open])

  const logout = () => {
    clearSession()
    navigate('/', { replace: true })
  }

  return (
    <div className="adm" data-open={open}>
      <aside className="adm-side" id="adm-side" aria-label="Administración">
        <Link to="/admin" className="adm-brand" aria-label="Mikuna, panel administrativo">
          <img src={logoLight} alt="Mikuna" width={3922} height={815} />
          <span className="adm-brand-tag">Admin</span>
        </Link>
        <nav className="adm-nav" aria-label="Secciones">
          {NAV_GROUPS.map((group) => (
            <div key={group.label} className="adm-nav-group">
              <span className="adm-nav-label">{group.label}</span>
              {group.items.map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end ?? false} onClick={() => setOpen(false)}>
                  <Icon name={item.icon} />
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="adm-side-foot">
          <div className="adm-user">
            <span className="adm-avatar" aria-hidden="true">{initials(me?.name)}</span>
            <span className="adm-user-text">
              <strong>{me?.name ?? 'Administrador'}</strong>
              {me && <span>{me.email}</span>}
            </span>
          </div>
          <button type="button" className="adm-logout" onClick={logout}>
            <Icon name="logout" />
            Cerrar sesión
          </button>
        </div>
      </aside>

      {open && <button type="button" className="adm-scrim" aria-label="Cerrar menú" onClick={() => setOpen(false)} />}

      <div className="adm-main">
        <div className="adm-topbar">
          <button type="button" className="adm-menu-btn" aria-label="Abrir menú" aria-expanded={open} aria-controls="adm-side" onClick={() => setOpen(true)}>
            <Icon name="menu" />
          </button>
          <img src={logo} alt="Mikuna" width={3922} height={815} />
        </div>
        <main className="adm-content">
          <header className="adm-head">
            <div>
              <h1>{title}</h1>
              {description && <p>{description}</p>}
            </div>
            {actions && <div className="adm-head-actions">{actions}</div>}
          </header>
          {children}
        </main>
      </div>
    </div>
  )
}
