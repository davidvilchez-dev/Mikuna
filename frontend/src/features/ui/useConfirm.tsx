import { useCallback, useRef, useState, type ReactNode } from 'react'
import { Modal } from './Modal'

export interface ConfirmOptions {
  title: string
  message: string
  confirmLabel: string
  cancelLabel?: string
  /** Acción destructiva (bloquear, retirar, vaciar): el botón principal va en rojo. */
  danger?: boolean
}

/**
 * Confirmación con el diálogo de la app en lugar de `window.confirm`: `confirm(opciones)` devuelve una promesa
 * que se resuelve en `true` solo si la persona pulsa el botón principal. El elemento devuelto se pinta en la página.
 */
export function useConfirm(): [ReactNode, (options: ConfirmOptions) => Promise<boolean>] {
  const [options, setOptions] = useState<ConfirmOptions | null>(null)
  const resolver = useRef<((value: boolean) => void) | null>(null)

  const confirm = useCallback((next: ConfirmOptions) => new Promise<boolean>((resolve) => {
    resolver.current?.(false)
    resolver.current = resolve
    setOptions(next)
  }), [])

  const settle = (value: boolean) => {
    resolver.current?.(value)
    resolver.current = null
    setOptions(null)
  }

  const element = options && (
    <Modal title={options.title} onClose={() => settle(false)}>
      <p className="mk-confirm-text">{options.message}</p>
      <div className="mk-confirm-actions">
        <button type="button" className="mk-confirm-btn mk-confirm-cancel" onClick={() => settle(false)}>{options.cancelLabel ?? 'Cancelar'}</button>
        <button type="button" className={options.danger ? 'mk-confirm-btn mk-confirm-danger' : 'mk-confirm-btn mk-confirm-ok'} onClick={() => settle(true)} autoFocus>
          {options.confirmLabel}
        </button>
      </div>
    </Modal>
  )

  return [element, confirm]
}
