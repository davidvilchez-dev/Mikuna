import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR, fetchMe, type AccountResponse } from './auth-api'
import { roleLabels, statusLabels } from './auth-labels'
import { AuthLayout } from './AuthLayout'
import { clearSession, getSession } from './session-storage'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; account: AccountResponse }
  | { kind: 'expired' }
  | { kind: 'error'; message: string }

/** Sesión propia: acredita el acceso; no es el perfil editable de RF-03 ni un panel de negocio. */
export function AccountPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (!session) return
    let active = true
    fetchMe(session.token)
      .then((account) => { if (active) setState({ kind: 'ready', account }) })
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
        <div className="auth-form">
          {canRetry && <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>}
          <button type="button" className="auth-submit auth-submit-secondary" onClick={logout}>Volver al inicio</button>
        </div>
      </AuthLayout>
    )
  }

  const { account } = state
  return (
    <AuthLayout title={`Hola, ${account.name}`} description="Tu sesión está activa.">
      <dl className="auth-details">
        <div><dt>Nombre</dt><dd>{account.name}</dd></div>
        <div><dt>Correo</dt><dd>{account.email}</dd></div>
        <div><dt>Tipo de cuenta</dt><dd>{roleLabels[account.role]}</dd></div>
        <div><dt>Estado</dt><dd>{statusLabels[account.status]}</dd></div>
      </dl>
      {account.role === 'CUSTOMER' && (
        <>
          <Link to="/carrito" className="auth-submit">Mi carrito</Link>
          <Link to="/mis-direcciones" className="auth-submit auth-submit-secondary">Mis direcciones</Link>
        </>
      )}
      {account.role === 'RESTAURANT' && (
        <Link to="/mi-restaurante" className="auth-submit">Mi restaurante</Link>
      )}
      {account.role === 'COURIER' && (
        <Link to="/repartidor/entregas" className="auth-submit">Mis entregas</Link>
      )}
      <button type="button" className="auth-submit auth-submit-secondary" onClick={logout}>Cerrar sesión</button>
    </AuthLayout>
  )
}
