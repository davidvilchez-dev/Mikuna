import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { CART_CHANGED, getCart } from '../cart/cart-api'
import { getSession } from '../auth/session-storage'
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

  const [filters, setFilters] = useState<Filters>({ categories: [], zones: [] })
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  // Punto de entrega del cliente: undefined mientras se consulta; null si no hay sesión o dirección con mapa.
  const [point, setPoint] = useState<{ latitude: number; longitude: number } | null | undefined>(
    () => (getSession()?.role === 'CUSTOMER' ? undefined : null),
  )

  useEffect(() => {
    const session = getSession()
    if (!session || session.role !== 'CUSTOMER') return
    let active = true
    const load = () => {
      getCart(session.token)
        .then((cart) => {
          if (!active) return
          const a = cart.address
          setPoint(a && a.latitude != null && a.longitude != null ? { latitude: a.latitude, longitude: a.longitude } : null)
        })
        .catch(() => { if (active) setPoint(null) })
    }
    load()
    window.addEventListener(CART_CHANGED, load)
    return () => { active = false; window.removeEventListener(CART_CHANGED, load) }
  }, [])

  useEffect(() => {
    let active = true
    getFilters().then((loaded) => { if (active) setFilters(loaded) }).catch(() => { /* los filtros son opcionales */ })
    return () => { active = false }
  }, [])

  const pointKey = point ? `${point.latitude},${point.longitude}` : ''
  useEffect(() => {
    if (point === undefined) return
    let active = true
    searchRestaurants({ name, category, zone, page, point })
      .then((data) => { if (active) setState({ kind: 'ready', data }) })
      .catch((error: unknown) => {
        if (active) setState({ kind: 'error', message: error instanceof ApiError ? error.message : GENERIC_ERROR })
      })
    return () => { active = false }
    // point se resume en pointKey para no repetir la consulta si el objeto cambia pero el punto no.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [name, category, zone, page, attempt, pointKey, point === undefined])

  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries({ pagina: '', ...changes })) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    setState({ kind: 'loading' })
    setParams(next)
  }

  const clear = () => {
    setState({ kind: 'loading' })
    setParams(new URLSearchParams())
  }

  const hasCriteria = Boolean(name || category || zone)

  return (
    <AuthLayout title="Restaurantes" description="Elige una categoría o usa el buscador de arriba." wide>
      {name && (
        <p className="catalog-query" role="status">
          Resultados para <strong>“{name}”</strong>
          <button type="button" className="catalog-query-clear" onClick={() => update({ q: '' })}>Quitar</button>
        </p>
      )}
      {filters.categories.length > 0 && (
        <div className="catalog-chips" role="group" aria-label="Categorías">
          <button type="button" className="catalog-chip" aria-pressed={!category} onClick={() => update({ categoria: '' })}>Todas</button>
          {filters.categories.map((option) => (
            <button type="button" key={option} className="catalog-chip" aria-pressed={category === option} onClick={() => update({ categoria: option })}>{option}</button>
          ))}
        </div>
      )}

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
                    <p>{[restaurant.category, restaurant.distanceKm != null ? `a ${restaurant.distanceKm.toLocaleString('es-PE')} km` : restaurant.zone].join(' · ')}</p>
                    {restaurant.branchCount > 1 && <small className="catalog-branches">{restaurant.branchCount} sucursales</small>}
                  </div>
                </div>
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
