import { Link } from 'react-router'
import { restaurantFeatures, publicPhotos } from './public-content'
import { PublicHero } from './components/PublicHero'
import { FeatureSection } from './components/FeatureSection'

export function ForRestaurantsPage() {
  return (
    <>
      <PublicHero variant="restaurant" title={<>Tu cocina, <span>a más mesas.</span></>} description="Lleva el sabor de tu restaurante a más personas con Mikuna." photo={publicPhotos.chef}>
        <Link className="public-button" to="/registro?tipo=restaurante">Registrar mi restaurante</Link>
        <Link className="public-text-button" to="/iniciar-sesion">Ya tengo cuenta</Link>
      </PublicHero>
      <FeatureSection title="Todo para gestionar tus pedidos" items={restaurantFeatures} variant="restaurant" />
      <section className="restaurant-onboarding" aria-labelledby="onboarding-title">
        <div className="public-shell onboarding-grid">
          <div><span className="public-eyebrow">SÚMATE A MIKUNA</span><h3 id="onboarding-title">Empieza en tres pasos</h3></div>
          <div>
            <ol className="onboarding-steps">
              {['Envía tu solicitud', 'Espera la revisión', 'Publica tu carta'].map((label, index) => (
                <li key={label}><span className="step-number">{index + 1}</span><span>{label}</span></li>
              ))}
            </ol>
            <p>Tu restaurante se habilita para recibir pedidos después de la aprobación.</p>
          </div>
        </div>
      </section>
    </>
  )
}
