import { toast } from 'react-toastify'

/** Duración corta: el aviso confirma la acción y se va solo. */
const SHORT = 2600

export function notifySuccess(message: string) {
  toast.success(message, { autoClose: SHORT })
}

export function notifyError(message: string) {
  toast.error(message, { autoClose: 5000 })
}

/** Confirmación breve de «agregado al carrito». */
export function notifyAddedToCart(productName: string, quantity = 1) {
  toast.success(quantity > 1 ? `${quantity} × ${productName} agregados al carrito` : `${productName} agregado al carrito`, { autoClose: SHORT })
}
