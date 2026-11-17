import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { FormField } from '../auth/FormField'
import type { ProductData } from './catalog-api'
import { validateImageFile, validateProductInput, type ProductErrors } from './catalog-validation'

interface ProductFormProps {
  initial: ProductData
  /** En edición la imagen es opcional (si no se elige otra, se conserva la actual). */
  currentImageUrl?: string
  submitLabel: string
  /** Debe lanzar ApiError si la API rechaza; el formulario muestra los errores por campo. */
  onSubmit: (data: ProductData, image: File | null) => Promise<void>
  onCancel: () => void
}

/** Alta y edición de un producto con vista previa de la imagen (RF-08). */
export function ProductForm({ initial, currentImageUrl, submitLabel, onSubmit, onCancel }: ProductFormProps) {
  const [values, setValues] = useState(initial)
  const [image, setImage] = useState<File | null>(null)
  const [errors, setErrors] = useState<ProductErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  // La URL temporal de la vista previa se libera cuando cambia la imagen o se cierra el formulario.
  const preview = useMemo(() => (image ? URL.createObjectURL(image) : null), [image])
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  const set = (field: keyof ProductData) => (value: string) => setValues((current) => ({ ...current, [field]: value }))
  const shownImage = preview ?? currentImageUrl

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setFormError(null)
    const localErrors = validateProductInput(values)
    const needsImage = currentImageUrl === undefined
    const imageError = image || needsImage ? validateImageFile(image) : undefined
    if (imageError) localErrors.image = imageError
    setErrors(localErrors)
    if (Object.keys(localErrors).length > 0) return

    setSubmitting(true)
    try {
      await onSubmit(values, image)
    } catch (error) {
      if (error instanceof ApiError && (error.status === 400 || error.status === 413)) setErrors(error.errors as ProductErrors)
      setFormError(error instanceof ApiError ? error.message : GENERIC_ERROR)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <FormField id="product-name" label="Nombre del producto" value={values.name} onChange={(e) => set('name')(e.target.value)} error={errors.name} />
      <FormField id="product-category" label="Categoría (por ejemplo, Entradas o Bebidas)" value={values.category} onChange={(e) => set('category')(e.target.value)} error={errors.category} />
      <div className="restaurant-field-textarea">
        <label htmlFor="product-description">Descripción breve (opcional)</label>
        <textarea id="product-description" value={values.description} onChange={(e) => set('description')(e.target.value)} aria-invalid={errors.description ? true : undefined} />
        {errors.description && <p className="auth-error">{errors.description}</p>}
      </div>
      <FormField id="product-price" label="Precio en soles (S/)" inputMode="decimal" value={values.price} onChange={(e) => set('price')(e.target.value)} error={errors.price} hint="Hasta 2 decimales, por ejemplo 18.50" />
      <div className="auth-field">
        <label htmlFor="product-image">{currentImageUrl ? 'Cambiar imagen (opcional)' : 'Imagen del producto'}</label>
        <input id="product-image" type="file" accept="image/jpeg,image/png,image/webp" aria-invalid={errors.image ? true : undefined}
          onChange={(e) => setImage(e.target.files?.[0] ?? null)} />
        <p className="auth-hint">JPG, PNG o WebP de hasta 2 MB.</p>
        {errors.image && <p className="auth-error">{errors.image}</p>}
        {shownImage && <img className="catalog-preview" src={shownImage} alt="Vista previa del producto" />}
      </div>
      {formError && <p className="auth-alert" role="alert">{formError}</p>}
      <div className="restaurant-actions">
        <button type="submit" className="auth-submit" disabled={submitting}>{submitting ? 'Guardando…' : submitLabel}</button>
        <button type="button" className="auth-submit auth-submit-secondary" onClick={onCancel} disabled={submitting}>Cancelar</button>
      </div>
    </form>
  )
}
