import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { attentionLabels, getCatalog, type Catalog, type PublicProduct } from './catalog-api'
import { formatPrice } from './catalog-validation'
import './catalog.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; catalog: Catalog }
  | { kind: 'notFound' }
  | { kind: 'error'; message: string }

/** Agrupa productos consecutivos de la misma categoría (la API ya los entrega ordenados por categoría). */
function groupByCategory(products: PublicProduct[]): { category: string; items: PublicProduct[] }[] {
  const groups: { category: string; items: PublicProduct[] }[] = []
  for (const product of products) {
    const last = groups[groups.length - 1]
    if (last && last.category === product.category) last.items.push(product)
    else groups.push({ category: product.category, items: [product] })
  }
  return groups
}

/** Catálogo público de un restaurante visible (RF-10): solo productos disponibles; no requiere sesión. */
export function RestaurantCatalogPage() {
  const { id = '' } = useParams()
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<ViewState>({ kind: 'loading' })

  useEffect(() => {
    let active = true
    getCatalog(id, page)
      .then((catalog) => { if (active) setState({ kind: 'ready', catalog }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && (error.status === 404 || error.status === 400)) setState({ kind: 'notFound' })
        else setState({ kind: 'error', message: error instanceof ApiError ? error.message : GENERIC_ERROR })
      })
    return () => { active = false }
  }, [id, page, attempt])

  if (state.kind === 'loading') {
    return <AuthLayout title="Restaurante" wide><p className="auth-description" role="status">Cargando el catálogo…</p></AuthLayout>
  }

  if (state.kind === 'notFound') {
    return (
      <AuthLayout title="Restaurante no encontrado" wide>
        <p className="auth-notice" role="status">Este restaurante no existe o no está disponible en Mikuna.</p>
        <Link to="/restaurantes" className="auth-submit">Buscar restaurantes</Link>
      </AuthLayout>
    )
  }

  if (state.kind === 'error') {
    return (
      <AuthLayout title="Restaurante" wide>
        <p className="auth-alert" role="alert">{state.message}</p>
        <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
      </AuthLayout>
    )
  }

  const { restaurant, products } = state.catalog
  const groups = groupByCategory(products.items)
  const notAttending = restaurant.attention !== 'OPEN'

  return (
    <AuthLayout title={restaurant.name} description={`${restaurant.category} · ${restaurant.zone}`} wide>
      <p><span className={'catalog-attention catalog-attention-' + restaurant.attention.toLowerCase()}>{attentionLabels[restaurant.attention]}</span></p>
      {restaurant.description && <p className="auth-description">{restaurant.description}</p>}
      {notAttending && (
        <p className="catalog-banner" role="status">
          {restaurant.attention === 'PAUSED'
            ? 'Sin atención por ahora: este restaurante no recibe pedidos en este momento.'
            : 'Fuera de horario: este restaurante no recibe pedidos en este momento.'}
        </p>
      )}

      {products.items.length === 0 ? (
        <p className="auth-notice" role="status">Este restaurante aún no tiene productos disponibles.</p>
      ) : (
        groups.map((group) => (
          <section className="catalog-category" key={group.category} aria-labelledby={'cat-' + group.category}>
            <h2 id={'cat-' + group.category}>{group.category}</h2>
            <ul className="catalog-products">
              {group.items.map((product) => (
                <li className="catalog-product" key={product.id}>
                  <img src={product.imageUrl} alt={product.name} loading="lazy" />
                  <div className="catalog-product-body">
                    <h3>{product.name}</h3>
                    {product.description && <p>{product.description}</p>}
                    <p className="catalog-price">{formatPrice(product.price)}</p>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}

      {products.totalPages > 1 && (
        <nav className="catalog-pager" aria-label="Paginación">
          <button type="button" className="auth-submit auth-submit-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button>
          <span>Página {page + 1} de {products.totalPages}</span>
          <button type="button" className="auth-submit auth-submit-secondary" disabled={page + 1 >= products.totalPages} onClick={() => setPage(page + 1)}>Siguiente</button>
        </nav>
      )}

      <Link to="/restaurantes" className="auth-switch">Volver a la búsqueda</Link>
    </AuthLayout>
  )
}
