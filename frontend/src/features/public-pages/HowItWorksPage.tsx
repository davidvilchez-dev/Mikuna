import { publicPhotos, orderSteps } from './public-content'
import { PublicHero } from './components/PublicHero'
import { FeatureSection } from './components/FeatureSection'
import { PublicIcon } from './components/PublicIcon'
import type { ShowComingSoon } from '../home/home-types'

export function HowItWorksPage({ onComingSoon }: { onComingSoon: ShowComingSoon }) {
  return (
    <>
      <PublicHero title={<>De tu antojo <span>a tu puerta.</span></>} description="Pedir en Mikuna, paso a paso." photo={publicPhotos.customer}>
        <button className="public-button" type="button" onClick={(event) => onComingSoon(event.currentTarget)}>Empezar a pedir</button>
      </PublicHero>
      <FeatureSection title="Así de fácil" items={orderSteps} numbered />
      <section className="public-shell craving-cta" aria-labelledby="craving-title">
        <div className="craving-stamp" aria-hidden="true"><PublicIcon name="bag" /></div>
        <div className="craving-copy">
          <span className="public-eyebrow">EL PLAN DE HOY: COMER RICO</span>
          <h3 id="craving-title">Tu próximo antojo <span>empieza aquí.</span></h3>
          <p>El sabor de Ayacucho, sin salir de casa.</p>
        </div>
        <button className="public-button" type="button" onClick={(event) => onComingSoon(event.currentTarget)}>Empezar a pedir</button>
      </section>
    </>
  )
}
