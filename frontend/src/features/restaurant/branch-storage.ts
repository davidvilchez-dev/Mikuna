const BRANCH_KEY = 'mikuna.branch'

/** Sucursal con la que trabaja el panel; sin ella el servidor usa la más antigua de la cuenta. */
export function getBranchId(): string | null {
  try {
    return window.sessionStorage.getItem(BRANCH_KEY)
  } catch {
    return null
  }
}

export function setBranchId(id: string | null): void {
  try {
    if (id) window.sessionStorage.setItem(BRANCH_KEY, id)
    else window.sessionStorage.removeItem(BRANCH_KEY)
  } catch {
    // Sin almacenamiento, el panel vuelve a la sucursal principal al recargar.
  }
}

/** Cabeceras de autenticación del panel del restaurante: token y sucursal elegida. */
export function panelHeaders(token: string): Record<string, string> {
  const branch = getBranchId()
  return { Authorization: 'Bearer ' + token, ...(branch ? { 'X-Restaurant-Id': branch } : {}) }
}
