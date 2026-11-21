import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { notifyAddedToCart, notifyError } from '../ui/notify'
import { clearSession, getSession } from '../auth/session-storage'
import { addToCart, clearCart } from '../cart/cart-api'
import { attentionLabels, getCatalog, type Catalog, type PublicProduct } from './catalog-api'
import { formatPrice } from './catalog-validation'
import { ProductDialog } from './ProductDialog'
import { parseDescription } from './product-description'
import '../cart/cart.css'
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
  const navigate = useNavigate()
  const [selected, setSelected] = useState<PublicProduct | null>(null)
  const [adding, setAdding] = useState(false)
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

  /** Agrega unidades al carrito (RF-11). Sin sesión lleva a iniciar sesión; el backend valida rol y elegibilidad. */
  async function add(product: PublicProduct, quantity = 1) {
    const session = getSession()
    if (!session) {
      navigate('/iniciar-sesion')
      return
    }
    if (adding) return
    setAdding(true)
    try {
      try {
        await addToCart(session.token, product.id, quantity)
      } catch (error) {
        if (!(error instanceof ApiError && error.code === 'CART_OTHER_RESTAURANT')) throw error
        if (!window.confirm('Tu carrito es de otro restaurante. ¿Vaciarlo y agregar este producto?')) return
        await clearCart(session.token)
        await addToCart(session.token, product.id, quantity)
      }
      notifyAddedToCart(product.name, quantity)
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        clearSession()
        navigate('/iniciar-sesion')
      } else if (error instanceof ApiError && error.status === 403) {
        notifyError('Solo las cuentas de cliente pueden agregar productos al carrito.')
      } else {
        notifyError(error instanceof ApiError ? error.message : GENERIC_ERROR)
      }
    } finally {
      setAdding(false)
    }
  }

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
    <AuthLayout
      title={restaurant.name}
      description={`${restaurant.category} · ${restaurant.zone}`}
      wide
      hero={(
        <div className="catalog-hero">
          {restaurant.coverUrl
            ? <img className="catalog-hero-cover" src={restaurant.coverUrl} alt="" />
            : <div className="catalog-hero-cover catalog-hero-empty" aria-hidden="true" />}
          <div className="catalog-hero-row">
            <span className="catalog-logo catalog-logo-lg" aria-hidden="true">
              {restaurant.logoUrl ? <img src={restaurant.logoUrl} alt="" /> : restaurant.name.charAt(0)}
            </span>
            <span className={'catalog-attention catalog-attention-' + restaurant.attention.toLowerCase()}>{attentionLabels[restaurant.attention]}</span>
          </div>
        </div>
      )}
    >
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
                  <button type="button" className="catalog-product-open" onClick={() => setSelected(product)} aria-label={`Ver detalle de ${product.name}`}>
                    <img src={product.imageUrl} alt="" loading="lazy" />
                    <span className="catalog-product-text">
                      <h3>{product.name}</h3>
                      {parseDescription(product.description).summary && <span className="catalog-product-summary">{parseDescription(product.description).summary}</span>}
                      <span className="catalog-price">{formatPrice(product.price)}</span>
                    </span>
                  </button>
                  <button type="button" className="auth-submit cart-add" disabled={adding || notAttending} onClick={() => add(product)}>Agregar al carrito</button>
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

      {selected && (
        <ProductDialog product={selected} canAdd={!adding && !notAttending} onAdd={(quantity) => add(selected, quantity)} onClose={() => setSelected(null)} />
      )}

      <Link to="/restaurantes" className="auth-switch">Volver a la búsqueda</Link>
    </AuthLayout>
  )
}
