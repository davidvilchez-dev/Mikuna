import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, errorMessage } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { useSession } from '../auth/RequireSession'
import {
  approveRestaurant,
  listRestaurants,
  rejectRestaurant,
  type AdminRestaurant,
  type RestaurantStatus,
} from './restaurant-api'
import { REJECT_REASON_MAX, validateRejectReason } from './restaurant-validation'
import { AdminLayout } from '../admin/AdminLayout'
import { Badge, EmptyState, ErrorState, LoadingBlock } from '../admin/admin-ui'

const filters: { status: RestaurantStatus; label: string }[] = [
  { status: 'PENDING', label: 'Pendientes' },
  { status: 'APPROVED', label: 'Aprobados' },
  { status: 'REJECTED', label: 'Rechazados' },
]

const dateFormat = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' })

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; items: AdminRestaurant[] }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Revisión de solicitudes de restaurantes (RF-06): lista, aprueba y rechaza con motivo. */
export function AdminRequestsPage() {
  const session = useSession()
  const [status, setStatus] = useState<RestaurantStatus>('PENDING')
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | undefined>()
  const [busy, setBusy] = useState(false)

  const token = session.token

  useEffect(() => {
    let active = true
    listRestaurants(token, status)
      .then((items) => { if (active) setState({ kind: 'ready', items }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 403) setState({ kind: 'forbidden' })
        else setState({ kind: 'error', message: errorMessage(error) })
      })
    return () => { active = false }
  }, [token, status, attempt])


  const reload = () => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }

  async function resolve(item: AdminRestaurant, action: () => Promise<AdminRestaurant>, done: string) {
    if (busy) return
    setBusy(true)
    setNotice(null)
    try {
      await action()
      setNotice(`${item.name}: ${done}`)
      setRejectingId(null)
      setReason('')
      reload()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setNotice(`${item.name}: otra persona ya resolvió esta solicitud. Se actualizó la lista.`)
        reload()
      } else {
        setNotice(errorMessage(error))
      }
    } finally {
      setBusy(false)
    }
  }

  function confirmReject(item: AdminRestaurant) {
    const error = validateRejectReason(reason)
    setReasonError(error)
    if (error) return
    void resolve(item, () => rejectRestaurant(token, item.id, reason.trim()), 'solicitud rechazada')
  }

  if (state.kind === 'forbidden') {
    return (
      <AuthLayout title="Solicitudes de restaurantes">
        <p className="auth-alert" role="alert">Esta sección es solo para administradores habilitados.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  return (
    <AdminLayout title="Solicitudes de restaurantes" description="Revisa las solicitudes y decide cuáles pueden recibir pedidos.">
      <div className="adm-seg" role="group" aria-label="Filtrar por estado">
        {filters.map((filter) => (
          <button key={filter.status} type="button" aria-pressed={status === filter.status}
            onClick={() => { setStatus(filter.status); setState({ kind: 'loading' }); setRejectingId(null); setNotice(null) }}>
            {filter.label}
          </button>
        ))}
      </div>
      {notice && <p className="adm-note" role="status">{notice}</p>}

      {state.kind === 'loading' && <LoadingBlock label="Cargando solicitudes" />}
      {state.kind === 'error' && <ErrorState message={state.message} onRetry={reload} />}
      {state.kind === 'ready' && state.items.length === 0 && (
        <EmptyState title="Todo al día" text="No hay restaurantes en este estado." />
      )}
      {state.kind === 'ready' && state.items.length > 0 && (
        <ul className="adm-cards">
          {state.items.map((item) => (
            <li key={item.id}>
              <div className="adm-cards-head">
                <h2>{item.name}</h2>
                <Badge tone={item.status === 'APPROVED' ? 'ok' : item.status === 'REJECTED' ? 'bad' : 'warn'}>
                  {item.status === 'APPROVED' ? 'Aprobado' : item.status === 'REJECTED' ? 'Rechazado' : 'Pendiente'}
                </Badge>
              </div>
              <dl className="adm-facts">
                <div><dt>Categoría</dt><dd>{item.category}</dd></div>
                <div><dt>Zona</dt><dd>{item.zone}</dd></div>
                <div><dt>Dirección</dt><dd>{item.address}</dd></div>
                <div><dt>Teléfono</dt><dd>{item.phone}</dd></div>
                <div><dt>Titular</dt><dd>{item.ownerName} ({item.ownerEmail})</dd></div>
                <div><dt>Enviada</dt><dd>{dateFormat.format(new Date(item.submittedAt))}</dd></div>
                {item.description && <div><dt>Descripción</dt><dd>{item.description}</dd></div>}
                {item.status === 'APPROVED' && (
                  <div><dt>Recibe pedidos</dt><dd>{item.acceptingOrders ? 'Sí' : 'No'} · {item.available ? 'Disponible' : 'En pausa'}</dd></div>
                )}
                {item.status === 'REJECTED' && <div><dt>Motivo del rechazo</dt><dd>{item.rejectionReason}</dd></div>}
              </dl>

              {item.status === 'PENDING' && rejectingId !== item.id && (
                <div className="adm-actions">
                  <button type="button" className="adm-btn" disabled={busy}
                    onClick={() => void resolve(item, () => approveRestaurant(token, item.id), 'solicitud aprobada')}>Aprobar</button>
                  <button type="button" className="adm-btn adm-btn-danger" disabled={busy}
                    onClick={() => { setRejectingId(item.id); setReason(''); setReasonError(undefined) }}>Rechazar</button>
                </div>
              )}
              {item.status === 'PENDING' && rejectingId === item.id && (
                <div className="adm-reject">
                  <label htmlFor={'reason-' + item.id}>Motivo del rechazo (obligatorio)</label>
                  <textarea id={'reason-' + item.id} value={reason} maxLength={REJECT_REASON_MAX + 50}
                    aria-invalid={reasonError ? true : undefined} onChange={(e) => setReason(e.target.value)} />
                  {reasonError && <p className="adm-field-error">{reasonError}</p>}
                  <div className="adm-actions">
                    <button type="button" className="adm-btn adm-btn-danger" disabled={busy} onClick={() => confirmReject(item)}>Confirmar rechazo</button>
                    <button type="button" className="adm-btn adm-btn-ghost" disabled={busy} onClick={() => setRejectingId(null)}>Cancelar</button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </AdminLayout>
  )
}
