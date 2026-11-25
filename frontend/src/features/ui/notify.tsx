import { toast, type TypeOptions } from 'react-toastify'

/** Duración corta: el aviso confirma la acción y se va solo. */
const SHORT = 2600
const LONG = 5000

/**
 * Un solo aviso por mensaje: si el mismo texto ya está en pantalla (p. ej. la persona repite la acción),
 * no se apila otro; se reinicia el tiempo del que ya se ve.
 */
function show(type: TypeOptions, message: string, autoClose: number) {
  const toastId = `${type}:${message}`
  if (toast.isActive(toastId)) {
    toast.update(toastId, { autoClose, progress: undefined })
    return
  }
  toast(message, { type, autoClose, toastId })
}

export function notifySuccess(message: string) {
  show('success', message, SHORT)
}

export function notifyError(message: string) {
  show('error', message, LONG)
}

/** Confirmación breve de «agregado al carrito». */
export function notifyAddedToCart(productName: string, quantity = 1) {
  show('success', quantity > 1 ? `${quantity} × ${productName} agregados al carrito` : `${productName} agregado al carrito`, SHORT)
}
