import { useRef } from 'react'
import { categories } from './home-content'
import { CategorySection } from './components/CategorySection'
import { ComingSoonNotice } from './components/ComingSoonNotice'
import { HeroSection } from './components/HeroSection'
import { SiteHeader } from './components/SiteHeader'
import { SiteFooter } from './components/SiteFooter'
import { useComingSoonNotice } from './useComingSoonNotice'
import { useSectionNavigation } from '../public-pages/useSectionNavigation'
import { HowItWorksPage } from '../public-pages/HowItWorksPage'
import { ForRestaurantsPage } from '../public-pages/ForRestaurantsPage'
import { CourierPage } from '../public-pages/CourierPage'
import { AboutSection } from '../public-pages/AboutSection'
import '../public-pages/public-pages.css'
import '../public-pages/continuous-home.css'

export function HomePage() {
  const menuToggleRef = useRef<HTMLButtonElement | null>(null)
  const notice = useComingSoonNotice(menuToggleRef)
  const activeSection = useSectionNavigation()
  const mainRef = useRef<HTMLElement | null>(null)

  return (
    <div className="continuous-site">
      <a className="skip-to-content" href="#contenido" onClick={(event) => { event.preventDefault(); mainRef.current?.focus() }}>Saltar al contenido</a>
      <SiteHeader activePage={activeSection} menuToggleRef={menuToggleRef} onComingSoon={notice.show} />
      <main id="contenido" ref={mainRef} tabIndex={-1} className="public-main">
        <section id="inicio" className="screen-panel home-panel" aria-labelledby="titulo-principal" tabIndex={-1}>
          <HeroSection onComingSoon={notice.show} />
          <CategorySection categories={categories} onComingSoon={notice.show} />
        </section>
        <section id="sobre-nosotros" className="screen-panel about-panel" aria-labelledby="about-title" tabIndex={-1}><AboutSection /></section>
        <section id="como-funciona" className="screen-panel info-panel how-panel" aria-labelledby="public-title-customer" tabIndex={-1}><HowItWorksPage onComingSoon={notice.show} /></section>
        <section id="para-restaurantes" className="screen-panel info-panel partners-panel" aria-labelledby="public-title-restaurant" tabIndex={-1}><ForRestaurantsPage /></section>
        <section id="se-repartidor" className="screen-panel info-panel courier-panel" aria-labelledby="public-title-courier" tabIndex={-1}><CourierPage /></section>
      </main>
      <SiteFooter />
      <ComingSoonNotice
        visible={notice.visible}
        revision={notice.revision}
        triggerRef={notice.triggerRef}
        onPause={notice.pause}
        onResume={notice.resume}
        onDismiss={notice.dismiss}
      />
    </div>
  )
}
