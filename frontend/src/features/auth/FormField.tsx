import { useState, type InputHTMLAttributes } from 'react'

type FieldIcon = 'mail' | 'lock' | 'user'

const ICON_PATHS: Record<FieldIcon, string> = {
  mail: 'M4 6h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1zM3.5 7.5 12 13l8.5-5.5',
  lock: 'M6 11h12a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1zM8 11V8a4 4 0 0 1 8 0v3',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4.5 20a7.5 7.5 0 0 1 15 0',
}

interface FormFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  id: string
  label: string
  error?: string
  hint?: string
  /** Icono decorativo a la izquierda del campo. */
  icon?: FieldIcon
  /** Muestra un botón para ver u ocultar la contraseña. */
  revealable?: boolean
}

/** Campo con etiqueta y mensaje de error asociado para lectores de pantalla. */
export function FormField({ id, label, error, hint, icon, revealable, type, ...inputProps }: FormFieldProps) {
  const [visible, setVisible] = useState(false)
  const describedBy = [hint ? id + '-hint' : null, error ? id + '-error' : null].filter(Boolean).join(' ') || undefined
  const input = (
    <input
      id={id}
      type={revealable && visible ? 'text' : type}
      aria-invalid={error ? true : undefined}
      aria-describedby={describedBy}
      {...inputProps}
    />
  )
  const decorated = icon || revealable
  return (
    <div className="auth-field">
      <label htmlFor={id}>{label}</label>
      {decorated ? (
        <div className={icon ? 'auth-input-wrap auth-input-icon' : 'auth-input-wrap'}>
          {icon && (
            <svg className="auth-field-icon" viewBox="0 0 24 24" aria-hidden="true"><path d={ICON_PATHS[icon]} /></svg>
          )}
          {input}
          {revealable && (
            <button
              type="button"
              className="auth-reveal"
              onClick={() => setVisible((value) => !value)}
              aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
              aria-pressed={visible}
              aria-controls={id}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                {visible
                  ? <path d="M3 3l18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.1A10 10 0 0 1 12 5c6 0 9.5 7 9.5 7a17 17 0 0 1-3 3.9M6.6 6.6C3.9 8.4 2.5 12 2.5 12S6 19 12 19a9.6 9.6 0 0 0 5.4-1.6" />
                  : <path d="M2.5 12S6 5 12 5s9.5 7 9.5 7-3.5 7-9.5 7-9.5-7-9.5-7zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z" />}
              </svg>
            </button>
          )}
        </div>
      ) : input}
      {hint && <p id={id + '-hint'} className="auth-hint">{hint}</p>}
      {error && <p id={id + '-error'} className="auth-error">{error}</p>}
    </div>
  )
}
