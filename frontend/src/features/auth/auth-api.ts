export type Role = 'CUSTOMER' | 'RESTAURANT' | 'COURIER' | 'ADMIN'
export type PublicRole = Exclude<Role, 'ADMIN'>
export type AccountStatus = 'ENABLED' | 'DISABLED'

export interface AccountResponse {
  id: string
  name: string
  email: string
  role: Role
  status: AccountStatus
}

export interface LoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  account: AccountResponse
}

export interface RegisterData {
  name: string
  email: string
  password: string
  passwordConfirmation: string
  role: PublicRole
}

export const GENERIC_ERROR = 'No se pudo completar la operación, inténtalo de nuevo'

/** Error normalizado de la API; status 0 indica que no hubo respuesta (red caída). */
export class ApiError extends Error {
  readonly status: number
  readonly errors: Record<string, string>

  constructor(status: number, detail: string, errors: Record<string, string> = {}) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.errors = errors
  }
}

/** Llamada JSON a la API con errores normalizados; la comparten las funcionalidades con sesión. */
export async function apiRequest<T>(url: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, {
      ...init,
      headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...init.headers },
    })
  } catch {
    throw new ApiError(0, GENERIC_ERROR)
  }

  if (response.ok) return (await response.json()) as T

  // Solo los ProblemDetail del backend se leen; el resto (p. ej. el 502 en texto del proxy de Vite) es genérico.
  const contentType = response.headers.get('Content-Type') ?? ''
  if (!contentType.includes('application/problem+json')) throw new ApiError(response.status, GENERIC_ERROR)

  try {
    const problem = (await response.json()) as { detail?: string; errors?: Record<string, string> }
    throw new ApiError(response.status, problem.detail ?? GENERIC_ERROR, problem.errors ?? {})
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(response.status, GENERIC_ERROR)
  }
}

function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  return apiRequest<T>('/api/auth' + path, init)
}

export function register(data: RegisterData): Promise<AccountResponse> {
  return request<AccountResponse>('/register', { method: 'POST', body: JSON.stringify(data) })
}

export function login(email: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/login', { method: 'POST', body: JSON.stringify({ email, password }) })
}

export function fetchMe(token: string): Promise<AccountResponse> {
  return request<AccountResponse>('/me', { headers: { Authorization: 'Bearer ' + token } })
}
