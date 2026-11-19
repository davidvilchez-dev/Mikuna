import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR, fetchMe, type AccountResponse, type Role } from './auth-api'
import { roleLabels, statusLabels } from './auth-labels'
import { AppIcon, type AppIconName } from './AppIcon'
import { AuthLayout } from './AuthLayout'
import { clearSession, getSession, saveSession } from './session-storage'
import './account.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; account: AccountResponse }
  | { kind: 'expired' }
  | { kind: 'error'; message: string }

interface QuickAction { to: string; title: string; text: string; icon: AppIconName; primary?: boolean }

const COMMON_ACTIONS: QuickAction[] = [
  { to: '/historial', title: 'Historial', text: 'Revisa pedidos anteriores y califícalos.', icon: 'receipt' },
  { to: '/avisos', title: 'Avisos', text: 'Novedades sobre tus pedidos.', icon: 'bell' },
]

const ACTIONS_BY_ROLE: Partial<Record<Role, QuickAction[]>> = {
  CUSTOMER: [
    { to: '/restaurantes', title: 'Pedir ahora', text: 'Explora restaurantes de tu ciudad y arma tu pedido.', icon: 'store', primary: true },
    { to: '/carrito', title: 'Mi carrito', text: 'Revisa lo que has agregado y confirma.', icon: 'cart' },
    { to: '/mis-direcciones', title: 'Mis direcciones', text: 'Elige dónde recibir tus pedidos.', icon: 'pin' },
    { to: '/historial', title: 'Mis pedidos', text: 'Sigue un pedido en curso o repite uno anterior.', icon: 'receipt' },
    { to: '/avisos', title: 'Avisos', text: 'Novedades sobre tus pedidos.', icon: 'bell' },
  ],
  RESTAURANT: [
    { to: '/mi-restaurante', title: 'Mi restaurante', text: 'Datos, horario y estado de tu habilitación.', icon: 'store', primary: true },
    { to: '/mi-restaurante/productos', title: 'Productos', text: 'Administra tu carta y su disponibilidad.', icon: 'box' },
    { to: '/mi-restaurante/pedidos', title: 'Pedidos', text: 'Acepta, prepara y entrega los pedidos.', icon: 'receipt' },
    ...COMMON_ACTIONS,
  ],
  COURIER: [
    { to: '/repartidor/entregas', title: 'Mis entregas', text: 'Disponibilidad y entregas asignadas.', icon: 'scooter', primary: true },
    ...COMMON_ACTIONS,
  ],
}

function firstName(name: string): string {
  return name.trim().split(/\s+/)[0] || name
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() || '·'
}

const greetings: Partial<Record<Role, string>> = {
  CUSTOMER: '¿Qué te provoca pedir hoy?',
  RESTAURANT: 'Esto es lo que puedes hacer con tu restaurante.',
  COURIER: 'Tus entregas te esperan.',
}

/** Sesión propia: acredita el acceso y reúne los accesos de cada tipo de cuenta; no es el perfil editable de RF-03. */
export function AccountPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!session) return
    let active = true
    fetchMe(session.token)
      .then((account) => {
        if (!active) return
        // Las sesiones guardadas antes de recordar el rol lo recuperan aquí para adaptar el menú.
        if (session.role !== account.role || session.name !== account.name) saveSession({ ...session, role: account.role, name: account.name })
        setState({ kind: 'ready', account })
      })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 401) {
          clearSession()
          setState({ kind: 'expired' })
        } else if (error instanceof ApiError && error.status === 403) {
          clearSession()
          setState({ kind: 'error', message: 'La cuenta no está habilitada' })
        } else {
          setState({ kind: 'error', message: GENERIC_ERROR })
        }
      })
    return () => { active = false }
  }, [session, attempt])

  if (!session || state.kind === 'expired') return <Navigate to="/iniciar-sesion" replace />
  // El administrador trabaja desde su panel, no desde la tarjeta de cuenta.
  if (state.kind === 'ready' && state.account.role === 'ADMIN') return <Navigate to="/admin" replace />

  const logout = () => {
    clearSession()
    navigate('/', { replace: true })
  }

  if (state.kind === 'loading') {
    return <AuthLayout title="Mi cuenta"><p className="auth-description" role="status">Cargando tu sesión…</p></AuthLayout>
  }

  if (state.kind === 'error') {
    const canRetry = state.message === GENERIC_ERROR
    return (
      <AuthLayout title="Mi cuenta">
        <p className="auth-alert" role="alert">{state.message}</p>
        <div className="account-error-actions">
          {canRetry && <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>}
          <button type="button" className="auth-submit auth-submit-secondary" onClick={logout}>Volver al inicio</button>
        </div>
      </AuthLayout>
    )
  }

  const { account } = state
  const actions = ACTIONS_BY_ROLE[account.role] ?? []
  return (
    <AuthLayout title={`Hola, ${firstName(account.name)}`} description={greetings[account.role] ?? 'Tu sesión está activa.'} wide>
      <div className="account-grid">
        <ul className="account-actions">
          {actions.map((action) => (
            <li key={action.to + action.title} className={action.primary ? 'account-action-primary' : undefined}>
              <Link to={action.to} className="account-action">
                <span className="account-action-icon"><AppIcon name={action.icon} /></span>
                <span className="account-action-text">
                  <strong>{action.title}</strong>
                  <small>{action.text}</small>
                </span>
                <AppIcon name="arrow" className="app-icon account-action-arrow" />
              </Link>
            </li>
          ))}
        </ul>

        <aside className="account-card" aria-label="Datos de tu cuenta">
          <div className="account-id">
            <span className="account-avatar" aria-hidden="true">{initials(account.name)}</span>
            <div>
              <strong>{account.name}</strong>
              <span>{roleLabels[account.role]}</span>
            </div>
          </div>
          <dl className="account-details">
            <div><dt>Correo</dt><dd>{account.email}</dd></div>
            <div>
              <dt>Estado</dt>
              <dd><span className={account.status === 'ENABLED' ? 'account-status account-status-on' : 'account-status account-status-off'}>{statusLabels[account.status]}</span></dd>
            </div>
          </dl>
          <button type="button" className="account-logout" onClick={logout}>
            <AppIcon name="logout" />
            Cerrar sesión
          </button>
        </aside>
      </div>
    </AuthLayout>
  )
}
