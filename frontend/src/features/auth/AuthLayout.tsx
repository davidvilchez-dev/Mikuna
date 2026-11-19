import type { ReactNode } from 'react'
import { AppHeader } from './AppHeader'
import './auth.css'
import './app-shell.css'

interface AuthLayoutProps {
  title: string
  description?: string
  children: ReactNode
  /** Contenido ancho para listas, tablas y cuadrículas; sin él, una tarjeta centrada. */
  wide?: boolean
}

/** Marco común de las pantallas con sesión: encabezado de la aplicación y contenido con su título. */
export function AuthLayout({ title, description, children, wide }: AuthLayoutProps) {
  return (
    <div className="app-shell">
      <AppHeader />
      <main className="app-main">
        <div className={wide ? 'app-container app-container-wide' : 'app-container'}>
          <section aria-labelledby="auth-title" className={wide ? 'app-section' : 'app-section app-card'}>
            <div className="app-title">
              <h1 id="auth-title">{title}</h1>
              {description && <p className="auth-description">{description}</p>}
            </div>
            {children}
          </section>
        </div>
      </main>
    </div>
  )
}
