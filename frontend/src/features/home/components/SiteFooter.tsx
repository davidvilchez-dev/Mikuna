import { homeAssets } from '../home-assets'
import type { ShowComingSoon } from '../home-types'
import { PublicIcon } from '../../public-pages/components/PublicIcon'
import { FoodImage } from './FoodImage'

export function SiteFooter({ onComingSoon }: { onComingSoon: ShowComingSoon }) {
  return (
    <footer className="site-footer">
      <div className="public-shell footer-grid">
        <div className="footer-brand">
          <a href="#inicio" aria-label="Mikuna, volver al inicio"><FoodImage asset={homeAssets.logo} className="footer-logo" /></a>
          <p>De nuestras cocinas<br />a tu mesa.</p>
          <span className="footer-city"><PublicIcon name="pin" /> Hecho para Ayacucho</span>
        </div>
        <nav aria-label="Explora Mikuna"><h2>Descubre</h2><a href="#inicio">Inicio</a><a href="#sobre-nosotros">Sobre nosotros</a><a href="#como-funciona">Cómo funciona</a></nav>
        <nav aria-label="Únete a Mikuna"><h2>Hagamos equipo</h2><a href="#para-restaurantes">Para restaurantes</a><a href="#se-repartidor">Sé repartidor</a><button type="button" onClick={(event) => onComingSoon(event.currentTarget)}>Crear cuenta</button></nav>
        <div className="footer-note"><span className="public-eyebrow">DE AQUÍ, PARA TI</span><h2>Ayacucho tiene<br />mucho sabor.</h2><p>Descubre tu próximo favorito<br />con Mikuna.</p></div>
      </div>
      <div className="public-shell footer-bottom"><span>© {new Date().getFullYear()} Mikuna</span><span>Tu antojo, en camino.</span><span>Ayacucho, Perú</span></div>
    </footer>
  )
}
