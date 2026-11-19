import { useEffect, useRef } from 'react'
import { ComingSoonNotice } from '../home/components/ComingSoonNotice'
import { SiteFooter } from '../home/components/SiteFooter'
import { SiteHeader } from '../home/components/SiteHeader'
import { useComingSoonNotice } from '../home/useComingSoonNotice'
import { AboutSection } from './AboutSection'
import { CourierPage } from './CourierPage'
import { ForRestaurantsPage } from './ForRestaurantsPage'
import { HowItWorksPage } from './HowItWorksPage'
import './public-pages.css'
import './continuous-home.css'

const pages = {
  'como-funciona': { className: 'how-panel', labelledBy: 'public-title-customer', render: () => <HowItWorksPage /> },
  'para-restaurantes': { className: 'partners-panel', labelledBy: 'public-title-restaurant', render: () => <ForRestaurantsPage /> },
  'se-repartidor': { className: 'courier-panel', labelledBy: 'public-title-courier', render: () => <CourierPage /> },
  'sobre-nosotros': { className: 'about-panel', labelledBy: 'about-title', render: () => <AboutSection /> },
} as const

export function PublicInfoPage({ page }: { page: keyof typeof pages }) {
  const menuToggleRef = useRef<HTMLButtonElement | null>(null)
  const notice = useComingSoonNotice(menuToggleRef)
  const mainRef = useRef<HTMLElement | null>(null)
  useEffect(() => { window.scrollTo(0, 0) }, [page])
  const config = pages[page]
  return (
    <div className="continuous-site">
      <a className="skip-to-content" href="#contenido" onClick={(event) => { event.preventDefault(); mainRef.current?.focus() }}>Saltar al contenido</a>
      <SiteHeader menuToggleRef={menuToggleRef} />
      <main id="contenido" ref={mainRef} tabIndex={-1} className="public-main">
        <section id={page} className={'screen-panel ' + (page === 'sobre-nosotros' ? '' : 'info-panel ') + config.className} aria-labelledby={config.labelledBy}>
          {config.render()}
        </section>
      </main>
      <SiteFooter />
      <ComingSoonNotice visible={notice.visible} revision={notice.revision} triggerRef={notice.triggerRef} onPause={notice.pause} onResume={notice.resume} onDismiss={notice.dismiss} />
    </div>
  )
}
