import { Link } from 'react-router'
import { publicPhotos } from '../../public-pages/public-content'

export function PartnerBanners() {
  return (
    <section className="partner-banners" aria-label="Únete a Mikuna">
      <div className="content-shell partner-grid">
        <article className="partner-card">
          <img src={publicPhotos.chef.src} alt="" loading="lazy" />
          <div>
            <h2>¿Tienes un restaurante?</h2>
            <p>Llega a más clientes de Ayacucho con Mikuna.</p>
            <Link to="/para-restaurantes">Registra tu restaurante</Link>
          </div>
        </article>
        <article className="partner-card partner-card-alt">
          <img src={publicPhotos.courier.src} alt="" loading="lazy" />
          <div>
            <h2>Reparte con nosotros</h2>
            <p>Elige tus horarios y gana por cada entrega.</p>
            <Link to="/se-repartidor">Quiero ser repartidor</Link>
          </div>
        </article>
      </div>
    </section>
  )
}
