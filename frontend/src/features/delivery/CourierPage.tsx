import { lazy, Suspense, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, errorMessage } from '../auth/auth-api'
import { AppIcon } from '../auth/AppIcon'
import { notifyError, notifySuccess } from '../ui/notify'
import { AuthLayout } from '../auth/AuthLayout'
import { useSession } from '../auth/RequireSession'
import {
  actOnDelivery,
  getAvailability,
  listDeliveries,
  sendLocation,
  setAvailability,
  type Availability,
  type CourierDelivery,
} from './courier-api'
import {
  DELIVERY_POLL_MS,
  DELIVERY_REASON_MAX,
  DELIVERY_STEPS,
  courierActions,
  courierDoneMessages,
  courierStatusHints,
  currentStep,
  navigationUrl,
  phoneHref,
  validateDeliveryReason,
  type CourierAction,
} from './courier-logic'
import type { RoutePoint } from './RouteMap'
import { orderCode } from '../restaurant/orders-logic'
import { formatPrice } from '../catalog/catalog-validation'
import { sharingMessages, startLocationSharing, type SharingState } from './location-sender'
import './courier.css'

/** Leaflet solo se descarga cuando hay una entrega con punto marcado. */
const RouteMap = lazy(() => import('./RouteMap'))

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; availability: Availability; deliveries: CourierDelivery[] }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

