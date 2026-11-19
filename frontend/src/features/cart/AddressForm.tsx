import { lazy, Suspense, useState, type FormEvent } from 'react'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AppIcon } from '../auth/AppIcon'
import { FormField } from '../auth/FormField'
import type { AddressData } from './cart-api'
import { validateAddress, type AddressErrors } from './cart-validation'
import { DISTRICTS } from './address-options'
import './address.css'
import './location-picker.css'

/** Leaflet solo se descarga cuando alguien va a marcar una ubicación. */
const LocationPicker = lazy(() => import('./LocationPicker'))

interface AddressFormProps {
  initial: AddressData
  submitLabel: string
  /** Debe lanzar ApiError si la API rechaza; el formulario muestra los errores por campo. */
  onSubmit: (data: AddressData) => Promise<void>
  onCancel: () => void
}

const FORM_FIELDS = ['label', 'street', 'district', 'reference', 'contactPhone']

type Place = 'Casa' | 'Trabajo' | 'Otro'

const OTHER_DISTRICT = '__otro__'

const PLACES: readonly Place[] = ['Casa', 'Trabajo', 'Otro']

/** Tipos de vía y de lote usuales en la ciudad; se anteponen al texto de la dirección. */
const STREET_PREFIXES = ['Jr.', 'Av.', 'Calle', 'Psje.', 'Mz./Lt.'] as const
const PREFIX_PATTERN = /^(Jr\.|Av\.|Calle|Psje\.|Mz\.\/Lt\.)\s*/i

function placeOf(label: string): Place | null {
  const value = label.trim().toLowerCase()
  if (!value) return null
  if (value === 'casa') return 'Casa'
  if (value === 'trabajo') return 'Trabajo'
  return 'Otro'
}

function knownDistrict(district: string): string | null {
  const value = district.trim().toLowerCase()
  return DISTRICTS.find((item) => item.value.toLowerCase() === value)?.value ?? null
}

