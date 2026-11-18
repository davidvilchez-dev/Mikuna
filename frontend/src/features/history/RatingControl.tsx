import { useState } from 'react'
import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { RATING_VALUES, isValidRating, ratingErrorMessage } from './history-logic'
import { rateOrder } from './history-api'

interface RatingControlProps {
  token: string
  orderId: string
  onRated: (value: number) => void
}

/** Cinco botones de 1 a 5 para calificar un pedido entregado (RF-31); un solo envío a la vez. */
export function RatingControl({ token, orderId, onRated }: RatingControlProps) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function submit(value: number) {
    if (busy || !isValidRating(value)) return
    setBusy(true)
    setMessage(null)
    try {
      await rateOrder(token, orderId, value)
      onRated(value)
    } catch (error) {
      if (error instanceof ApiError) {
        setMessage(ratingErrorMessage(error.status, error.code, error.message))
        if (error.code === 'RATING_ALREADY_EXISTS') onRated(-1)
      } else {
        setMessage(GENERIC_ERROR)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="rating-control" role="group" aria-label="Calificar el pedido de 1 a 5">
      <span>Califica tu pedido:</span>
      {RATING_VALUES.map((value) => (
        <button key={value} type="button" className="auth-submit auth-submit-secondary" disabled={busy}
          aria-label={`${value} de 5`} onClick={() => void submit(value)}>
          {value}
        </button>
      ))}
      {message && <p className="auth-alert" role="alert">{message}</p>}
    </div>
  )
}
