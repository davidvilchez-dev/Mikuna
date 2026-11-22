import { useCallback, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { AppIcon } from '../auth/AppIcon'
import { notifyError, notifySuccess } from '../ui/notify'
import { RestaurantLayout } from '../restaurant/RestaurantLayout'
import { Modal } from '../ui/Modal'
import { clearSession, getSession } from '../auth/session-storage'
import { getBranchId } from '../restaurant/branch-storage'
import { listBranches, type Restaurant } from '../restaurant/restaurant-api'
import {
  copyMenuFrom,
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

/** Ofrece copiar la carta de otra sucursal aprobada de la misma cuenta a la sucursal actual. */
function CopyMenu({ token, onCopied, onFailure }: { token: string; onCopied: (message: string) => void; onFailure: (error: unknown) => string }) {
  const [others, setOthers] = useState<Restaurant[]>([])
  const [source, setSource] = useState('')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    listBranches(token).then((all) => {
      if (!active) return
      const currentId = getBranchId() ?? all[0]?.id
      const list = all.filter((branch) => branch.id !== currentId && branch.status === 'APPROVED')
      setOthers(list)
      setSource(list[0]?.id ?? '')
    }).catch(() => { /* sin sucursales la opción no aparece */ })
    return () => { active = false }
  }, [token])

  if (others.length === 0) return null

  async function copy() {
    if (busy || !source) return
    setBusy(true)
    try {
      const result = await copyMenuFrom(token, source)
      onCopied(result.copied === 0
        ? 'No había productos nuevos para copiar'
        : `Se copiaron ${result.copied} ${result.copied === 1 ? 'producto' : 'productos'}` + (result.skipped > 0 ? ` (${result.skipped} ya existían)` : ''))
    } catch (error) {
      notifyError(onFailure(error))
    } finally {
      setBusy(false)
    }
  }

  if (!open) return <button type="button" className="auth-submit auth-submit-secondary" onClick={() => setOpen(true)}>Copiar carta de otra sucursal</button>
  return (
    <div className="rs-copy">
      <label htmlFor="copy-source">Copiar desde</label>
      <select id="copy-source" value={source} onChange={(e) => setSource(e.target.value)} disabled={busy}>
        {others.map((branch) => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
      </select>
      <button type="button" className="auth-submit" onClick={() => void copy()} disabled={busy}>{busy ? 'Copiando…' : 'Copiar'}</button>
      <button type="button" className="auth-submit auth-submit-secondary" onClick={() => setOpen(false)} disabled={busy}>Cancelar</button>
    </div>
  )
}

/** Gestión del catálogo propio del restaurante aprobado (RF-08). */
export function ProductsPage() {
  const navigate = useNavigate()
  const [session] = useState(() => getSession())
  const [state, setState] = useState<ViewState>({ kind: 'loading' })
  const [mode, setMode] = useState<Mode>({ kind: 'list' })
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
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

  const reload = (message: string | null, isError = false) => {
    if (message) (isError ? notifyError : notifySuccess)(message)
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
      if (error instanceof ApiError && error.status === 409) reload(message, true)
      else notifyError(message)
    } finally {
      setBusy(false)
    }
  }

  if (state.kind === 'loading') {
    return <RestaurantLayout title="Mi catálogo" wide><p className="auth-description" role="status">Cargando tus productos…</p></RestaurantLayout>
  }

  if (state.kind === 'forbidden') {
    return (
      <RestaurantLayout title="Mi catálogo" wide>
        <p className="auth-alert" role="alert">{state.message || 'Tu restaurante debe estar aprobado para gestionar productos.'}</p>
        <Link to="/mi-restaurante" className="auth-submit auth-submit-secondary">Ir a mi restaurante</Link>
      </RestaurantLayout>
    )
  }

  if (state.kind === 'error') {
    return (
      <RestaurantLayout title="Mi catálogo" wide>
        <p className="auth-alert" role="alert">{state.message}</p>
        <button type="button" className="auth-submit" onClick={() => { setState({ kind: 'loading' }); setAttempt((n) => n + 1) }}>Reintentar</button>
      </RestaurantLayout>
    )
  }

  const { data } = state
  return (
    <RestaurantLayout title="Mi carta" description="Los clientes solo ven los productos disponibles." wide>
      <div className="rs-toolbar">
        <span>{data.totalItems} {data.totalItems === 1 ? 'producto' : 'productos'}</span>
        <div className="rs-toolbar-actions">
          <CopyMenu token={token} onCopied={(message) => reload(message)} onFailure={handleFailure} />
          <button type="button" className="auth-submit" onClick={() => setMode({ kind: 'create' })}>+ Nuevo producto</button>
        </div>
      </div>

      {data.items.length === 0 ? (
        <div className="rs-empty">
          <span className="rs-empty-icon"><AppIcon name="box" /></span>
          <strong>Aún no tienes productos</strong>
          <p>Crea el primero para que los clientes lo vean en tu restaurante.</p>
        </div>
      ) : (
        <ul className="rs-products">
          {data.items.map((product) => (
            <li className={product.available ? 'rs-product' : 'rs-product rs-product-off'} key={product.id}>
              <div className="rs-product-photo">
                <img src={product.imageUrl} alt={product.name} loading="lazy" />
                {!product.available && <span className="rs-product-tag">No disponible</span>}
              </div>
              <div className="rs-product-body">
                <small>{product.category}</small>
                <h2>{product.name}</h2>
                {product.description && <p>{product.description}</p>}
                <strong className="rs-price">{formatPrice(product.price)}</strong>
              </div>
              <div className="rs-product-foot">
                <label className="rs-toggle">
                  <button type="button" role="switch" aria-checked={product.available} className="rs-switch rs-switch-sm" disabled={busy}
                    onClick={() => run(() => setProductAvailability(token, product.id, !product.available, product.version),
                      product.available ? 'Producto marcado como no disponible' : 'Producto marcado como disponible')}>
                    <span className="rs-switch-knob" />
                  </button>
                  {product.available ? 'Disponible' : 'Agotado'}
                </label>
                <button type="button" className="rs-link" disabled={busy}
                  onClick={() => setMode({ kind: 'edit', product })}>Editar</button>
                <button type="button" className="rs-link rs-link-danger" disabled={busy}
                  onClick={() => { if (window.confirm(`¿Retirar «${product.name}»? Esta acción no se puede deshacer.`)) void run(() => withdrawProduct(token, product.id, product.version), 'Producto retirado') }}>
                  Retirar
                </button>
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
      {mode.kind === 'create' && (
        <Modal title="Nuevo producto" onClose={() => setMode({ kind: 'list' })}>
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
        </Modal>
      )}
      {mode.kind === 'edit' && (
        <Modal title={'Editar: ' + mode.product.name} onClose={() => setMode({ kind: 'list' })}>
          <ProductForm
            initial={toData(mode.product)}
            currentImageUrl={mode.product.imageUrl}
            submitLabel="Guardar cambios"
            onCancel={() => setMode({ kind: 'list' })}
            onSubmit={async (data, image) => {
              const product = mode.product
              try {
                await updateProduct(token, product.id, product.version, data, image)
                reload('Cambios guardados')
              } catch (error) {
                if (error instanceof ApiError && error.status === 401) handleFailure(error)
                // 409: el producto cambió; se muestra el aviso y se recarga la lista con la versión vigente.
                if (error instanceof ApiError && error.status === 409) {
                  reload(error.message, true)
                  return
                }
                throw error
              }
            }}
          />
        </Modal>
      )}
    </RestaurantLayout>
  )
}
