import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR, login } from './auth-api'
import { validateLogin } from './auth-validation'
import { AuthSplitLayout } from './AuthSplitLayout'
import { FormField } from './FormField'
import { saveSession } from './session-storage'

export function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    setFormError(null)
    const localErrors = validateLogin({ email, password })
    setErrors(localErrors)
    if (Object.keys(localErrors).length > 0) return

    setSubmitting(true)
    try {
      const result = await login(email, password)
      saveSession({ token: result.accessToken, expiresAt: Date.parse(result.expiresAt), role: result.account.role, name: result.account.name })
      navigate('/mi-cuenta', { replace: true })
    } catch (error) {
      setFormError(error instanceof ApiError ? error.message : GENERIC_ERROR)
      setSubmitting(false)
    }
  }

  return (
    <AuthSplitLayout visual="login" imageLeft title="Te damos la bienvenida" description="Inicia sesión con tu correo y contraseña.">
      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <FormField id="email" label="Correo electrónico" type="email" icon="mail" placeholder="ejemplo@correo.com" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} error={errors.email} />
        <FormField id="password" label="Contraseña" type="password" icon="lock" placeholder="••••••••" revealable autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} error={errors.password} />

        {formError && <p className="auth-alert" role="alert">{formError}</p>}

        <button type="submit" className="auth-submit" disabled={submitting}>
          {submitting ? 'Ingresando…' : 'Iniciar sesión'}
        </button>
        <p className="auth-switch">¿Aún no tienes cuenta? <Link to="/registro">Crea una</Link></p>
      </form>
    </AuthSplitLayout>
  )
}
