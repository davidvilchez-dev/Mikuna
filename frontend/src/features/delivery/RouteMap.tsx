import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef } from 'react'
import '../cart/location-picker.css'
import './route-map.css'

export type RoutePointKind = 'courier' | 'pickup' | 'dropoff'

export interface RoutePoint {
  kind: RoutePointKind
  latitude: number
  longitude: number
  /** Texto fijo sobre el marcador. */
  label: string
}

interface RouteMapProps {
  points: RoutePoint[]
  ariaLabel: string
  className?: string
}

const PIN_SVG = `<svg viewBox="0 0 32 42" width="34" height="44" aria-hidden="true">
  <path d="M16 1C8.3 1 2 7.2 2 14.8 2 25 16 41 16 41s14-16 14-26.2C30 7.2 23.7 1 16 1z" stroke="#fff" stroke-width="2"/>
  <circle cx="16" cy="15" r="5" fill="#fff"/></svg>`

function iconFor(kind: RoutePointKind): L.DivIcon {
  if (kind === 'courier') {
    return L.divIcon({ className: 'rm-marker rm-courier', html: '<span class="rm-courier-dot"></span>', iconSize: [26, 26], iconAnchor: [13, 13] })
  }
  return L.divIcon({ className: 'rm-marker rm-' + kind, html: PIN_SVG, iconSize: [34, 44], iconAnchor: [17, 43] })
}

/**
 * Mapa de solo lectura con hasta tres puntos: el repartidor, el local y el destino. Los marcadores se mueven sin
 * recrear el mapa (el repartidor se actualiza cada pocos segundos) y el encuadre solo se recalcula cuando aparece
 * o desaparece un punto, para no quitarle el mapa a quien lo está moviendo.
 */
export default function RouteMap({ points, ariaLabel, className }: RouteMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<L.Map | null>(null)
  const markersRef = useRef<Map<RoutePointKind, L.Marker>>(new Map())
  const fitKeyRef = useRef('')

  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    const map = L.map(container, { center: [-13.15878, -74.22321], zoom: 15, maxZoom: 19, zoomControl: false, scrollWheelZoom: false })
    L.control.zoom({ position: 'bottomright', zoomInTitle: 'Acercar', zoomOutTitle: 'Alejar' }).addTo(map)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a>',
    }).addTo(map)
    mapRef.current = map
    const markers = markersRef.current
    return () => { map.remove(); mapRef.current = null; markers.clear(); fitKeyRef.current = '' }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const markers = markersRef.current
    const wanted = new Set(points.map((p) => p.kind))
    for (const [kind, marker] of markers) {
      if (!wanted.has(kind)) { marker.remove(); markers.delete(kind) }
    }
    for (const point of points) {
      const latlng = L.latLng(point.latitude, point.longitude)
      const existing = markers.get(point.kind)
      if (existing) {
        existing.setLatLng(latlng)
        existing.setTooltipContent(point.label)
      } else {
        const marker = L.marker(latlng, { interactive: false, icon: iconFor(point.kind), zIndexOffset: point.kind === 'courier' ? 1000 : 0 })
          .addTo(map)
          .bindTooltip(point.label, { permanent: true, direction: 'top', offset: [0, point.kind === 'courier' ? -14 : -42], className: 'locpick-tip' })
        markers.set(point.kind, marker)
      }
    }
    const key = points.map((p) => p.kind).sort().join()
    if (key !== fitKeyRef.current && points.length > 0) {
      fitKeyRef.current = key
      const bounds = L.latLngBounds(points.map((p) => [p.latitude, p.longitude] as [number, number]))
      map.fitBounds(bounds, { padding: [48, 48], maxZoom: 17, animate: false })
    }
  }, [points])

  return (
    <div className="locpick">
      <div className="locpick-frame">
        <div ref={containerRef} className={'locpick-map ' + (className ?? 'cr-map')} role="region" aria-label={ariaLabel} />
      </div>
    </div>
  )
}
