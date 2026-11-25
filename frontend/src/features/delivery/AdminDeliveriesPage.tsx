import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, errorMessage } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { useSession } from '../auth/RequireSession'
import { assignDelivery, listPending, releaseDelivery, type PendingDelivery } from './admin-deliveries-api'
import { DELIVERY_POLL_MS } from './courier-logic'
import { orderCode } from '../restaurant/orders-logic'
import { AdminLayout } from '../admin/AdminLayout'
import { Badge, EmptyState, ErrorState, LoadingBlock } from '../admin/admin-ui'

const dateFormat = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' })

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; items: PendingDelivery[] }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Entregas por resolver (RF-24): asigna manualmente a un candidato y libera las de repartidores inhabilitados. */
export function AdminDeliveriesPage() {
  const session = useSession()
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const token = session.token

  useEffect(() => {
    let active = true
    const load = () => {
      listPending(token)
        .then((items) => { if (active) setView({ kind: 'ready', items }) })
        .catch((error: unknown) => {
          if (!active) return
          if (error instanceof ApiError && error.status === 403) setView({ kind: 'forbidden' })
          else {
            const message = errorMessage(error)
            setView((current) => (current.kind === 'ready' ? current : { kind: 'error', message }))
          }
        })
    }
    load()
    const timer = setInterval(load, DELIVERY_POLL_MS)
    return () => { active = false; clearInterval(timer) }
  }, [token, attempt])


  const reload = () => setAttempt((n) => n + 1)

  async function run(action: () => Promise<unknown>, done: string) {
    if (busy) return
    setBusy(true)
    setNotice(null)
    try {
      await action()
      setNotice(done)
    } catch (error) {
      // 409: otra solicitud ya la resolvió o el repartidor dejó de ser candidato; se muestra el motivo.
      setNotice(errorMessage(error))
    } finally {
      setBusy(false)
      reload()
    }
  }

  if (view.kind === 'forbidden') {
    return (
      <AuthLayout title="Entregas pendientes">
        <p className="auth-alert" role="alert">Esta sección es solo para administradores.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  return (
    <AdminLayout title="Entregas pendientes" description="Asigna a un repartidor las entregas que el sistema no pudo asignar. La lista se actualiza sola.">
      {notice && <p className="adm-note" role="status">{notice}</p>}
      {view.kind === 'loading' && <LoadingBlock label="Cargando entregas" />}
      {view.kind === 'error' && <ErrorState message={view.message} onRetry={() => { setView({ kind: 'loading' }); reload() }} />}
      {view.kind === 'ready' && view.items.length === 0 && (
        <EmptyState title="Sin entregas pendientes" text="No hay entregas pendientes de asignación." />
      )}
      {view.kind === 'ready' && view.items.length > 0 && (
        <ul className="adm-cards">
          {view.items.map((item) => (
            <li key={item.orderId}>
              <div className="adm-cards-head">
                <h2>{item.restaurantName} <small>· Pedido {orderCode(item.orderId)}</small></h2>
                <Badge tone={item.courierDisabled ? 'bad' : 'warn'}>{item.courierDisabled ? 'Repartidor inhabilitado' : 'Sin repartidor'}</Badge>
              </div>
              <dl className="adm-facts">
                <div><dt>Listo desde</dt><dd>{dateFormat.format(new Date(item.readyAt))}</dd></div>
                <div><dt>Distrito de entrega</dt><dd>{item.district}</dd></div>
              </dl>
              {item.courierDisabled && (
                <div className="adm-actions">
                  <button type="button" className="adm-btn" disabled={busy}
                    onClick={() => void run(() => releaseDelivery(token, item.orderId), 'Entrega liberada; se intentará reasignar')}>
                    Liberar
                  </button>
                </div>
              )}
              {!item.courierDisabled && item.candidates.length === 0 && (
                <p className="adm-note">No hay repartidores disponibles. Se asignará sola cuando haya uno.</p>
              )}
              {!item.courierDisabled && item.candidates.length > 0 && (
                <div className="adm-actions">
                  {item.candidates.map((candidate) => (
                    <button key={candidate.courierId} type="button" className="adm-btn adm-btn-ghost" disabled={busy}
                      onClick={() => void run(() => assignDelivery(token, item.orderId, candidate.courierId), `Entrega asignada a ${candidate.name}`)}>
                      Asignar a {candidate.name}
                    </button>
                  ))}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </AdminLayout>
  )
}
