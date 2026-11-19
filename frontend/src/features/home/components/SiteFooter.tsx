import { Link } from 'react-router'
import logoLight from '../../../assets/branding/logo-mikuna-light.svg'
import { PublicIcon } from '../../public-pages/components/PublicIcon'

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="public-shell footer-grid">
        <div className="footer-brand">
          <Link to="/" aria-label="Mikuna, volver al inicio"><img src={logoLight} alt="Mikuna" width={3922} height={815} className="footer-logo" loading="lazy" /></Link>
          <p>De nuestras cocinas<br />a tu mesa.</p>
          <span className="footer-city"><PublicIcon name="pin" /> Hecho para Ayacucho</span>
        </div>
        <nav aria-label="Explora Mikuna"><h2>Descubre</h2><Link to="/">Inicio</Link><Link to="/restaurantes">Restaurantes</Link><Link to="/sobre-nosotros">Sobre nosotros</Link><Link to="/como-funciona">Cómo funciona</Link></nav>
        <nav aria-label="Únete a Mikuna"><h2>Hagamos equipo</h2><Link to="/para-restaurantes">Para restaurantes</Link><Link to="/se-repartidor">Sé repartidor</Link><Link to="/registro">Crear cuenta</Link><Link to="/iniciar-sesion">Iniciar sesión</Link></nav>
        <div className="footer-note"><h2>Ayacucho tiene<br />mucho sabor.</h2><p>Descubre tu próximo favorito<br />con Mikuna.</p></div>
      </div>
      <div className="public-shell footer-bottom"><span>© {new Date().getFullYear()} Mikuna</span><span>Tu antojo, en camino.</span><span>Ayacucho, Perú</span></div>
    </footer>
  )
}
