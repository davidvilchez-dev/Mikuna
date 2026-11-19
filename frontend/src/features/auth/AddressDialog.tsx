import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { AddressForm } from '../cart/AddressForm'
import { createAddress, listAddresses, selectAddress, type Address, type AddressData } from '../cart/cart-api'
import { ApiError, GENERIC_ERROR } from './auth-api'
import { AppIcon } from './AppIcon'
import './address-dialog.css'

interface AddressDialogProps {
  token: string
  /** Dirección que hoy usa el carrito para la entrega. */
  selectedId: string | null
  onClose: () => void
}

type ListState =
  | { kind: 'loading' }
  | { kind: 'ready'; addresses: Address[] }
  | { kind: 'error'; message: string }

const emptyData: AddressData = { label: '', street: '', district: '', reference: '', contactPhone: '', latitude: null, longitude: null }

/** Panel para elegir la dirección de entrega o agregar una nueva sin salir de la pantalla. */
export function AddressDialog({ token, selectedId, onClose }: AddressDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [step, setStep] = useState<'list' | 'new'>('list')
  const [state, setState] = useState<ListState>({ kind: 'loading' })
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  useEffect(() => {
    let active = true
    listAddresses(token)
      .then((addresses) => { if (active) setState({ kind: 'ready', addresses }) })
      .catch((error: unknown) => {
        if (active) setState({ kind: 'error', message: error instanceof ApiError ? error.message : GENERIC_ERROR })
      })
    return () => { active = false }
  }, [token])

  async function choose(address: Address) {
    if (busy) return
    if (address.id === selectedId) { onClose(); return }
    setBusy(true)
    setNotice(null)
    try {
      await selectAddress(token, address.id)
      onClose()
    } catch (error) {
      setNotice(error instanceof ApiError ? error.message : GENERIC_ERROR)
      setBusy(false)
    }
  }

  async function create(data: AddressData) {
    const created = await createAddress(token, data)
    try {
      await selectAddress(token, created.id)
    } catch {
      // La dirección quedó guardada; si no se pudo elegir, se podrá hacer desde el listado.
    }
    onClose()
  }

  return (
    <dialog
      ref={dialogRef}
      className="addr-dialog"
      aria-labelledby="addr-dialog-title"
      onClose={onClose}
      onClick={(event) => { if (event.target === dialogRef.current) dialogRef.current?.close() }}
    >
      <div className="addr-dialog-inner">
        <header className="addr-dialog-head">
          {step === 'new' ? (
            <button type="button" className="addr-dialog-icon" onClick={() => setStep('list')} aria-label="Volver a mis direcciones">
              <AppIcon name="back" />
            </button>
          ) : <span />}
          <button type="button" className="addr-dialog-icon" onClick={() => dialogRef.current?.close()} aria-label="Cerrar">
            <svg className="app-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
          </button>
        </header>

        <h2 id="addr-dialog-title">{step === 'new' ? 'Agrega tu dirección' : '¿Dónde quieres recibir tu pedido?'}</h2>

        {step === 'list' && (
          <>
            {notice && <p className="auth-alert" role="alert">{notice}</p>}
            {state.kind === 'loading' && <p className="auth-description" role="status">Cargando tus direcciones…</p>}
            {state.kind === 'error' && <p className="auth-alert" role="alert">{state.message}</p>}
            {state.kind === 'ready' && (
              <ul className="addr-options">
                {state.addresses.map((address) => (
                  <li key={address.id}>
                    <button type="button" className={address.id === selectedId ? 'addr-option addr-option-active' : 'addr-option'}
                      disabled={busy} onClick={() => void choose(address)}>
                      <span className="addr-option-icon"><AppIcon name="pin" /></span>
                      <span className="addr-option-text">
                        <strong>{address.label ?? address.street}</strong>
                        <small>{address.label ? address.street + ' · ' : ''}{address.district}</small>
                      </span>
                      {address.id === selectedId && <AppIcon name="check" className="app-icon addr-option-check" />}
                    </button>
                  </li>
                ))}
                {state.addresses.length === 0 && <li className="addr-empty">Aún no tienes direcciones guardadas.</li>}
              </ul>
            )}
            <button type="button" className="addr-dialog-new" onClick={() => setStep('new')}>
              <span aria-hidden="true">+</span> Agregar nueva dirección
            </button>
            <Link to="/mis-direcciones" className="addr-dialog-manage" onClick={() => dialogRef.current?.close()}>Administrar mis direcciones</Link>
          </>
        )}

        {step === 'new' && (
          <AddressForm initial={emptyData} submitLabel="Guardar y continuar" onSubmit={create} onCancel={() => setStep('list')} />
        )}
      </div>
    </dialog>
  )
}
