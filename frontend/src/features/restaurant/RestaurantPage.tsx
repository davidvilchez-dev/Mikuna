import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { FormField } from '../auth/FormField'
import { clearSession, getSession } from '../auth/session-storage'
import {
  getMyRestaurant,
  resubmitMyRestaurant,
  setAvailability,
  submitRestaurant,
  updateMyRestaurant,
  updateSchedule,
  type Restaurant,
  type RestaurantInput,
  type RestaurantStatus,
} from './restaurant-api'
import {
  WEEK_DAYS,
  toDayForms,
  toOpeningHours,
  validateRestaurantInput,
  validateSchedule,
  type DayForm,
  type RestaurantErrors,
  type ScheduleErrors,
} from './restaurant-validation'
import './restaurant.css'

const statusLabels: Record<RestaurantStatus, string> = {
  PENDING: 'Pendiente de aprobación',
  APPROVED: 'Aprobado',
  REJECTED: 'Rechazado',
}

const emptyInput: RestaurantInput = { name: '', category: '', description: '', address: '', zone: '', phone: '' }

type ViewState =
  | { kind: 'loading' }
  | { kind: 'none' }
  | { kind: 'ready'; restaurant: Restaurant }
  | { kind: 'forbidden' }
  | { kind: 'error'; message: string }

function toInput(restaurant: Restaurant): RestaurantInput {
  return {
    name: restaurant.name,
    category: restaurant.category,
    description: restaurant.description ?? '',
    address: restaurant.address,
    zone: restaurant.zone,
    phone: restaurant.phone,
  }
}

interface RestaurantFormProps {
  initial: RestaurantInput
  submitLabel: string
  /** Debe lanzar ApiError si la API rechaza; el formulario muestra los errores por campo. */
  onSubmit: (input: RestaurantInput) => Promise<void>
}

function RestaurantForm({ initial, submitLabel, onSubmit }: RestaurantFormProps) {
  const [values, setValues] = useState(initial)
  const [errors, setErrors] = useState<RestaurantErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const set = (field: keyof RestaurantInput) => (value: string) => setValues((current) => ({ ...current, [field]: value }))

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setFormError(null)
    const localErrors = validateRestaurantInput(values)
    setErrors(localErrors)
    if (Object.keys(localErrors).length > 0) return

    setSubmitting(true)
    try {
      await onSubmit(values)
    } catch (error) {
      if (error instanceof ApiError && error.status === 400) setErrors(error.errors as RestaurantErrors)
      setFormError(error instanceof ApiError ? error.message : GENERIC_ERROR)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <FormField id="name" label="Nombre comercial" value={values.name} onChange={(e) => set('name')(e.target.value)} error={errors.name} />
      <FormField id="category" label="Categoría o tipo de cocina" value={values.category} onChange={(e) => set('category')(e.target.value)} error={errors.category} />
      <div className="restaurant-field-textarea">
        <label htmlFor="description">Descripción breve (opcional)</label>
        <textarea id="description" value={values.description} onChange={(e) => set('description')(e.target.value)} aria-invalid={errors.description ? true : undefined} />
        {errors.description && <p className="auth-error">{errors.description}</p>}
      </div>
      <FormField id="address" label="Dirección" value={values.address} onChange={(e) => set('address')(e.target.value)} error={errors.address} />
      <FormField id="zone" label="Distrito o zona" value={values.zone} onChange={(e) => set('zone')(e.target.value)} error={errors.zone} />
      <FormField id="phone" label="Teléfono de contacto" type="tel" value={values.phone} onChange={(e) => set('phone')(e.target.value)} error={errors.phone} />
      {formError && <p className="auth-alert" role="alert">{formError}</p>}
      <button type="submit" className="auth-submit" disabled={submitting}>{submitting ? 'Guardando…' : submitLabel}</button>
    </form>
  )
}

interface ScheduleEditorProps {
  restaurant: Restaurant
  editable: boolean
  token: string
  onChange: (restaurant: Restaurant) => void
  onFailure: (error: unknown) => string
}

