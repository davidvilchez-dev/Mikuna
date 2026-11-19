import { useEffect, useRef } from 'react'
import { categories } from './home-content'
import { CategorySection } from './components/CategorySection'
import { ComingSoonNotice } from './components/ComingSoonNotice'
import { HeroSection } from './components/HeroSection'
import { PartnerBanners } from './components/PartnerBanners'
import { SiteHeader } from './components/SiteHeader'
import { SiteFooter } from './components/SiteFooter'
import { useComingSoonNotice } from './useComingSoonNotice'
import '../public-pages/public-pages.css'
import '../public-pages/continuous-home.css'

export function HomePage() {
  const menuToggleRef = useRef<HTMLButtonElement | null>(null)
  const notice = useComingSoonNotice(menuToggleRef)
  const mainRef = useRef<HTMLElement | null>(null)
  useEffect(() => { window.scrollTo(0, 0) }, [])

  return (
    <div className="continuous-site">
      <a className="skip-to-content" href="#contenido" onClick={(event) => { event.preventDefault(); mainRef.current?.focus() }}>Saltar al contenido</a>
      <SiteHeader menuToggleRef={menuToggleRef} />
      <main id="contenido" ref={mainRef} tabIndex={-1} className="public-main">
        <section id="inicio" className="screen-panel home-panel" aria-labelledby="titulo-principal" tabIndex={-1}>
          <HeroSection />
          <CategorySection categories={categories} onComingSoon={notice.show} />
          <PartnerBanners />
        </section>
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
