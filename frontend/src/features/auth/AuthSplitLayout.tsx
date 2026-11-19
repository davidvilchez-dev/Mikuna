import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { homeAssets } from '../home/home-assets'
import { FoodImage } from '../home/components/FoodImage'
import logoColor from '../../assets/branding/logo-mikuna.svg'
import customerImage from '../../assets/auth/customer-phone.jpg'
import loginImage from '../../assets/public-pages/customer.png'
import courierImage from '../../assets/public-pages/courier.png'
import chefImage from '../../assets/public-pages/restaurant-chef.png'
import './auth.css'
import './auth-split.css'

export type AuthVisual = 'login' | 'customer' | 'restaurant' | 'courier'

const visuals: Record<AuthVisual, { src: string; position: string; eyebrow: string; title: string; text: string }> = {
  login: {
    src: loginImage,
    position: '45% center',
    eyebrow: 'Para clientes',
    title: 'Tu comida favorita, sin salir de casa.',
    text: 'Pide a los restaurantes de tu ciudad y sigue tu pedido hasta la puerta.',
  },
  customer: {
    src: customerImage,
    position: '45% center',
    eyebrow: 'Para clientes',
    title: 'Tu comida favorita, sin salir de casa.',
    text: 'Pide a los restaurantes de tu ciudad y sigue tu pedido hasta la puerta.',
  },
  restaurant: {
    src: chefImage,
    position: '58% center',
    eyebrow: 'Para restaurantes',
    title: 'Lleva tu cocina a más mesas.',
    text: 'Publica tu carta, recibe pedidos y prepáralos; Mikuna se encarga de la entrega.',
  },
  courier: {
    src: courierImage,
    position: '60% center',
    eyebrow: 'Para repartidores',
    title: 'Genera ingresos entregando pedidos.',
    text: 'Activa tu disponibilidad cuando quieras y recibe entregas cerca de ti.',
  },
}

interface AuthSplitLayoutProps {
  title: string
  description?: string
  visual: AuthVisual
  /** Pone la foto a la izquierda y el formulario a la derecha. */
  imageLeft?: boolean
  children: ReactNode
}

/** Pantalla dividida para iniciar sesión y registrarse: formulario a la izquierda, foto a la derecha. */
export function AuthSplitLayout({ title, description, visual, imageLeft, children }: AuthSplitLayoutProps) {
  const current = visuals[visual]
  return (
    <div className={imageLeft ? 'auth-split auth-split-reverse' : 'auth-split'}>
      <div className="auth-split-panel">
        <header className="auth-split-top">
          <Link to="/" className="auth-brand" aria-label="Mikuna, volver al inicio">
            <FoodImage asset={homeAssets.logo} className="auth-split-logo" priority />
          </Link>
          <Link to="/" className="auth-split-back">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 6l-6 6 6 6" /></svg>
            Volver al inicio
          </Link>
        </header>
        <main className="auth-split-main">
          <section className="auth-split-content" aria-labelledby="auth-title">
            <h1 id="auth-title">{title}</h1>
            {description && <p className="auth-split-description">{description}</p>}
            {children}
          </section>
        </main>
        <footer className="auth-split-foot">© {new Date().getFullYear()} Mikuna · Delivery de tu ciudad</footer>
      </div>
      <aside className="auth-split-visual">
        <img className="auth-split-photo" key={visual} src={current.src} alt="" style={{ objectPosition: current.position }} />
        {imageLeft && (
          <Link to="/" className="auth-split-visual-brand" aria-label="Mikuna, volver al inicio">
            <img src={logoColor} alt="Mikuna" width={3922} height={815} />
          </Link>
        )}
        <div className="auth-split-caption" key={visual + '-caption'}>
          <span>{current.eyebrow}</span>
          <p className="auth-split-caption-title">{current.title}</p>
          <p>{current.text}</p>
        </div>
      </aside>
    </div>
  )
}
