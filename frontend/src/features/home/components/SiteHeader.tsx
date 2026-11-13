import { useEffect, useRef, useState } from 'react'
import { homeAssets } from '../home-assets'
import { headerAction, navigation } from '../home-content'
import type { MenuToggleRef, ShowComingSoon } from '../home-types'
import { FoodImage } from './FoodImage'

interface SiteHeaderProps {
  activePage: string
  menuToggleRef: MenuToggleRef
  onComingSoon: ShowComingSoon
}

export function SiteHeader({ activePage, menuToggleRef, onComingSoon }: SiteHeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const headerRef = useRef<HTMLElement | null>(null)

  useEffect(() => {
    const header = headerRef.current
    if (!header) return
    const updateHeight = () => document.documentElement.style.setProperty('--site-header-height', `${header.getBoundingClientRect().height}px`)
    updateHeight()
    const observer = new ResizeObserver(updateHeight)
    observer.observe(header)
    return () => {
      observer.disconnect()
      document.documentElement.style.removeProperty('--site-header-height')
    }
  }, [])

  useEffect(() => {
    const desktop = window.matchMedia('(min-width: 1280px)')
    const closeAtDesktop = () => { if (desktop.matches) setMenuOpen(false) }
    desktop.addEventListener('change', closeAtDesktop)
    return () => desktop.removeEventListener('change', closeAtDesktop)
  }, [])

  useEffect(() => {
    if (!menuOpen) return
    const closeWithEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      setMenuOpen(false)
      menuToggleRef.current?.focus({ preventScroll: true })
    }
    document.addEventListener('keydown', closeWithEscape)
    return () => document.removeEventListener('keydown', closeWithEscape)
  }, [menuOpen, menuToggleRef])

  const followSection = () => {
    if (!menuOpen) return
    setMenuOpen(false)
    queueMicrotask(() => menuToggleRef.current?.focus({ preventScroll: true }))
  }

  return (
    <header ref={headerRef} className="site-header bg-mikuna-white">
      <div className="header-shell">
        <a href="#inicio" className="brand-link" aria-label="Mikuna, ir al inicio" data-keep-clear onClick={followSection}>
          <FoodImage asset={homeAssets.logo} className="brand-image" priority />
        </a>
        <button
          ref={menuToggleRef}
          type="button"
          className="menu-toggle xl:hidden"
          data-mobile-menu-toggle
          data-keep-clear
          aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'}
          aria-expanded={menuOpen}
          aria-controls="navegacion-principal"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            {menuOpen
              ? <path d="m6 6 12 12M18 6 6 18" />
              : <path d="M4 6h16M4 12h16M4 18h16" />}
          </svg>
        </button>
        <nav id="navegacion-principal" className="site-nav" aria-label="Navegación principal" data-open={menuOpen}>
          {navigation.map((item) => (
              <a key={item.id} href={'#' + item.id} className={'nav-link' + (activePage === item.id ? ' nav-link-active' : '')} aria-current={activePage === item.id ? 'location' : undefined} onClick={followSection}>
                <span data-keep-clear>{item.label}</span>
              </a>
            ))}
          <button type="button" className="header-order bg-mikuna-orange text-mikuna-white" data-action={headerAction.id} onClick={(event) => { onComingSoon(event.currentTarget); followSection() }}>
            <span data-keep-clear>{headerAction.label}</span>
          </button>
        </nav>
      </div>
    </header>
  )
}
