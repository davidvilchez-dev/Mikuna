import { publicPhotos } from './public-content'
import { PublicIcon } from './components/PublicIcon'

export function AboutSection() {
  return (
    <>
      <div className="public-shell about-grid">
        <div className="about-copy">
          <span className="public-eyebrow">SOBRE NOSOTROS</span>
          <h2 id="about-title">El sabor de Ayacucho,<span> más cerca.</span></h2>
          <p>Mikuna es una propuesta de delivery que conecta a las personas con las cocinas de su ciudad.</p>
          <p>Unimos a quienes preparan, a quienes reparten y a quienes disfrutan cada pedido. Porque detrás de un buen antojo, hay toda una ciudad.</p>
          <a href="#como-funciona" className="about-link">Conoce cómo funciona</a>
        </div>
        <div className="about-photos">
          <figure className="about-photo-main"><img src={publicPhotos.chef.src} alt={publicPhotos.chef.alt} width="1672" height="941" loading="lazy" decoding="async" /><figcaption>El sabor empieza en la cocina</figcaption></figure>
          <figure><img src={publicPhotos.courier.src} alt={publicPhotos.courier.alt} width="1672" height="941" loading="lazy" decoding="async" /></figure>
          <figure><img src={publicPhotos.customer.src} alt={publicPhotos.customer.alt} width="1672" height="941" loading="lazy" decoding="async" /></figure>
        </div>
      </div>
      <div className="about-values">
        <div className="public-shell about-values-grid">
          <div><PublicIcon name="store" /><h3>De nuestra ciudad</h3><p>Un espacio para descubrir la cocina de Ayacucho.</p></div>
          <div><PublicIcon name="bag" /><h3>Para cada antojo</h3><p>Tus sabores favoritos, reunidos en un solo lugar.</p></div>
          <div><PublicIcon name="scooter" /><h3>Juntos, más cerca</h3><p>Restaurantes, repartidores y clientes conectados.</p></div>
        </div>
      </div>
    </>
  )
}
