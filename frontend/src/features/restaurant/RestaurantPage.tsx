import { Suspense, lazy, useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AppIcon } from '../auth/AppIcon'
import { notifyError, notifySuccess } from '../ui/notify'
import { Modal } from '../ui/Modal'
import { AuthLayout } from '../auth/AuthLayout'
import { RestaurantLayout } from './RestaurantLayout'
import { FormField } from '../auth/FormField'
import { clearSession, getSession } from '../auth/session-storage'
import {
  getMyRestaurant,
  resubmitMyRestaurant,

  submitRestaurant,
  updateMyRestaurant,
  updateImages,
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
import { addBranch } from './restaurant-api'
import { setBranchId } from './branch-storage'
import '../cart/location-picker.css'
import './restaurant.css'

const LocationPicker = lazy(() => import('../cart/LocationPicker'))

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
    latitude: restaurant.latitude ?? null,
    longitude: restaurant.longitude ?? null,
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

  const [mapOpen, setMapOpen] = useState(false)

  const set = (field: 'name' | 'category' | 'description' | 'address' | 'zone' | 'phone') => (value: string) =>
    setValues((current) => ({ ...current, [field]: value }))
  const located = values.latitude != null && values.longitude != null

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

  if (mapOpen) {
    return (
      <Suspense fallback={<p className="auth-description" role="status">Cargando el mapa…</p>}>
        <LocationPicker
          initial={located ? { latitude: values.latitude as number, longitude: values.longitude as number } : null}
          confirmLabel="Confirmar ubicación del local"
          onConfirm={(point, suggestion) => {
            setValues((current) => ({
              ...current,
              ...point,
              address: current.address.trim() === '' && suggestion ? suggestion.street : current.address,
            }))
            setMapOpen(false)
          }}
          onCancel={() => setMapOpen(false)}
        />
      </Suspense>
    )
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
      <div className={located ? 'locsum locsum-ok' : 'locsum'}>
        <span className="locsum-icon"><AppIcon name={located ? 'check' : 'pin'} /></span>
        <div className="locsum-text">
          <strong>{located ? 'Ubicación marcada en el mapa' : 'Ubicación en el mapa'}</strong>
          <small>{located ? 'Los repartidores llegarán a este punto.' : 'Marca dónde está el local para que el repartidor llegue sin perderse.'}</small>
        </div>
        <button type="button" className="auth-submit auth-submit-secondary" onClick={() => setMapOpen(true)}>{located ? 'Cambiar' : 'Marcar en el mapa'}</button>
      </div>
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
  const [saving, setSaving] = useState(false)

  const patch = (day: string, changes: Partial<DayForm>) =>
    setDays((current) => current.map((entry) => (entry.day === day ? { ...entry, ...changes } : entry)))

  async function save() {
    if (saving) return
    const localErrors = validateSchedule(days)
    setErrors(localErrors)
    if (Object.keys(localErrors).length > 0) return
    setSaving(true)
    try {
      onChange(await updateSchedule(token, toOpeningHours(days)))
      notifySuccess('Horario guardado')
    } catch (error) {
      notifyError(onFailure(error))
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
    </div>
  )
}

/** Solicitud, estado, datos, horario y disponibilidad del restaurante propio (RF-05 y RF-07). */
interface ImageUploaderProps {
  restaurant: Restaurant
  onSave: (logo: File | null, cover: File | null) => Promise<void>
}

function ImageUploader({ restaurant, onSave }: ImageUploaderProps) {
  const [logo, setLogo] = useState<File | null>(null)
  const [cover, setCover] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [inputKey, setInputKey] = useState(0)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || (!logo && !cover)) return
    setBusy(true)
    setError(null)
    try {
      await onSave(logo, cover)
      setLogo(null)
      setCover(null)
      setInputKey((n) => n + 1)
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : GENERIC_ERROR)
    } finally {
      setBusy(false)
    }
  }

  const logoSrc = useMemo(() => (logo ? URL.createObjectURL(logo) : restaurant.logoUrl), [logo, restaurant.logoUrl])
  const coverSrc = useMemo(() => (cover ? URL.createObjectURL(cover) : restaurant.coverUrl), [cover, restaurant.coverUrl])
  useEffect(() => () => { if (logoSrc && logo) URL.revokeObjectURL(logoSrc) }, [logoSrc, logo])
  useEffect(() => () => { if (coverSrc && cover) URL.revokeObjectURL(coverSrc) }, [coverSrc, cover])

  const discard = () => { setLogo(null); setCover(null); setError(null); setInputKey((n) => n + 1) }
  const dirty = Boolean(logo || cover)

  return (
    <form className="rs-images" onSubmit={submit} noValidate>
      <div className="rs-banner">
        <label className="rs-cover-pick" htmlFor="rs-cover">
          {coverSrc ? <img src={coverSrc} alt="Portada" /> : <span className="rs-cover-empty">Sin portada</span>}
          <span className="rs-pick-badge"><AppIcon name="camera" />{coverSrc ? 'Cambiar portada' : 'Subir portada'}</span>
        </label>
        <input key={'c' + inputKey} id="rs-cover" className="rs-file" type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => setCover(e.target.files?.[0] ?? null)} />
        <label className="rs-logo-pick" htmlFor="rs-logo" aria-label={logoSrc ? 'Cambiar logo' : 'Subir logo'}>
          {logoSrc ? <img src={logoSrc} alt="Logo" /> : <span>{restaurant.name.charAt(0).toUpperCase()}</span>}
          <span className="rs-pick-badge rs-pick-badge-round"><AppIcon name="camera" /></span>
        </label>
        <input key={'l' + inputKey} id="rs-logo" className="rs-file" type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => setLogo(e.target.files?.[0] ?? null)} />
      </div>
      <p className="rs-images-hint">Toca el logo o la portada para cambiarlos. JPG, PNG o WebP de hasta 2 MB.</p>
      {error && <p className="auth-alert" role="alert">{error}</p>}
      {dirty && (
        <div className="rs-images-actions">
          <button type="submit" className="auth-submit" disabled={busy}>{busy ? 'Subiendo…' : 'Guardar imágenes'}</button>
          <button type="button" className="auth-submit auth-submit-secondary" disabled={busy} onClick={discard}>Descartar</button>
        </div>
      )}
    </form>
  )
}

