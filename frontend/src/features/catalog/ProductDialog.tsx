import { useEffect, useRef, useState } from 'react'
import { formatPrice } from './catalog-validation'
import { parseDescription } from './product-description'
import type { PublicProduct } from './catalog-api'
import '../auth/address-dialog.css'

/** Máximo de unidades por producto que acepta el carrito. */
const MAX_QUANTITY = 20

interface Props {
  product: PublicProduct
  canAdd: boolean
  onAdd: (quantity: number) => void
  onClose: () => void
}

/** Detalle de un plato: foto a la izquierda; a la derecha, descripción, cantidad y total. */
export function ProductDialog({ product, canAdd, onAdd, onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [quantity, setQuantity] = useState(1)
  const { summary, items } = parseDescription(product.description)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  return (
    <dialog ref={dialogRef} className="addr-dialog product-dialog" aria-labelledby="product-dialog-title" onClose={onClose}
      onClick={(event) => { if (event.target === dialogRef.current) dialogRef.current?.close() }}>
      <div className="product-dialog-photo">
        <img src={product.imageUrl} alt={product.name} />
      </div>
      <div className="product-dialog-body">
        <button type="button" className="product-dialog-close" onClick={() => dialogRef.current?.close()} aria-label="Cerrar">
          <svg className="app-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>
        </button>
        <p className="product-dialog-category">{product.category}</p>
        <h2 id="product-dialog-title">{product.name}</h2>
        {summary && <p className="product-dialog-summary">{summary}</p>}
        <p className="product-dialog-price">{formatPrice(product.price)}</p>
        {items.length > 0 && (
          <section className="product-dialog-contains" aria-labelledby="product-dialog-includes">
            <h3 id="product-dialog-includes">Contiene</h3>
            <ul className="product-dialog-list">
              {items.map((item) => <li key={item}>{item}</li>)}
            </ul>
          </section>
        )}

        <div className="product-dialog-buy">
          <div className="product-dialog-qty">
            <span id="product-dialog-qty-label">Cantidad</span>
            <div className="product-dialog-stepper" role="group" aria-labelledby="product-dialog-qty-label">
              <button type="button" aria-label="Quitar una unidad" disabled={quantity <= 1} onClick={() => setQuantity((n) => Math.max(1, n - 1))}>−</button>
              <output aria-live="polite">{quantity}</output>
              <button type="button" className="is-plus" aria-label="Agregar una unidad" disabled={quantity >= MAX_QUANTITY} onClick={() => setQuantity((n) => Math.min(MAX_QUANTITY, n + 1))}>+</button>
            </div>
          </div>
          <div className="product-dialog-total">
            <span>Total</span>
            <strong>{formatPrice(product.price * quantity)}</strong>
          </div>
          <button type="button" className="auth-submit" disabled={!canAdd} onClick={() => { onAdd(quantity); dialogRef.current?.close() }}>Agregar al pedido</button>
        </div>
      </div>
    </dialog>
  )
}
