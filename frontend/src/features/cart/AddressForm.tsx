import { useState, type FormEvent } from 'react'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { FormField } from '../auth/FormField'
import type { AddressData } from './cart-api'
import { validateAddress, type AddressErrors } from './cart-validation'

interface AddressFormProps {
  initial: AddressData
  submitLabel: string
  /** Debe lanzar ApiError si la API rechaza; el formulario muestra los errores por campo. */
  onSubmit: (data: AddressData) => Promise<void>
  onCancel: () => void
}

/** Alta y edición de una dirección de entrega (RF-12). El backend rechaza los distritos fuera de la zona. */
export function AddressForm({ initial, submitLabel, onSubmit, onCancel }: AddressFormProps) {
  const [values, setValues] = useState(initial)
  const [errors, setErrors] = useState<AddressErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const set = (field: keyof AddressData) => (value: string) => setValues((current) => ({ ...current, [field]: value }))

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
      if (error instanceof ApiError && error.status === 400) setErrors(error.errors as AddressErrors)
      setFormError(error instanceof ApiError ? error.message : GENERIC_ERROR)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <FormField id="address-label" label="Etiqueta (opcional, por ejemplo Casa)" value={values.label} onChange={(e) => set('label')(e.target.value)} error={errors.label} />
      <FormField id="address-street" label="Dirección (calle y número)" value={values.street} onChange={(e) => set('street')(e.target.value)} error={errors.street} />
      <FormField id="address-district" label="Distrito" value={values.district} onChange={(e) => set('district')(e.target.value)} error={errors.district} hint="Solo repartimos en los distritos cubiertos." />
      <FormField id="address-reference" label="Referencia (opcional)" value={values.reference} onChange={(e) => set('reference')(e.target.value)} error={errors.reference} />
      <FormField id="address-phone" label="Teléfono de contacto (opcional)" inputMode="tel" value={values.contactPhone} onChange={(e) => set('contactPhone')(e.target.value)} error={errors.contactPhone} />
      {formError && <p className="auth-alert" role="alert">{formError}</p>}
      <div className="restaurant-actions">
        <button type="submit" className="auth-submit" disabled={submitting}>{submitting ? 'Guardando…' : submitLabel}</button>
        <button type="button" className="auth-submit auth-submit-secondary" onClick={onCancel} disabled={submitting}>Cancelar</button>
      </div>
    </form>
  )
}
