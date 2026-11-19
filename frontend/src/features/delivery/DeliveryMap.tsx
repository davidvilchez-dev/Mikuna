import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'
import '../cart/location-picker.css'

interface DeliveryMapProps {
  latitude: number
  longitude: number
}

const PIN_SVG = `<svg viewBox="0 0 32 42" width="38" height="50" aria-hidden="true">
  <path d="M16 1C8.3 1 2 7.2 2 14.8 2 25 16 41 16 41s14-16 14-26.2C30 7.2 23.7 1 16 1z" fill="#EF5002" stroke="#fff" stroke-width="2"/>
  <circle cx="16" cy="15" r="5" fill="#fff"/></svg>`

/** Mapa del punto de entrega que marcó el cliente. Solo lectura: se puede mover y acercar, no cambiar el punto. */
export default function DeliveryMap({ latitude, longitude }: DeliveryMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    const map = L.map(container, { center: [latitude, longitude], zoom: 17, maxZoom: 19, zoomControl: false, scrollWheelZoom: false })
    L.control.zoom({ position: 'bottomright', zoomInTitle: 'Acercar', zoomOutTitle: 'Alejar' }).addTo(map)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a>',
    }).addTo(map)
    L.marker([latitude, longitude], {
      interactive: false,
      icon: L.divIcon({ className: 'locpick-marker', html: PIN_SVG, iconSize: [38, 50], iconAnchor: [19, 49] }),
    }).addTo(map).bindTooltip('Punto de entrega', { permanent: true, direction: 'top', offset: [0, -46], className: 'locpick-tip' })
    return () => { map.remove() }
  }, [latitude, longitude])

  return (
    <div className="locpick">
      <div className="locpick-frame">
        <div ref={containerRef} className="locpick-map cr-map" role="region" aria-label="Mapa del punto de entrega" />
      </div>
    </div>
  )
}