function ScheduleEditor({ restaurant, editable, token, onChange, onFailure }: ScheduleEditorProps) {
  const [days, setDays] = useState<DayForm[]>(() => toDayForms(restaurant.schedule))
  const [errors, setErrors] = useState<ScheduleErrors>({})
  const [message, setMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const patch = (day: string, changes: Partial<DayForm>) =>
    setDays((current) => current.map((entry) => (entry.day === day ? { ...entry, ...changes } : entry)))

  async function save() {
    if (saving) return
    setMessage(null)
    const localErrors = validateSchedule(days)
    setErrors(localErrors)
    if (Object.keys(localErrors).length > 0) return
    setSaving(true)
    try {
      onChange(await updateSchedule(token, toOpeningHours(days)))
      setMessage('Horario guardado')
    } catch (error) {
      setMessage(onFailure(error))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="restaurant-days">
      {WEEK_DAYS.map(({ day, label }) => {
        const entry = days.find((d) => d.day === day)!
        return (
          <div className="restaurant-day" key={day}>
            <strong>{label}</strong>
            <input type="time" aria-label={`${label}: apertura`} value={entry.opensAt} disabled={!editable || entry.closed} onChange={(e) => patch(day, { opensAt: e.target.value })} />
            <input type="time" aria-label={`${label}: cierre`} value={entry.closesAt} disabled={!editable || entry.closed} onChange={(e) => patch(day, { closesAt: e.target.value })} />
            <label className="restaurant-closed">
              <input type="checkbox" checked={entry.closed} disabled={!editable} onChange={(e) => patch(day, { closed: e.target.checked })} /> Cerrado
            </label>
            {errors[day] && <p className="auth-error">{errors[day]}</p>}
          </div>
        )
      })}
      {editable && <button type="button" className="auth-submit" onClick={save} disabled={saving}>{saving ? 'Guardando…' : 'Guardar horario'}</button>}
      {message && <p className="auth-notice" role="status">{message}</p>}
    </div>
  )
}

/** Solicitud, estado, datos, horario y disponibilidad del restaurante propio (RF-05 y RF-07). */
export function RestaurantPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)

  const token = session?.token ?? ''

  /** 401: la sesión no vale; otros errores se muestran. Devuelve el mensaje para el usuario. */
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
    getMyRestaurant(token)
      .then((restaurant) => { if (active) setState({ kind: 'ready', restaurant }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && error.status === 404) setState({ kind: 'none' })
        else if (error instanceof ApiError && error.status === 403) setState({ kind: 'forbidden' })
        else setState({ kind: 'error', message: handleFailure(error) })
      })
    return () => { active = false }
  }, [token, attempt, handleFailure])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  const show = (restaurant: Restaurant, message: string | null = null) => {
    setState({ kind: 'ready', restaurant })
    setNotice(message)
    setEditing(false)
  }

  if (state.kind === 'loading') {
    return <AuthLayout title="Mi restaurante"><p className="auth-description" role="status">Cargando tu restaurante…</p></AuthLayout>
  }

  if (state.kind === 'forbidden') {
    return (
      <AuthLayout title="Mi restaurante">
        <p className="auth-alert" role="alert">Esta sección es solo para cuentas de restaurante habilitadas.</p>
        <Link to="/mi-cuenta" className="auth-submit auth-submit-secondary">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  if (state.kind === 'error') {
    return (
      <AuthLayout title="Mi restaurante">
        <p className="auth-alert" role="alert">{state.message}</p>
        <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
      </AuthLayout>
    )
  }

  if (state.kind === 'none') {
    return (
      <AuthLayout title="Solicita el registro de tu restaurante" description="Completa los datos de tu negocio. Un administrador revisará tu solicitud.">
        <RestaurantForm
          initial={emptyInput}
          submitLabel="Enviar solicitud"
          onSubmit={async (input) => {
            try {
              show(await submitRestaurant(token, input), 'Solicitud enviada: queda pendiente de aprobación.')
            } catch (error) {
              if (error instanceof ApiError && error.status === 409) {
                // Ya existe una solicitud (otra pestaña o un reintento): se muestra la existente.
                setAttempt((n) => n + 1)
              }
              if (error instanceof ApiError && error.status === 401) handleFailure(error)
              throw error
            }
          }}
        />
        <Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link>
      </AuthLayout>
    )
  }

  const { restaurant } = state
  const isApproved = restaurant.status === 'APPROVED'
  const badgeClass = isApproved ? 'restaurant-badge restaurant-badge-ok' : restaurant.status === 'REJECTED' ? 'restaurant-badge restaurant-badge-bad' : 'restaurant-badge'

  async function run(action: () => Promise<Restaurant>, message: string) {
    if (busy) return
    setBusy(true)
    try {
      show(await action(), message)
    } catch (error) {
      setNotice(handleFailure(error))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title={restaurant.name} description={restaurant.category}>
      <p><span className={badgeClass}>{statusLabels[restaurant.status]}</span></p>
      {notice && <p className="auth-notice" role="status">{notice}</p>}

      {restaurant.status === 'PENDING' && (
        <p className="auth-notice">Aún no puedes recibir pedidos: un administrador debe aprobar tu solicitud. Mientras tanto puedes corregir tus datos.</p>
      )}
      {restaurant.status === 'REJECTED' && (
        <>
          <p className="auth-alert" role="alert">Tu solicitud fue rechazada: {restaurant.rejectionReason}</p>
          <p className="auth-description">Corrige tus datos y vuelve a enviarla.</p>
        </>
      )}
      {isApproved && (
        <p className="auth-notice" role="status">
          {restaurant.acceptingOrders ? 'Estás recibiendo pedidos ahora.' : 'Ahora no recibes pedidos: activa tu disponibilidad y revisa que estés dentro de tu horario.'}
        </p>
      )}

      {editing ? (
        <RestaurantForm
          initial={toInput(restaurant)}
          submitLabel={restaurant.status === 'REJECTED' ? 'Guardar y reenviar solicitud' : 'Guardar cambios'}
          onSubmit={async (input) => {
            try {
              const saved = await updateMyRestaurant(token, input)
              show(restaurant.status === 'REJECTED' ? await resubmitMyRestaurant(token) : saved,
                restaurant.status === 'REJECTED' ? 'Solicitud reenviada: queda pendiente de aprobación.' : 'Cambios guardados')
            } catch (error) {
              if (error instanceof ApiError && error.status === 401) handleFailure(error)
              throw error
            }
          }}
        />
      ) : (
        <>
          <dl className="auth-details">
            <div><dt>Dirección</dt><dd>{restaurant.address}</dd></div>
            <div><dt>Distrito o zona</dt><dd>{restaurant.zone}</dd></div>
            <div><dt>Teléfono</dt><dd>{restaurant.phone}</dd></div>
            {restaurant.description && <div><dt>Descripción</dt><dd>{restaurant.description}</dd></div>}
          </dl>
          <button type="button" className="auth-submit auth-submit-secondary" onClick={() => setEditing(true)}>
            {restaurant.status === 'REJECTED' ? 'Corregir y reenviar' : 'Editar datos'}
          </button>
        </>
      )}

      <section className="restaurant-section" aria-labelledby="schedule-title">
        <h2 id="schedule-title">Horario de atención</h2>
        {!isApproved && <p className="auth-description">Podrás definir tu horario y tu disponibilidad cuando tu restaurante sea aprobado.</p>}
        <ScheduleEditor
          key={restaurant.id + restaurant.schedule.map((d) => (d.opensAt ?? '-') + (d.closesAt ?? '-')).join()}
          restaurant={restaurant}
          editable={isApproved}
          token={token}
          onChange={(updated) => show(updated, null)}
          onFailure={handleFailure}
        />
      </section>

      <section className="restaurant-section" aria-labelledby="availability-title">
        <h2 id="availability-title">Disponibilidad</h2>
        <p className="auth-description">
          {restaurant.available ? 'Estás disponible para recibir pedidos dentro de tu horario.' : 'Estás en pausa: no recibirás pedidos.'}
        </p>
        <button
          type="button"
          className="auth-submit"
          disabled={!isApproved || busy}
          onClick={() => run(() => setAvailability(token, !restaurant.available), restaurant.available ? 'Disponibilidad en pausa' : 'Disponibilidad activada')}
        >
          {restaurant.available ? 'Pausar disponibilidad' : 'Activar disponibilidad'}
        </button>
      </section>

      {isApproved && (
        <section className="restaurant-section" aria-labelledby="catalog-title">
          <h2 id="catalog-title">Catálogo</h2>
          <p className="auth-description">Administra los productos que verán tus clientes.</p>
          <Link to="/mi-restaurante/productos" className="auth-submit">Mi catálogo</Link>
        </section>
      )}

      <Link to="/mi-cuenta" className="auth-switch">Volver a mi cuenta</Link>
    </AuthLayout>
  )
}