/** Panel del repartidor: disponibilidad (RF-21, RF-23) y entrega en curso (RF-25). Se actualiza sola cada pocos segundos. */
export function CourierPage() {
  const session = useSession()
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | undefined>()
  const [busy, setBusy] = useState(false)
  const [sharing, setSharing] = useState<SharingState>('idle')
  // Cambia cuando el repartidor pulsa «Compartir mi ubicación»: reinicia la escucha del GPS (y con ello el permiso).
  const [shareAttempt, setShareAttempt] = useState(0)
  // Última posición del propio repartidor (la que se envía al cliente); alimenta el mapa de la entrega.
  const [myPosition, setMyPosition] = useState<{ latitude: number; longitude: number } | null>(null)

  const token = session.token

  useEffect(() => {
    let active = true
    const load = () => {
      Promise.all([getAvailability(token), listDeliveries(token)])
        .then(([availability, deliveries]) => { if (active) setView({ kind: 'ready', availability, deliveries }) })
        .catch((error: unknown) => {
          if (!active) return
          if (error instanceof ApiError && error.status === 403) setView({ kind: 'forbidden' })
          else {
            const message = errorMessage(error)
            // Un fallo de una actualización periódica no borra lo que ya se muestra.
            setView((current) => (current.kind === 'ready' ? current : { kind: 'error', message }))
          }
        })
    }
    load()
    const timer = setInterval(load, DELIVERY_POLL_MS)
    return () => { active = false; clearInterval(timer) }
  }, [token, attempt])

  // Mientras haya una entrega Asignada o Recogida se comparte la ubicación del navegador (RF-26).
  const hasActiveDelivery = view.kind === 'ready' && view.deliveries.length > 0
  useEffect(() => {
    if (!token || !hasActiveDelivery) return
    return startLocationSharing((position) => {
      setMyPosition({ latitude: position.latitude, longitude: position.longitude })
      return sendLocation(token, position)
    }, setSharing)
  }, [token, hasActiveDelivery, shareAttempt])


  const reload = () => setAttempt((n) => n + 1)

  async function toggle(available: boolean) {
    if (busy) return
    setBusy(true)
    try {
      await setAvailability(token, available)
      notifySuccess(available ? 'Estás en línea: te asignaremos la siguiente entrega' : 'Te desconectaste: no recibirás nuevas entregas')
      reload()
    } catch (error) {
      notifyError(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  async function run(delivery: CourierDelivery, action: CourierAction, reasonText?: string) {
    if (busy) return
    setBusy(true)
    try {
      await actOnDelivery(token, delivery.orderId, action, reasonText)
      notifySuccess(courierDoneMessages[action])
      setRejecting(false)
      setReason('')
      reload()
    } catch (error) {
      // 404/409: la entrega cambió (reasignada o ya registrada); se muestra el motivo y se recarga.
      notifyError(errorMessage(error))
      reload()
    } finally {
      setBusy(false)
    }
  }

  function confirmReject(delivery: CourierDelivery) {
    const error = validateDeliveryReason(reason)
    setReasonError(error)
    if (error) return
    void run(delivery, 'reject', reason)
  }

  if (view.kind === 'forbidden') {
    return (
      <AuthLayout title="Entregas">
        <p className="auth-alert" role="alert">Esta sección es solo para repartidores con la cuenta habilitada.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout wide title="Entregas" description="Conéctate para recibir pedidos y sigue tu ruta.">
      {view.kind === 'loading' && <p className="auth-description" role="status">Cargando…</p>}
      {view.kind === 'error' && (
        <>
          <p className="auth-alert" role="alert">{view.message}</p>
          <button type="button" className="auth-submit" onClick={() => { setView({ kind: 'loading' }); reload() }}>Reintentar</button>
        </>
      )}
      {view.kind === 'ready' && (
        <div className="cr">
          <section className={view.availability.available ? 'cr-status cr-status-on' : 'cr-status'} aria-label="Disponibilidad">
            <span className="cr-dot" aria-hidden="true" />
            <div className="cr-status-text">
              <strong>{view.availability.available ? 'Estás en línea' : 'Estás desconectado'}</strong>
              <small>
                {hasActiveDelivery
                  ? sharingMessages[sharing]
                  : view.availability.available ? 'Esperando el siguiente pedido' : 'Conéctate para recibir pedidos'}
              </small>
            </div>
            <button type="button" role="switch" aria-checked={view.availability.available} aria-label="Estar en línea"
              className="cr-switch" disabled={busy} onClick={() => void toggle(!view.availability.available)}>
              <span className="cr-switch-knob" />
            </button>
          </section>

          {hasActiveDelivery && (
            <section className={'cr-share cr-share-' + sharing} role={sharing === 'denied' ? 'alert' : 'status'} aria-label="Ubicación">
              <span className="cr-share-icon"><AppIcon name="navigate" /></span>
              <div className="cr-share-text">
                <strong>
                  {sharing === 'sharing' ? 'Compartiendo tu ubicación' : sharing === 'denied' ? 'Ubicación bloqueada' : sharing === 'unsupported' ? 'Ubicación no disponible' : sharing === 'error' ? 'No se pudo enviar tu ubicación' : 'Comparte tu ubicación'}
                </strong>
                <small>
                  {sharing === 'sharing' ? 'El cliente ve en el mapa por dónde vienes.'
                    : sharing === 'denied' ? 'Toca el candado junto a la dirección del navegador, abre «Permisos» y elige «Ubicación: Permitir». Luego pulsa el botón.'
                    : sharing === 'unsupported' ? sharingMessages.unsupported
                    : sharing === 'error' ? 'Se reintentará sola; si sigue fallando, pulsa el botón.'
                    : 'El navegador te pedirá permiso. Así el cliente puede seguir su pedido en vivo.'}
                </small>
              </div>
              {sharing !== 'sharing' && sharing !== 'unsupported' && (
                <button type="button" className="auth-submit" onClick={() => setShareAttempt((n) => n + 1)}>
                  {sharing === 'denied' ? 'Ya la permití, reintentar' : 'Compartir mi ubicación'}
                </button>
              )}
            </section>
          )}

          {view.deliveries.length === 0 && (
            <div className="cr-empty" role="status">
              <span className={view.availability.available ? 'cr-empty-icon cr-pulse' : 'cr-empty-icon'}><AppIcon name="scooter" /></span>
              <strong>{view.availability.available ? 'Buscando pedidos para ti…' : 'No estás recibiendo pedidos'}</strong>
              <p>
                {view.availability.available
                  ? 'Te asignaremos la siguiente entrega en cuanto haya un pedido listo. Mantén esta pantalla abierta.'
                  : 'Activa el interruptor de arriba para ponerte en línea y empezar a recibir entregas.'}
              </p>
              {!view.availability.available && (
                <button type="button" className="auth-submit" disabled={busy} onClick={() => void toggle(true)}>Ponerme en línea</button>
              )}
            </div>
          )}

          {view.deliveries.map((delivery) => {
            const step = currentStep(delivery.status)
            const phone = phoneHref(delivery.delivery.phone)
            const { latitude, longitude } = delivery.delivery
            const pickup = delivery.restaurant
            const mapPoints: RoutePoint[] = []
            if (myPosition) mapPoints.push({ kind: 'courier', ...myPosition, label: 'Tú' })
            if (pickup.latitude !== null && pickup.longitude !== null) mapPoints.push({ kind: 'pickup', latitude: pickup.latitude, longitude: pickup.longitude, label: 'Recoger aquí' })
            if (latitude !== null && longitude !== null) mapPoints.push({ kind: 'dropoff', latitude, longitude, label: 'Entregar aquí' })
            const hasPoint = mapPoints.length > 0
            const options = courierActions(delivery.status)
            const main = options.find((option) => option.action !== 'reject')
            const canReject = options.some((option) => option.action === 'reject')
            // Antes de recoger, la ruta va al restaurante; después, a la casa del cliente.
            const toRestaurant = delivery.status === 'ASSIGNED'
            const route = toRestaurant
              ? navigationUrl({ street: pickup.address ?? pickup.name, district: pickup.zone ?? '', latitude: pickup.latitude, longitude: pickup.longitude })
              : navigationUrl({ ...delivery.delivery })
            const receiverPhone = delivery.receiver ? phoneHref(delivery.receiver.phone) : null
            const cash = delivery.payment?.method === 'CASH'
            return (
              <article key={delivery.orderId} className="cr-delivery">
                <div className="cr-order-head">
                  <strong className="cr-order-code">Pedido {orderCode(delivery.orderId)}</strong>
                  {delivery.payment && (
                    <span className={cash ? 'cr-pay cr-pay-cash' : 'cr-pay'}>
                      {cash ? `Cobra ${formatPrice(delivery.payment.total)} en efectivo` : 'Pagado con tarjeta: no cobres'}
                    </span>
                  )}
                </div>
                <ol className="cr-steps" aria-label="Avance de la entrega">
                  {DELIVERY_STEPS.map((label, index) => (
                    <li key={label} className={index < step ? 'cr-step cr-step-done' : index === step ? 'cr-step cr-step-active' : 'cr-step'} aria-current={index === step ? 'step' : undefined}>
                      <span className="cr-step-mark">{index < step ? <AppIcon name="check" /> : index + 1}</span>
                      {label}
                    </li>
                  ))}
                </ol>
                {courierStatusHints[delivery.status] && <p className="cr-hint">{courierStatusHints[delivery.status]}</p>}

                <div className="cr-body">
                  <div className="cr-info">
                    <div className="cr-block">
                      <span className="cr-block-icon"><AppIcon name="store" /></span>
                      <div>
                        <small>Recoger en</small>
                        <strong>{delivery.restaurant.name}</strong>
                        {delivery.restaurant.address && <span>{delivery.restaurant.address}{delivery.restaurant.zone ? `, ${delivery.restaurant.zone}` : ''}</span>}
                        {delivery.restaurant.phone && <a className="cr-inline" href={phoneHref(delivery.restaurant.phone) ?? undefined}>Llamar al local · {delivery.restaurant.phone}</a>}
                      </div>
                    </div>
                    <div className="cr-block">
                      <span className="cr-block-icon cr-block-icon-dest"><AppIcon name="pin" /></span>
                      <div>
                        <small>Entregar en{delivery.delivery.label ? ` · ${delivery.delivery.label}` : ''}</small>
                        <strong>{delivery.delivery.street}</strong>
                        <span>{delivery.delivery.district}</span>
                        {delivery.delivery.reference && <span className="cr-reference">Referencia: {delivery.delivery.reference}</span>}
                        {delivery.receiver && (
                          <span className="cr-reference">
                            Recibe: {delivery.receiver.name}
                            {receiverPhone && <> · <a className="cr-inline" href={receiverPhone}>{delivery.receiver.phone}</a></>}
                            {delivery.receiver.note ? ` · ${delivery.receiver.note}` : ''}
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="cr-block">
                      <span className="cr-block-icon"><AppIcon name="box" /></span>
                      <div>
                        <small>Productos</small>
                        <ul className="cr-lines">
                          {delivery.lines.map((line) => <li key={line.productName}><b>{line.quantity}×</b> {line.productName}</li>)}
                        </ul>
                      </div>
                    </div>
                    <div className="cr-links">
                      <a className="cr-link cr-link-primary" href={route} target="_blank" rel="noopener noreferrer">
                        <AppIcon name="navigate" />{toRestaurant ? 'Ir al restaurante' : 'Ir al cliente'}
                      </a>
                      {phone && <a className="cr-link" href={phone}><AppIcon name="phone" />Llamar al cliente</a>}
                    </div>
                  </div>

                  <div className="cr-map-wrap">
                    {hasPoint ? (
                      <>
                      <Suspense fallback={<p className="auth-description" role="status">Cargando el mapa…</p>}>
                        <RouteMap points={mapPoints} ariaLabel="Mapa de la entrega: tu ubicación, el local y el destino" />
                      </Suspense>
                      <div className="rm-legend" aria-hidden="true">
                        {myPosition && <span><i className="rm-l-courier" />Tú</span>}
                        {mapPoints.some((p) => p.kind === 'pickup') && <span><i className="rm-l-pickup" />Recoger</span>}
                        {mapPoints.some((p) => p.kind === 'dropoff') && <span><i className="rm-l-dropoff" />Entregar</span>}
                      </div>
                      </>
                    ) : (
                      <div className="cr-nomap">
                        <AppIcon name="pin" />
                        <strong>Todavía no hay puntos para mostrar</strong>
                        <p>Usa la dirección y la referencia, o toca «Cómo llegar» para buscarla. Tu ubicación aparecerá aquí al compartirla.</p>
                      </div>
                    )}
                  </div>
                </div>

                {!rejecting && (
                  <div className="cr-actions">
                    {main && (
                      <button type="button" className="auth-submit cr-main" disabled={busy} onClick={() => void run(delivery, main.action)}>
                        {main.action === 'complete' && cash ? `Entregué y cobré ${formatPrice(delivery.payment.total)}` : main.label}
                      </button>
                    )}
                    {canReject && (
                      <button type="button" className="cr-reject" disabled={busy} onClick={() => { setRejecting(true); setReason(''); setReasonError(undefined) }}>Rechazar entrega</button>
                    )}
                  </div>
                )}
                {rejecting && (
                  <div className="cr-reject-panel">
                    <label htmlFor="delivery-reason">Motivo del rechazo <span className="addr-optional">(opcional)</span></label>
                    <textarea id="delivery-reason" rows={3} value={reason} maxLength={DELIVERY_REASON_MAX + 50}
                      aria-invalid={reasonError ? true : undefined} onChange={(e) => setReason(e.target.value)} />
                    {reasonError && <p className="auth-error">{reasonError}</p>}
                    <div className="cr-actions">
                      <button type="button" className="auth-submit" disabled={busy} onClick={() => confirmReject(delivery)}>Confirmar rechazo</button>
                      <button type="button" className="auth-submit auth-submit-secondary" disabled={busy} onClick={() => setRejecting(false)}>Cancelar</button>
                    </div>
                  </div>
                )}
              </article>
            )
          })}
        </div>
      )}
    </AuthLayout>
  )
}
