import type { InputHTMLAttributes } from 'react'

interface FormFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  id: string
  label: string
  error?: string
  hint?: string
}

/** Campo con etiqueta y mensaje de error asociado para lectores de pantalla. */
export function FormField({ id, label, error, hint, ...inputProps }: FormFieldProps) {
  const describedBy = [hint ? id + '-hint' : null, error ? id + '-error' : null].filter(Boolean).join(' ') || undefined
  return (
    <div className="auth-field">
      <label htmlFor={id}>{label}</label>
      <input id={id} aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...inputProps} />
      {hint && <p id={id + '-hint'} className="auth-hint">{hint}</p>}
      {error && <p id={id + '-error'} className="auth-error">{error}</p>}
    </div>
  )
}