/** Alta y edición de una dirección de entrega (RF-12), pensada para las calles y barrios de Ayacucho. */
export function AddressForm({ initial, submitLabel, onSubmit, onCancel }: AddressFormProps) {
  const [values, setValues] = useState(initial)
  const [place, setPlace] = useState<Place | null>(() => placeOf(initial.label))
  const [otherDistrict, setOtherDistrict] = useState(() => initial.district.trim() !== '' && knownDistrict(initial.district) === null)
  const [errors, setErrors] = useState<AddressErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  // Una dirección nueva empieza por el mapa; al editar se parte de los detalles ya guardados.
  const [step, setStep] = useState<'map' | 'details'>(() => (initial.latitude === null && initial.street.trim() === '' ? 'map' : 'details'))
  const located = values.latitude !== null && values.longitude !== null
  // Dirección que sugirió el mapa para el punto; se muestra en el resumen y se puede corregir abajo.
  const [mapAddress, setMapAddress] = useState<string | null>(null)
  const [autoFilled, setAutoFilled] = useState(false)

  const set = (field: keyof AddressData) => (value: string) => setValues((current) => ({ ...current, [field]: value }))

  function choosePlace(next: Place) {
    setPlace(next)
    set('label')(next === 'Otro' ? '' : next)
  }

  function addPrefix(prefix: string) {
    setValues((current) => ({ ...current, street: prefix + ' ' + current.street.replace(PREFIX_PATTERN, '') }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setFormError(null)
    const localErrors = validateAddress(values)
    setErrors(localErrors)
    if (Object.keys(localErrors).length > 0) return

    setSubmitting(true)
    try {
      await onSubmit(values)
    } catch (error) {
      let message = error instanceof ApiError ? error.message : GENERIC_ERROR
      if (error instanceof ApiError && error.status === 400) {
        setErrors(error.errors as AddressErrors)
        // Los errores de campos que el formulario no muestra (por ejemplo, el punto del mapa) se leen en el aviso.
        const hidden = Object.entries(error.errors).filter(([field]) => !FORM_FIELDS.includes(field)).map(([, text]) => text)
        if (hidden.length > 0) message = hidden.join(' ')
      }
      setFormError(message)
    } finally {
      setSubmitting(false)
    }
  }

  if (step === 'map') {
    return (
      <Suspense fallback={<p className="auth-description" role="status">Cargando el mapa…</p>}>
        <LocationPicker
          initial={located ? { latitude: values.latitude as number, longitude: values.longitude as number } : null}
          confirmLabel="Confirmar ubicación"
          onConfirm={(point, suggestion) => {
            setValues((current) => ({
              ...current,
              ...point,
              street: suggestion ? suggestion.street : current.street,
              district: suggestion?.district ?? current.district,
            }))
            if (suggestion?.district) setOtherDistrict(false)
            setMapAddress(suggestion ? suggestion.display : null)
            setAutoFilled(Boolean(suggestion))
            setStep('details')
          }}
          onCancel={() => (located || values.street.trim() !== '' ? setStep('details') : onCancel())}
        />
      </Suspense>
    )
  }

  return (
    <form className="addr-form" onSubmit={handleSubmit} noValidate>
      <div className={located ? 'locsum locsum-ok' : 'locsum'}>
        <span className="locsum-icon"><AppIcon name={located ? 'check' : 'pin'} /></span>
        <span className="locsum-text">
          <strong>{located ? 'Ubicación marcada en el mapa' : 'Aún no marcas el punto exacto'}</strong>
          <small>{located ? (mapAddress ?? 'El repartidor llegará a ese punto.') : 'Ayuda al repartidor a llegar más rápido.'}</small>
        </span>
        <button type="button" className="locsum-change" onClick={() => setStep('map')}>{located ? 'Cambiar' : 'Marcar'}</button>
      </div>

      {autoFilled && <p className="addr-autofill" role="status">Completamos la calle y el distrito con tu ubicación. Revísalos y corrígelos si hace falta.</p>}

      <div className="addr-row">
        <div className="auth-field">
          <label htmlFor="address-place">¿Qué lugar es?</label>
          <select id="address-place" className="addr-select" value={place ?? ''} onChange={(e) => { if (e.target.value) choosePlace(e.target.value as Place) }}>
            <option value="" disabled>Selecciona</option>
            {PLACES.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
        </div>

        <div className="auth-field">
          <label htmlFor="address-district">Distrito</label>
          <select id="address-district" className="addr-select" aria-invalid={errors.district && !otherDistrict ? true : undefined}
            value={otherDistrict ? OTHER_DISTRICT : knownDistrict(values.district) ?? ''}
            onChange={(e) => {
              if (e.target.value === OTHER_DISTRICT) { setOtherDistrict(true); set('district')('') }
              else { setOtherDistrict(false); set('district')(e.target.value) }
            }}>
            <option value="" disabled>Selecciona</option>
            {DISTRICTS.map((district) => <option key={district.value} value={district.value}>{district.value}</option>)}
            <option value={OTHER_DISTRICT}>Otro distrito…</option>
          </select>
          {errors.district && !otherDistrict && <p className="auth-error" role="alert">{errors.district}</p>}
        </div>
      </div>

      {place === 'Otro' && (
        <FormField id="address-label" label="Nombre del lugar" placeholder="Ej. Casa de mi tía" maxLength={50}
          value={values.label} onChange={(e) => set('label')(e.target.value)} error={errors.label} />
      )}
      {otherDistrict && (
        <FormField id="address-district-other" label="Nombre del distrito" maxLength={100}
          value={values.district} onChange={(e) => set('district')(e.target.value)} error={errors.district} hint="Solo repartimos en los distritos cubiertos." />
      )}

      <fieldset className="addr-group">
        <legend>Calle y número</legend>
        <div className="addr-chips addr-chips-small" aria-label="Tipo de vía">
          {STREET_PREFIXES.map((prefix) => (
            <button type="button" key={prefix} className="addr-chip-button" onClick={() => addPrefix(prefix)}>{prefix}</button>
          ))}
        </div>
        <FormField id="address-street" label="Jirón, avenida, calle o pasaje" placeholder="Ej. Jr. Libertad 245"
          hint="Si no tiene número, escribe s/n. En asociaciones o urbanizaciones indica manzana y lote."
          value={values.street} onChange={(e) => set('street')(e.target.value)} error={errors.street} />
      </fieldset>

      <div className="auth-field">
        <label htmlFor="address-reference">Referencia <span className="addr-optional">(opcional)</span></label>
        <textarea id="address-reference" rows={3} maxLength={200} aria-invalid={errors.reference ? true : undefined}
          placeholder="Ej. Frente al mercado, casa de portón verde de dos pisos"
          value={values.reference} onChange={(e) => set('reference')(e.target.value)} />
        <p className="auth-hint">Ayuda al repartidor a encontrar tu casa.</p>
        {errors.reference && <p className="auth-error">{errors.reference}</p>}
      </div>

      <FormField id="address-phone" label="Teléfono de contacto (opcional)" inputMode="tel" placeholder="Ej. 966 123 456"
        hint="Para que el repartidor pueda llamarte." value={values.contactPhone} onChange={(e) => set('contactPhone')(e.target.value)} error={errors.contactPhone} />

      {formError && <p className="auth-alert" role="alert">{formError}</p>}
      <div className="addr-actions">
        <button type="submit" className="auth-submit" disabled={submitting}>{submitting ? 'Guardando…' : submitLabel}</button>
        <button type="button" className="auth-submit auth-submit-secondary" onClick={onCancel} disabled={submitting}>Cancelar</button>
      </div>
    </form>
  )
}
