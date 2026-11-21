import { useEffect, useMemo, useRef, useState } from 'react'
import { buildSlotDays } from './checkout-options'
import '../auth/address-dialog.css'

interface Props {
  /** Franja elegida (ISO) o cadena vacía si es entrega inmediata. */
  value: string
  onConfirm: (value: string) => void
  onClose: () => void
}

/** Panel «Tipos de entrega»: inmediata o programada en una franja de una hora. */
export function DeliveryTimeDialog({ value, onConfirm, onClose }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const days = useMemo(() => buildSlotDays(new Date()), [])
  const [scheduled, setScheduled] = useState(value !== '')
  const [selected, setSelected] = useState(value)

  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  const canConfirm = !scheduled || selected !== ''

  return (
    <dialog ref={dialogRef} className="addr-dialog delivery-dialog" aria-labelledby="delivery-dialog-title" onClose={onClose}
      onClick={(event) => { if (event.target === dialogRef.current) dialogRef.current?.close() }}>
      <div className="addr-dialog-inner">
        <h2 id="delivery-dialog-title">Tipos de entrega</h2>

        <div className="delivery-types" role="radiogroup" aria-label="Tipo de entrega">
          <label className="delivery-type">
            <span aria-hidden="true">🔥</span><strong>Inmediata</strong>
            <input type="radio" name="delivery-type" checked={!scheduled} onChange={() => setScheduled(false)} />
          </label>
          <label className="delivery-type">
            <span aria-hidden="true">⏰</span><strong>Programado</strong>
            <input type="radio" name="delivery-type" checked={scheduled} onChange={() => setScheduled(true)} />
          </label>
        </div>

        {scheduled && (
          <div className="delivery-days">
            {days.length === 0 && <p className="auth-notice" role="status">No hay horarios disponibles por ahora.</p>}
            {days.map((day) => (
              <section key={day.key}>
                <h3><span>{day.title}</span><em>{day.short}</em></h3>
                <div className="delivery-slots">
                  {day.slots.map((slot) => (
                    <button type="button" key={slot.value} className={'delivery-slot' + (selected === slot.value ? ' is-selected' : '')}
                      aria-pressed={selected === slot.value} onClick={() => setSelected(slot.value)}>{slot.label}</button>
                  ))}
                </div>
              </section>
            ))}
          </div>
        )}

        <div className="delivery-actions">
          <button type="button" className="auth-submit" disabled={!canConfirm}
            onClick={() => { onConfirm(scheduled ? selected : ''); dialogRef.current?.close() }}>Confirmar</button>
          <button type="button" className="auth-submit auth-submit-secondary" onClick={() => dialogRef.current?.close()}>Regresar</button>
        </div>
      </div>
    </dialog>
  )
}
