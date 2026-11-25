const SESSION_EXPIRED = 'mikuna:session-expired'

/** Avisa a toda la aplicación de que el servidor rechazó la sesión (401 con token). */
export function notifySessionExpired(): void {
  window.dispatchEvent(new Event(SESSION_EXPIRED))
}

/** Suscribe a la sesión vencida; devuelve la función para cancelar la suscripción. */
export function onSessionExpired(listener: () => void): () => void {
  window.addEventListener(SESSION_EXPIRED, listener)
  return () => window.removeEventListener(SESSION_EXPIRED, listener)
}
