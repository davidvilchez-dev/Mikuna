import { useEffect, useRef, type ReactNode } from 'react'
import './modal.css'

interface ModalProps {
  title: string
  onClose: () => void
  children: ReactNode
}

/** Ventana modal con <dialog> nativo: foco atrapado, Escape y clic fuera para cerrar. */
export function Modal({ title, onClose, children }: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  return (
    <dialog
      ref={ref}
      className="mk-modal"
      aria-labelledby="mk-modal-title"
      onClose={onClose}
      onClick={(event) => { if (event.target === ref.current) ref.current?.close() }}
    >
      <div className="mk-modal-inner">
        <header className="mk-modal-head">
          <h2 id="mk-modal-title">{title}</h2>
          <button type="button" className="mk-modal-close" aria-label="Cerrar" onClick={() => ref.current?.close()}>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" /></svg>
          </button>
        </header>
        {children}
      </div>
    </dialog>
  )
}
