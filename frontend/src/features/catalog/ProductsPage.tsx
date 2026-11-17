import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AuthLayout } from '../auth/AuthLayout'
import { clearSession, getSession } from '../auth/session-storage'
import {
  createProduct,
  listMyProducts,
  setProductAvailability,
  updateProduct,
  withdrawProduct,
  type PageResponse,
  type Product,
  type ProductData,
} from './catalog-api'
import { formatPrice } from './catalog-validation'
import { ProductForm } from './ProductForm'
import '../restaurant/restaurant.css'
import './catalog.css'

type ViewState =
  | { kind: 'loading' }
  | { kind: 'ready'; data: PageResponse<Product> }
  | { kind: 'forbidden'; message: string }
  | { kind: 'error'; message: string }

type Mode = { kind: 'list' } | { kind: 'create' } | { kind: 'edit'; product: Product }

const emptyData: ProductData = { name: '', category: '', description: '', price: '' }

function toData(product: Product): ProductData {
  return { name: product.name, category: product.category, description: product.description ?? '', price: product.price.toFixed(2) }
}

/** Gestión del catálogo propio del restaurante aprobado (RF-08). */
export function ProductsPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [mode, setMode] = useState<Mode>({ kind: 'list' })
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const token = session?.token ?? ''

  /** 401: la sesión no vale; otros errores se muestran. Devuelve el mensaje para el usuario. */
  const handleFailure = useCallback((error: unknown): string => {
    if (error instanceof ApiError && error.status === 401) {
      clearSession()
      navigate('/iniciar-sesion', { replace: true })
      return 'Tu sesión venció'
    }
    return error instanceof ApiError ? error.message : GENERIC_ERROR
  }, [navigate])

  useEffect(() => {
    if (!token) return
    let active = true
    listMyProducts(token, page)
      .then((data) => { if (active) setState({ kind: 'ready', data }) })
      .catch((error: unknown) => {
        if (!active) return
        if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
          setState({ kind: 'forbidden', message: error.status === 404 ? 'Primero envía la solicitud de tu restaurante.' : error.message })
        } else {
          setState({ kind: 'error', message: handleFailure(error) })
        }
      })
    return () => { active = false }
  }, [token, page, attempt, handleFailure])

  if (!session) return <Navigate to="/iniciar-sesion" replace />

  const reload = (message: string | null) => {
    setNotice(message)
    setMode({ kind: 'list' })
    setState({ kind: 'loading' })
    setAttempt((n) => n + 1)
  }

  async function run(action: () => Promise<unknown>, success: string) {
    if (busy) return
    setBusy(true)
    try {
      await action()
      reload(success)
    } catch (error) {
      const message = handleFailure(error)
      // 409: otra edición cambió el producto; se recarga para ver la versión vigente.
      if (error instanceof ApiError && error.status === 409) reload(message)
      else setNotice(message)
    } finally {
      setBusy(false)
    }
  }

  if (state.kind === 'loading') {
    return <AuthLayout title="Mi catálogo" wide><p className="auth-description" role="status">Cargando tus productos…</p></AuthLayout>
  }

  if (state.kind === 'forbidden') {
    return (
      <AuthLayout title="Mi catálogo" wide>
        <p className="auth-alert" role="alert">{state.message || 'Tu restaurante debe estar aprobado para gestionar productos.'}</p>
        <Link to="/mi-restaurante" className="auth-submit auth-submit-secondary">Ir a mi restaurante</Link>
      </AuthLayout>
    )
  }

  if (state.kind === 'error') {
    return (
      <AuthLayout title="Mi catálogo" wide>
        <p className="auth-alert" role="alert">{state.message}</p>
        <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
      </AuthLayout>
    )
  }

  if (mode.kind === 'create') {
    return (
      <AuthLayout title="Nuevo producto" description="Se publica como disponible apenas lo guardes.">
        <ProductForm
          initial={emptyData}
          submitLabel="Guardar producto"
          onCancel={() => setMode({ kind: 'list' })}
          onSubmit={async (data, image) => {
            try {
              await createProduct(token, data, image as File)
              reload('Producto creado')
            } catch (error) {
              if (error instanceof ApiError && error.status === 401) handleFailure(error)
              throw error
            }
          }}
        />
      </AuthLayout>
    )
  }

  if (mode.kind === 'edit') {
    const { product } = mode
    return (
      <AuthLayout title={'Editar: ' + product.name}>
        <ProductForm
          initial={toData(product)}
          currentImageUrl={product.imageUrl}
          submitLabel="Guardar cambios"
          onCancel={() => setMode({ kind: 'list' })}
          onSubmit={async (data, image) => {
            try {
              await updateProduct(token, product.id, product.version, data, image)
              reload('Cambios guardados')
            } catch (error) {
              if (error instanceof ApiError && error.status === 401) handleFailure(error)
              // 409: el producto cambió; se muestra el aviso y se recarga la lista con la versión vigente.
              if (error instanceof ApiError && error.status === 409) {
                reload(error.message)
                return
              }
              throw error
            }
          }}
        />
      </AuthLayout>
    )
  }

  const { data } = state
  return (
    <AuthLayout title="Mi catálogo" description="Los clientes solo ven los productos disponibles." wide>
      {notice && <p className="auth-notice" role="status">{notice}</p>}
      <div className="restaurant-actions">
        <button type="button" className="auth-submit" onClick={() => { setNotice(null); setMode({ kind: 'create' }) }}>Nuevo producto</button>
        <Link to="/mi-restaurante" className="auth-submit auth-submit-secondary">Mi restaurante</Link>
      </div>

      {data.items.length === 0 ? (
        <p className="auth-description">Aún no tienes productos. Crea el primero para que los clientes lo vean.</p>
      ) : (
        <ul className="restaurant-list">
          {data.items.map((product) => (
            <li className="restaurant-card catalog-item" key={product.id}>
              <img className="catalog-thumb" src={product.imageUrl} alt={product.name} loading="lazy" />
              <div className="catalog-item-body">
                <h2>{product.name}</h2>
                <p className="catalog-meta">{product.category} · {formatPrice(product.price)}</p>
                {product.description && <p>{product.description}</p>}
                <p>
                  <span className={product.available ? 'restaurant-badge restaurant-badge-ok' : 'restaurant-badge restaurant-badge-bad'}>
                    {product.available ? 'Disponible' : 'No disponible'}
                  </span>
                </p>
                <div className="restaurant-actions">
                  <button type="button" className="auth-submit auth-submit-secondary" disabled={busy}
                    onClick={() => { setNotice(null); setMode({ kind: 'edit', product }) }}>Editar</button>
                  <button type="button" className="auth-submit auth-submit-secondary" disabled={busy}
                    onClick={() => run(() => setProductAvailability(token, product.id, !product.available, product.version),
                      product.available ? 'Producto marcado como no disponible' : 'Producto marcado como disponible')}>
                    {product.available ? 'Marcar no disponible' : 'Marcar disponible'}
                  </button>
                  <button type="button" className="auth-submit auth-submit-secondary" disabled={busy}
                    onClick={() => { if (window.confirm(`¿Retirar «${product.name}»? Esta acción no se puede deshacer.`)) void run(() => withdrawProduct(token, product.id, product.version), 'Producto retirado') }}>
                    Retirar
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {data.totalPages > 1 && (
        <nav className="catalog-pager" aria-label="Paginación">
          <button type="button" className="auth-submit auth-submit-secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button>
          <span>Página {page + 1} de {data.totalPages}</span>
          <button type="button" className="auth-submit auth-submit-secondary" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Siguiente</button>
        </nav>
      )}
    </AuthLayout>
  )
}
