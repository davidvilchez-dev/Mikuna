import { heroActions, heroContent } from '../home-content'
import type { ShowComingSoon } from '../home-types'
import { HeroFoodComposition } from './HeroFoodComposition'

export function HeroSection({ onComingSoon }: { onComingSoon: ShowComingSoon }) {
  return (
    <section id="presentacion" className="hero-section bg-mikuna-ivory" aria-labelledby="titulo-principal">
      <div className="hero-shell">
        <div className="hero-copy" data-hero-copy>
          <h1 id="titulo-principal" data-keep-clear>
            <span>Tu antojo,</span>{' '}<span className="text-mikuna-orange">en camino.</span>
          </h1>
          <p className="hero-description text-mikuna-gray" data-description data-keep-clear>{heroContent.description}</p>
          <div className="hero-actions">
            {heroActions.map((action) => (
              <button
                key={action.id}
                type="button"
                className={action.appearance === 'primary' ? 'hero-action action-primary bg-mikuna-orange text-mikuna-white' : 'hero-action action-secondary text-mikuna-indigo'}
                data-action={action.id}
                onClick={(event) => onComingSoon(event.currentTarget)}
              >
                <span data-keep-clear>{action.label}</span>
                {action.appearance === 'primary' && (
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M4 12h16m-7-7 7 7-7 7" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
              </button>
            ))}
          </div>
        </div>
        <HeroFoodComposition />
      </div>
    </section>
  )
}
