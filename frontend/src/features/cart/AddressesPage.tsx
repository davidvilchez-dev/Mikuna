import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, errorMessage } from '../auth/auth-api'
import { AppIcon } from '../auth/AppIcon'
import { notifyError, notifySuccess } from '../ui/notify'
import { useConfirm } from '../ui/useConfirm'
import { AuthLayout } from '../auth/AuthLayout'
import { useSession } from '../auth/RequireSession'
import { AddressForm } from './AddressForm'
import { createAddress, deleteAddress, listAddresses, updateAddress, type Address, type AddressData } from './cart-api'
import '../restaurant/restaurant.css'
import './cart.css'
import './address.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; addresses: Address[] }
  | { kind: 'forbidden'; message: string }
  | { kind: 'error'; message: string }

type Mode = { kind: 'list' } | { kind: 'create' } | { kind: 'edit'; address: Address }

const emptyData: AddressData = { label: '', street: '', district: '', reference: '', contactPhone: '', latitude: null, longitude: null }

function toData(address: Address): AddressData {
  return {
    label: address.label ?? '',
    street: address.street,
    district: address.district,
    reference: address.reference ?? '',
    contactPhone: address.contactPhone ?? '',
    latitude: address.latitude,
    longitude: address.longitude,
  }
}

/** Libreta de direcciones de entrega del cliente (RF-12). */
export function AddressesPage() {
  const session = useSession()
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [mode, setMode] = useState<Mode>({ kind: 'list' })
  const [attempt, setAttempt] = useState(0)
  const [busy, setBusy] = useState(false)
  const [confirmDialog, confirm] = useConfirm()

  const token = session.token

  useEffect(() => {
    let active = true
    listAddresses(token)
      .then((addresses) => { if (active) setState({ kind: 'ready', addresses }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setState({ kind: 'forbidden', message: 'Las direcciones de entrega son solo para cuentas de cliente.' })
        else setState({ kind: 'error', message: errorMessage(error) })
      })
    return () => { active = false }
  }, [token, attempt])


  const reload = (message: string | null) => {
    if (message) notifySuccess(message)
    setMode({ kind: 'list' })
    setState({ kind: 'loading' })
    setAttempt((n) => n + 1)
  }

  async function remove(address: Address) {
    if (busy) return
    const ok = await confirm({
      title: '¿Eliminar esta dirección?',
      message: `${address.label ? address.label + ' · ' : ''}${address.street}, ${address.district}. Tus pedidos anteriores no cambian.`,
      confirmLabel: 'Eliminar dirección',
      danger: true,
    })
    if (!ok) return
    setBusy(true)
    try {
      await deleteAddress(token, address.id)
      reload('Dirección eliminada')
    } catch (error) {
      notifyError(errorMessage(error))
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
      <AuthLayout title="Nueva dirección" description="Marca el punto en el mapa y cuéntanos cómo llegar.">
        <AddressForm initial={emptyData} submitLabel="Guardar dirección"
          onSubmit={async (data) => { await createAddress(token, data); reload('Dirección guardada') }}
          onCancel={() => setMode({ kind: 'list' })} />
      </AuthLayout>
    )
  }
  if (mode.kind === 'edit') {
    const address = mode.address
    return (
      <AuthLayout title="Editar dirección">
        <AddressForm initial={toData(address)} submitLabel="Guardar cambios"
          onSubmit={async (data) => { await updateAddress(token, address.id, data); reload('Dirección actualizada') }}
          onCancel={() => setMode({ kind: 'list' })} />
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Mis direcciones" description="Guarda tus direcciones y elige a dónde llegan tus pedidos." wide>
      <ul className="addr-grid">
        {state.addresses.map((address) => (
          <li key={address.id}>
            <article className="addr-card">
              <div className="addr-card-head">
                <span className="addr-card-icon"><AppIcon name="pin" /></span>
                <div className="addr-card-body">
                  <h2>{address.label ?? 'Dirección'}</h2>
                  <p className="addr-street">{address.street}</p>
                  <p>{address.district}</p>
                  {address.reference && <p>Referencia: {address.reference}</p>}
                  {address.contactPhone && <p>Teléfono: {address.contactPhone}</p>}
                  {address.latitude !== null && <p className="addr-located"><AppIcon name="check" /> Ubicación marcada en el mapa</p>}
                </div>
              </div>
              <div className="addr-card-actions">
                <button type="button" className="auth-submit auth-submit-secondary" onClick={() => setMode({ kind: 'edit', address })} disabled={busy}>Editar</button>
                <button type="button" className="auth-submit auth-submit-secondary" onClick={() => remove(address)} disabled={busy}>Eliminar</button>
              </div>
            </article>
          </li>
        ))}
        <li>
          <button type="button" className="addr-add" onClick={() => setMode({ kind: 'create' })}>
            <span className="addr-add-plus" aria-hidden="true">+</span>
            Agregar nueva dirección
          </button>
        </li>
      </ul>
      {confirmDialog}
    </AuthLayout>
  )
}
