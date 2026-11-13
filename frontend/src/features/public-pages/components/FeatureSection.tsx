import type { FeatureItem } from '../public-content'
import { PublicIcon } from './PublicIcon'

export function FeatureSection({ title, items, numbered = false, variant = 'default' }: {
  title: string
  items: readonly FeatureItem[]
  numbered?: boolean
  variant?: 'default' | 'restaurant' | 'courier'
}) {
  return (
    <section className={'public-shell feature-section feature-section-' + variant} aria-labelledby={'feature-title-' + variant}>
      <h3 id={'feature-title-' + variant}>{title}</h3>
      <ol className={'feature-grid' + (numbered ? ' feature-grid-steps' : '')}>
        {items.map((item, index) => (
          <li key={item.title} className="feature-card">
            <div className="feature-icon"><PublicIcon name={item.icon} /></div>
            <div className="feature-copy">
              {numbered && <span className="step-number" aria-label={'Paso ' + (index + 1)}>{index + 1}</span>}
              <h4>{item.title}</h4>
              <p>{item.description}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  )
}
