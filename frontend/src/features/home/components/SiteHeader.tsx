import { useEffect, useRef, useState } from 'react'
import { Link, NavLink } from 'react-router'
import { hasActiveSession } from '../../auth/session-storage'
import { homeAssets } from '../home-assets'
import { mainNavigation, partnerNavigation } from '../home-content'
import type { MenuToggleRef } from '../home-types'
import { FoodImage } from './FoodImage'

interface SiteHeaderProps {
  menuToggleRef: MenuToggleRef
}

export function SiteHeader({ menuToggleRef }: SiteHeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [partnersOpen, setPartnersOpen] = useState(false)
  const headerRef = useRef<HTMLElement | null>(null)
  const partnersRef = useRef<HTMLDivElement | null>(null)
  const signedIn = hasActiveSession()

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
    const desktop = window.matchMedia('(min-width: 1100px)')
    const closeAtDesktop = () => { if (desktop.matches) setMenuOpen(false) }
    desktop.addEventListener('change', closeAtDesktop)
    return () => desktop.removeEventListener('change', closeAtDesktop)
  }, [])

  useEffect(() => {
    if (!menuOpen && !partnersOpen) return
    const closeWithEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      setPartnersOpen(false)
      if (menuOpen) {
        setMenuOpen(false)
        menuToggleRef.current?.focus({ preventScroll: true })
      }
    }
    const closeOutside = (event: PointerEvent) => {
      if (partnersRef.current && !partnersRef.current.contains(event.target as Node)) setPartnersOpen(false)
    }
    document.addEventListener('keydown', closeWithEscape)
    document.addEventListener('pointerdown', closeOutside)
    return () => {
      document.removeEventListener('keydown', closeWithEscape)
      document.removeEventListener('pointerdown', closeOutside)
    }
  }, [menuOpen, partnersOpen, menuToggleRef])

  const follow = () => {
    setPartnersOpen(false)
    if (!menuOpen) return
    setMenuOpen(false)
    queueMicrotask(() => menuToggleRef.current?.focus({ preventScroll: true }))
  }

  return (
    <header ref={headerRef} className="site-header">
      <div className="header-shell">
        <Link to="/" className="brand-link" aria-label="Mikuna, ir al inicio" data-keep-clear onClick={() => { window.scrollTo({ top: 0 }); follow() }}>
          <FoodImage asset={homeAssets.logo} className="brand-image" priority />
        </Link>
        <button
          ref={menuToggleRef}
          type="button"
          className="menu-toggle"
          data-mobile-menu-toggle
          data-keep-clear
          aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'}
          aria-expanded={menuOpen}
          aria-controls="navegacion-principal"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            {menuOpen ? <path d="m6 6 12 12M18 6 6 18" /> : <path d="M4 6h16M4 12h16M4 18h16" />}
          </svg>
        </button>
        <nav id="navegacion-principal" className="site-nav" aria-label="Navegación principal" data-open={menuOpen}>
          <div className="nav-links">
            {mainNavigation.map((item) => (
              <NavLink key={item.to} to={item.to} className={({ isActive }) => 'nav-link' + (isActive ? ' nav-link-active' : '')} onClick={follow}>
                <span data-keep-clear>{item.label}</span>
              </NavLink>
            ))}
            <div className="nav-group" ref={partnersRef} data-open={partnersOpen}>
              <button type="button" className="nav-link nav-group-toggle" aria-expanded={partnersOpen} aria-controls="menu-socios" onClick={() => setPartnersOpen((open) => !open)}>
                <span data-keep-clear>Trabaja con nosotros</span>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
              </button>
              <div id="menu-socios" className="nav-menu">
                {partnerNavigation.map((item) => (
                  <Link key={item.to} to={item.to} className="nav-menu-item" onClick={follow}>
                    <strong>{item.label}</strong>
                    <span>{item.hint}</span>
                  </Link>
                ))}
              </div>
            </div>
          </div>
          <div className="nav-account">
            {signedIn ? (
              <>
                <Link to="/historial" className="nav-link" onClick={follow}><span data-keep-clear>Mis pedidos</span></Link>
                <Link to="/mi-cuenta" className="header-cta" onClick={follow}><span data-keep-clear>Mi cuenta</span></Link>
              </>
            ) : (
              <>
                <Link to="/iniciar-sesion" className="nav-link nav-login" onClick={follow}><span data-keep-clear>Iniciar sesión</span></Link>
                <Link to="/registro" className="header-cta" onClick={follow}><span data-keep-clear>Crear cuenta</span></Link>
              </>
            )}
          </div>
        </nav>
      </div>
    </header>
  )
}
