import type { ShowComingSoon } from '../home/home-types'
import { restaurantFeatures, publicPhotos } from './public-content'
import { PublicHero } from './components/PublicHero'
import { FeatureSection } from './components/FeatureSection'

export function ForRestaurantsPage({ onComingSoon }: { onComingSoon: ShowComingSoon }) {
  return (
    <>
      <PublicHero variant="restaurant" title={<>Tu cocina, <span>a más mesas.</span></>} description="Lleva el sabor de tu restaurante a más personas con Mikuna." photo={publicPhotos.chef}>
        <button className="public-button" type="button" onClick={(event) => onComingSoon(event.currentTarget)}>Registrar mi restaurante</button>
        <button className="public-text-button" type="button" onClick={(event) => onComingSoon(event.currentTarget)}>Ya tengo cuenta</button>
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