export function RestaurantPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [editing, setEditing] = useState(false)

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
    if (message) notifySuccess(message)
    setEditing(false)
  }

  if (state.kind === 'loading') {
    return <RestaurantLayout title="Mi restaurante"><p className="auth-description" role="status">Cargando tu restaurante…</p></RestaurantLayout>
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
      <RestaurantLayout title="Mi restaurante">
        <p className="auth-alert" role="alert">{state.message}</p>
        <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
      </RestaurantLayout>
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
  const statusClass = isApproved ? 'rs-chip rs-chip-ok' : restaurant.status === 'REJECTED' ? 'rs-chip rs-chip-bad' : 'rs-chip'


  return (
    <RestaurantLayout title="Mi negocio" description="Tus datos, tu horario y tu estado en Mikuna.">

      <section className={restaurant.available && isApproved ? 'rs-hero' : 'rs-hero'} aria-label="Estado del restaurante">
        <span className="rs-hero-icon">
          {restaurant.logoUrl ? <img src={restaurant.logoUrl} alt="" /> : <AppIcon name="store" />}
        </span>
        <div className="rs-hero-text">
          <h2>{restaurant.name}</h2>
          <p>{restaurant.category} · {restaurant.zone}</p>
          <span className={statusClass}>{statusLabels[restaurant.status]}</span>
        </div>
      </section>

      {restaurant.status === 'PENDING' && (
        <p className="auth-notice">Aún no puedes recibir pedidos: un administrador debe aprobar tu solicitud. Mientras tanto puedes corregir tus datos.</p>
      )}
      {restaurant.status === 'REJECTED' && (
        <p className="auth-alert" role="alert">Tu solicitud fue rechazada: {restaurant.rejectionReason}. Corrige tus datos y vuelve a enviarla.</p>
      )}

      <div className="rs-grid">
        <section className="rs-card" aria-labelledby="images-title">
          <h2 id="images-title">Imagen del negocio</h2>
          <p className="auth-description">Así ven tus clientes tu restaurante en la lista.</p>
          <ImageUploader
            restaurant={restaurant}
            onSave={async (logo, cover) => {
              try {
                show(await updateImages(token, logo, cover), 'Imágenes guardadas')
              } catch (error) {
                throw new Error(handleFailure(error), { cause: error })
              }
            }}
          />
        </section>

        <section className="rs-card" aria-labelledby="data-title">
          <h2 id="data-title">Datos del negocio</h2>
          <dl className="rs-data">
            <div><dt>Dirección</dt><dd>{restaurant.address}</dd></div>
            <div><dt>Distrito o zona</dt><dd>{restaurant.zone}</dd></div>
            <div><dt>Teléfono</dt><dd>{restaurant.phone}</dd></div>
            <div><dt>Ubicación en el mapa</dt><dd>{restaurant.latitude != null ? 'Marcada' : 'Sin marcar'}</dd></div>
            {restaurant.description && <div><dt>Descripción</dt><dd>{restaurant.description}</dd></div>}
          </dl>
          <button type="button" className="auth-submit auth-submit-secondary rs-edit" onClick={() => setEditing(true)}>
            {restaurant.status === 'REJECTED' ? 'Corregir y reenviar' : 'Editar datos'}
          </button>
          {editing && (
            <Modal title="Datos del negocio" onClose={() => setEditing(false)}>
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
            </Modal>
          )}
        </section>

        <section className="rs-card" aria-labelledby="schedule-title">
          <h2 id="schedule-title">Horario de atención</h2>
          {!isApproved && <p className="auth-description">Podrás definir tu horario cuando tu restaurante sea aprobado.</p>}
          <ScheduleEditor
            key={restaurant.id + restaurant.schedule.map((d) => (d.opensAt ?? '-') + (d.closesAt ?? '-')).join()}
            restaurant={restaurant}
            editable={isApproved}
            token={token}
            onChange={(updated) => show(updated, null)}
            onFailure={handleFailure}
          />
        </section>
      </div>
    </RestaurantLayout>
  )
}

/** Alta de una sucursal más: nace pendiente y la revisa un administrador como cualquier solicitud. */
export function NewBranchPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [base, setBase] = useState<Restaurant | null>(null)
  const token = session?.token ?? ''

  useEffect(() => {
    if (!token) return
    let active = true
    getMyRestaurant(token).then((value) => { if (active) setBase(value) }).catch(() => { /* el formulario funciona sin datos base */ })
    return () => { active = false }
  }, [token])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  return (
    <RestaurantLayout title="Agregar sucursal" description="Cada sucursal tiene su dirección, su horario y sus pedidos. Un administrador la aprueba antes de publicarla.">
      <section className="rs-card">
        <RestaurantForm
          key={base?.id ?? 'nueva'}
          initial={{ ...emptyInput, category: base?.category ?? '', phone: base?.phone ?? '', description: base?.description ?? '' }}
          submitLabel="Enviar sucursal"
          onSubmit={async (input) => {
            const created = await addBranch(token, input)
            setBranchId(created.id)
            notifySuccess('Sucursal enviada: queda pendiente de aprobación.')
            navigate('/mi-restaurante/negocio', { replace: true })
          }}
        />
      </section>
    </RestaurantLayout>
  )
}
