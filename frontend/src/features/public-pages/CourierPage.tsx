import { Link } from 'react-router'
import { courierFeatures, publicPhotos } from './public-content'
import { PublicHero } from './components/PublicHero'
import { FeatureSection } from './components/FeatureSection'
import { PublicIcon } from './components/PublicIcon'

export function CourierPage() {
  return (
    <>
      <PublicHero variant="courier" title={<>Tu próxima entrega <span>empieza aquí.</span></>} description="Conecta con pedidos de tu ciudad y gestiona tus entregas con Mikuna." photo={publicPhotos.courier}>
        <Link className="public-button" to="/registro?tipo=repartidor">Quiero ser repartidor</Link>
        <Link className="public-text-button" to="/iniciar-sesion">Ya tengo cuenta</Link>
      </PublicHero>
      <FeatureSection title="Tus entregas, en un solo lugar" items={courierFeatures} variant="courier" />
      <section className="public-shell courier-cta" aria-labelledby="courier-cta-title">
        <div className="courier-route" aria-hidden="true">
          <span className="route-point"><PublicIcon name="store" /></span>
          <span className="route-line" />
          <span className="route-scooter"><PublicIcon name="scooter" /></span>
          <span className="route-line" />
          <span className="route-point"><PublicIcon name="pin" /></span>
          <span className="route-caption">AYACUCHO, PERÚ</span>
        </div>
        <div className="courier-cta-copy">
          <span className="public-eyebrow">CADA ENTREGA CONECTA</span>
          <h3 id="courier-cta-title">Tu ciudad. Tu recorrido.<br /><span>Reparte con Mikuna.</span></h3>
          <p>Acerca las cocinas de Ayacucho a quienes las disfrutan.</p>
          <Link className="courier-join" to="/registro?tipo=repartidor">Quiero sumarme</Link>
        </div>
      </section>
    </>
  )
}
