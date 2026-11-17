import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError, GENERIC_ERROR, register, type AccountResponse, type PublicRole } from './auth-api'
import { publicRoleOptions, roleLabels } from './auth-labels'
import { roleFromQuery, validateRegistration, type FieldErrors, type RegistrationForm } from './auth-validation'
import { AuthLayout } from './AuthLayout'
import { FormField } from './FormField'

export function RegisterPage() {
  const [searchParams] = useSearchParams()
  const [form, setForm] = useState<RegistrationForm>(() => ({
    name: '', email: '', password: '', passwordConfirmation: '', role: roleFromQuery(searchParams.get('tipo')),
  }))
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [created, setCreated] = useState<AccountResponse | null>(null)

  const update = (field: keyof RegistrationForm) => (value: string) => {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setFormError(null)
    const localErrors = validateRegistration(form)
    setErrors(localErrors)
    if (Object.keys(localErrors).length > 0) return

    setSubmitting(true)
    try {
      // validateRegistration ya garantizó que el rol es público.
      const account = await register({ ...form, role: form.role as PublicRole })
      setCreated(account)
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.errors as FieldErrors)
        setFormError(error.message)
      } else {
        setFormError(GENERIC_ERROR)
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (created) {
    return (
      <AuthLayout title="¡Cuenta creada!" description={`Tu cuenta de ${roleLabels[created.role].toLowerCase()} está lista.`}>
        <div className="auth-success" role="status">
          <p>Registramos <strong>{created.email}</strong>. Ya puedes iniciar sesión con tu correo y contraseña.</p>
          {created.role === 'RESTAURANT' && (
            <p className="auth-notice">Tu habilitación comercial está pendiente. Inicia sesión y envía la solicitud de tu restaurante; podrás recibir pedidos cuando un administrador la apruebe.</p>
          )}
        </div>
        <Link to="/iniciar-sesion" className="auth-submit">Iniciar sesión</Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Crea tu cuenta" description="Elige cómo quieres usar Mikuna.">
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <fieldset className="auth-roles" aria-describedby={errors.role ? 'role-error' : undefined}>
          <legend>Tipo de cuenta</legend>
          {publicRoleOptions.map((option) => (
            <label key={option.value} className="auth-role">
              <input type="radio" name="role" value={option.value} checked={form.role === option.value} onChange={(event) => update('role')(event.target.value)} />
              <span><strong>{option.label}</strong><small>{option.hint}</small></span>
            </label>
          ))}
          {errors.role && <p id="role-error" className="auth-error">{errors.role}</p>}
        </fieldset>

        <FormField id="name" label="Nombre del titular" autoComplete="name" maxLength={120} value={form.name} onChange={(event) => update('name')(event.target.value)} error={errors.name} />
        <FormField id="email" label="Correo electrónico" type="email" autoComplete="email" value={form.email} onChange={(event) => update('email')(event.target.value)} error={errors.email} />
        <FormField id="password" label="Contraseña" type="password" autoComplete="new-password" hint="Hasta 128 caracteres." value={form.password} onChange={(event) => update('password')(event.target.value)} error={errors.password} />
        <FormField id="passwordConfirmation" label="Confirma la contraseña" type="password" autoComplete="new-password" value={form.passwordConfirmation} onChange={(event) => update('passwordConfirmation')(event.target.value)} error={errors.passwordConfirmation} />

        {formError && <p className="auth-alert" role="alert">{formError}</p>}

        <button type="submit" className="auth-submit" disabled={submitting}>
          {submitting ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
        <p className="auth-switch">¿Ya tienes cuenta? <Link to="/iniciar-sesion">Inicia sesión</Link></p>
      </form>
    </AuthLayout>
  )
}
