import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import { AddressForm } from './AddressForm'
import { createAddress, deleteAddress, listAddresses, updateAddress, type Address, type AddressData } from './cart-api'
import '../restaurant/restaurant.css'
import './cart.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; addresses: Address[] }
  | { kind: 'forbidden'; message: string }
  | { kind: 'error'; message: string }

type Mode = { kind: 'list' } | { kind: 'create' } | { kind: 'edit'; address: Address }

const emptyData: AddressData = { label: '', street: '', district: '', reference: '', contactPhone: '' }

function toData(address: Address): AddressData {
  return {
    label: address.label ?? '',
    street: address.street,
    district: address.district,
    reference: address.reference ?? '',
    contactPhone: address.contactPhone ?? '',
  }
}

/** Libreta de direcciones de entrega del cliente (RF-12). */
export function AddressesPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [mode, setMode] = useState<Mode>({ kind: 'list' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const token = session?.token ?? ''

  const handleFailure = useCallback((error: unknown): string => {
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
    listAddresses(token)
      .then((addresses) => { if (active) setState({ kind: 'ready', addresses }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setState({ kind: 'forbidden', message: 'Las direcciones de entrega son solo para cuentas de cliente.' })
        else setState({ kind: 'error', message: handleFailure(error) })
      })
    return () => { active = false }
  }, [token, attempt, handleFailure])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  const reload = (message: string | null) => {
    setNotice(message)
    setMode({ kind: 'list' })
    setState({ kind: 'loading' })
    setAttempt((n) => n + 1)
  }

  async function remove(address: Address) {
    if (busy || !window.confirm('¿Eliminar esta dirección?')) return
    setBusy(true)
    try {
      await deleteAddress(token, address.id)
      reload('Dirección eliminada')
    } catch (error) {
      setNotice(handleFailure(error))
    } finally {
      setBusy(false)
    }
  }

  if (state.kind === 'loading') {
    return <AuthLayout title="Mis direcciones" wide><p className="auth-description" role="status">Cargando tus direcciones…</p></AuthLayout>
  }
  if (state.kind === 'forbidden') {
    return <AuthLayout title="Mis direcciones" wide><p className="auth-notice" role="status">{state.message}</p><Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link></AuthLayout>
  }
  if (state.kind === 'error') {
    return (
      <AuthLayout title="Mis direcciones" wide>
        <p className="auth-alert" role="alert">{state.message}</p>
        <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
      </AuthLayout>
    )
  }

  if (mode.kind === 'create') {
    return (
      <AuthLayout title="Nueva dirección" wide>
        <AddressForm initial={emptyData} submitLabel="Guardar dirección"
          onSubmit={async (data) => { await createAddress(token, data); reload('Dirección guardada') }}
          onCancel={() => setMode({ kind: 'list' })} />
      </AuthLayout>
    )
  }
  if (mode.kind === 'edit') {
    const address = mode.address
    return (
      <AuthLayout title="Editar dirección" wide>
        <AddressForm initial={toData(address)} submitLabel="Guardar cambios"
          onSubmit={async (data) => { await updateAddress(token, address.id, data); reload('Dirección actualizada') }}
          onCancel={() => setMode({ kind: 'list' })} />
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Mis direcciones" description="Elige la dirección de entrega de tu pedido desde el carrito." wide>
      {notice && <p className="auth-notice" role="status">{notice}</p>}
      {state.addresses.length === 0 ? (
        <p className="auth-notice" role="status">Aún no registraste direcciones.</p>
      ) : (
        <ul className="cart-list">
          {state.addresses.map((address) => (
            <li className="cart-address" key={address.id}>
              <div>
                <h2>{address.label ?? address.street}</h2>
                <p>{address.street} · {address.district}</p>
                {address.reference && <p>Referencia: {address.reference}</p>}
                {address.contactPhone && <p>Teléfono: {address.contactPhone}</p>}
              </div>
              <div className="restaurant-actions">
                <button type="button" className="auth-submit auth-submit-secondary" onClick={() => setMode({ kind: 'edit', address })} disabled={busy}>Editar</button>
                <button type="button" className="auth-submit auth-submit-secondary" onClick={() => remove(address)} disabled={busy}>Eliminar</button>
              </div>
            </li>
          ))}
        </ul>
      )}
      <div className="restaurant-actions">
        <button type="button" className="auth-submit" onClick={() => setMode({ kind: 'create' })}>Agregar dirección</button>
        <Link to="/carrito" className="auth-submit auth-submit-secondary">Ir al carrito</Link>
      </div>
    </AuthLayout>
  )
}
