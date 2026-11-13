import type { ReactNode } from 'react'

interface PublicHeroProps {
  title: ReactNode
  description: string
  photo: { readonly src: string; readonly alt: string }
  children: ReactNode
  variant?: 'customer' | 'restaurant' | 'courier'
}

export function PublicHero({ title, description, photo, children, variant = 'customer' }: PublicHeroProps) {
  return (
    <div className={'public-hero public-hero-' + variant}>
      <div className="public-shell public-hero-grid">
        <div className="public-hero-copy">
          <h2 id={'public-title-' + variant}>{title}</h2>
          <p>{description}</p>
          <div className="public-hero-actions">{children}</div>
        </div>
        <div className="public-photo-frame">
          <img src={photo.src} alt={photo.alt} width="1672" height="941" fetchPriority="high" decoding="async" />
        </div>
      </div>
    </div>
  )
}
