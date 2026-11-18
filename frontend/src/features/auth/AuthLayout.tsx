import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { homeAssets } from '../home/home-assets'
import { FoodImage } from '../home/components/FoodImage'
import { NotificationsBell } from '../notifications/NotificationsBell'
import './auth.css'

interface AuthLayoutProps {
  title: string
  description?: string
  children: ReactNode
  /** Tarjeta ancha para listas y tablas. */
  wide?: boolean
}

/** Marco común de las pantallas de cuenta, con regreso al inicio público. */
export function AuthLayout({ title, description, children, wide }: AuthLayoutProps) {
  return (
    <div className="auth-page">
      <header className="auth-header">
        <Link to="/" className="auth-brand" aria-label="Mikuna, volver al inicio">
          <FoodImage asset={homeAssets.logo} className="auth-logo" priority />
        </Link>
        <div className="auth-header-links">
          <NotificationsBell />
          <Link to="/" className="auth-back">Volver al inicio</Link>
        </div>
      </header>
      <main className="auth-main">
        <section className={wide ? 'auth-card auth-card-wide' : 'auth-card'} aria-labelledby="auth-title">
          <h1 id="auth-title">{title}</h1>
          {description && <p className="auth-description">{description}</p>}
          {children}
        </section>
      </main>
    </div>
  )
}
