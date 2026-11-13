import { useEffect, useState } from 'react'

const sectionIds = ['inicio', 'sobre-nosotros', 'como-funciona', 'para-restaurantes', 'se-repartidor'] as const

export function useSectionNavigation() {
  const [activeSection, setActiveSection] = useState<string>('inicio')

  useEffect(() => {
    const sections = sectionIds.map((id) => document.getElementById(id)).filter((element): element is HTMLElement => element !== null)
    const observer = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) setActiveSection(entry.target.id)
      }
    }, { rootMargin: '-25% 0px -65% 0px', threshold: 0 })
    sections.forEach((section) => observer.observe(section))

    const focusDestination = () => {
      const destination = sections.find((section) => '#' + section.id === window.location.hash)
      destination?.focus({ preventScroll: true })
    }
    window.addEventListener('hashchange', focusDestination)
    const frame = requestAnimationFrame(() => {
      const destination = sections.find((section) => '#' + section.id === window.location.hash)
      destination?.scrollIntoView({ behavior: 'instant', block: 'start' })
    })

    return () => {
      observer.disconnect()
      window.removeEventListener('hashchange', focusDestination)
      cancelAnimationFrame(frame)
    }
  }, [])

  return activeSection
}
