import { Link } from 'react-router'
import { heroActions, heroContent, heroHighlights } from '../home-content'
import { PublicIcon } from '../../public-pages/components/PublicIcon'
import { AddressSearch } from './AddressSearch'
import { HeroFoodComposition } from './HeroFoodComposition'

export function HeroSection() {
  const account = heroActions.find((action) => action.behavior === 'route' && action.to)
  return (
    <section id="presentacion" className="hero-section" aria-labelledby="titulo-principal">
      <div className="hero-card">
        <div className="hero-glow" aria-hidden="true" />
        <div className="hero-shell">
          <div className="hero-copy" data-hero-copy>
            <p className="hero-eyebrow">Delivery en Ayacucho</p>
            <h1 id="titulo-principal" data-keep-clear>
              <span>Tu antojo,</span>{' '}<span className="hero-accent">en camino.</span>
            </h1>
            <p className="hero-description" data-description data-keep-clear>{heroContent.description}</p>
            <AddressSearch />
            {account && (
              <p className="hero-account">
                ¿Primera vez en Mikuna?{' '}
                <Link to={account.to!} data-action={account.id}>{account.label}</Link>
              </p>
            )}
          </div>
          <HeroFoodComposition />
        </div>
        <ul className="hero-highlights">
          {heroHighlights.map((item) => (
            <li key={item.title}>
              <span className="highlight-icon"><PublicIcon name={item.icon} /></span>
              <span><strong>{item.title}</strong><small>{item.text}</small></span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}
