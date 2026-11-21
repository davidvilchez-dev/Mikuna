import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import {
  attentionLabels,
  getFilters,
  searchRestaurants,
  type Filters,
  type PageResponse,
  type PublicRestaurant,
} from './catalog-api'
import './catalog.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; data: PageResponse<PublicRestaurant> }
  | { kind: 'error'; message: string }

/** Búsqueda pública de restaurantes por nombre, categoría y zona (RF-09); no requiere sesión. */
export function RestaurantSearchPage() {
  const [params, setParams] = useSearchParams()
  const name = params.get('q') ?? ''
  const category = params.get('categoria') ?? ''
  const zone = params.get('zona') ?? ''
  const page = Math.max(0, (Number(params.get('pagina')) || 1) - 1)

  const [nameInput, setNameInput] = useState(name)
  const [filters, setFilters] = useState<Filters>({ categories: [], zones: [] })
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    getFilters().then((loaded) => { if (active) setFilters(loaded) }).catch(() => { /* los filtros son opcionales */ })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    searchRestaurants({ name, category, zone, page })
      .then((data) => { if (active) setState({ kind: 'ready', data }) })
      .catch((error: unknown) => {
        if (active) setState({ kind: 'error', message: error instanceof ApiError ? error.message : GENERIC_ERROR })
      })
    return () => { active = false }
  }, [name, category, zone, page, attempt])

  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries({ pagina: '', ...changes })) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setState({ kind: 'loading' })
    setParams(next)
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    update({ q: nameInput.trim() })
  }

  const clear = () => {
    setNameInput('')
    setState({ kind: 'loading' })
    setParams(new URLSearchParams())
  }

  const hasCriteria = Boolean(name || category || zone)

  return (
    <AuthLayout title="Restaurantes" description="Busca por nombre, categoría o zona. No necesitas iniciar sesión." wide>
      <form className="catalog-search" onSubmit={submit} role="search">
        <div className="auth-field">
          <label htmlFor="search-name">Nombre</label>
          <input id="search-name" type="search" value={nameInput} maxLength={100} onChange={(e) => setNameInput(e.target.value)} placeholder="Por ejemplo, pollería" />
        </div>
        <div className="auth-field">
          <label htmlFor="search-category">Categoría</label>
          <select id="search-category" value={category} onChange={(e) => update({ categoria: e.target.value })}>
            <option value="">Todas</option>
            {filters.categories.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
        </div>
        <div className="auth-field">
          <label htmlFor="search-zone">Zona</label>
          <select id="search-zone" value={zone} onChange={(e) => update({ zona: e.target.value })}>
            <option value="">Todas</option>
            {filters.zones.map((option) => <option key={option} value={option}>{option}</option>)}
          </select>
        </div>
        <button type="submit" className="auth-submit">Buscar</button>
      </form>

      {state.kind === 'loading' && <p className="auth-description" role="status">Buscando restaurantes…</p>}

      {state.kind === 'error' && (
        <>
          <p className="auth-alert" role="alert">{state.message}</p>
          <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
        </>
      )}

      {state.kind === 'ready' && state.data.items.length === 0 && (
        <>
          <p className="auth-notice" role="status">
            {hasCriteria ? 'No encontramos restaurantes con esos criterios.' : 'Todavía no hay restaurantes disponibles.'}
          </p>
          {hasCriteria && <button type="button" className="auth-submit auth-submit-secondary" onClick={clear}>Limpiar búsqueda</button>}
        </>
      )}

      {state.kind === 'ready' && state.data.items.length > 0 && (
        <>
          <ul className="catalog-results">
            {state.data.items.map((restaurant) => (
              <li className={'catalog-result' + (restaurant.attention === 'OPEN' ? '' : ' catalog-result-closed')} key={restaurant.id}>
                <div className="catalog-cover">
                  {restaurant.coverUrl
                    ? <img src={restaurant.coverUrl} alt="" loading="lazy" />
                    : <span className="catalog-cover-empty" aria-hidden="true">{restaurant.name.charAt(0)}</span>}
                  <span className={'catalog-attention catalog-attention-' + restaurant.attention.toLowerCase()}>{attentionLabels[restaurant.attention]}</span>
                </div>
                <div className="catalog-result-row">
                  <span className="catalog-logo" aria-hidden="true">
                    {restaurant.logoUrl ? <img src={restaurant.logoUrl} alt="" loading="lazy" /> : restaurant.name.charAt(0)}
                  </span>
                  <div className="catalog-result-text">
                    <Link to={'/restaurantes/' + restaurant.id}>
                      <h2>{restaurant.name}</h2>
                    </Link>
                    <p>{restaurant.category} · {restaurant.zone}</p>
                  </div>
                </div>
                {restaurant.description && <p className="catalog-result-desc">{restaurant.description}</p>}
              </li>
            ))}
          </ul>
          {state.data.totalPages > 1 && (
            <nav className="catalog-pager" aria-label="Paginación">
              <button type="button" className="auth-submit auth-submit-secondary" disabled={page === 0} onClick={() => update({ pagina: String(page) })}>Anterior</button>
              <span>Página {page + 1} de {state.data.totalPages}</span>
              <button type="button" className="auth-submit auth-submit-secondary" disabled={page + 1 >= state.data.totalPages} onClick={() => update({ pagina: String(page + 2) })}>Siguiente</button>
            </nav>
          )}
        </>
      )}
    </AuthLayout>
  )
}
