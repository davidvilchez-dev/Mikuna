import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useRef, useState } from 'react'
import { AppIcon } from '../auth/AppIcon'
import { reverseGeocode, type AddressSuggestion } from './reverse-geocode'
import './location-picker.css'

export interface MapPoint {
  latitude: number
  longitude: number
}

interface LocationPickerProps {
  /** Punto ya marcado; sin él el mapa abre en la plaza principal de Ayacucho. */
  initial: MapPoint | null
  confirmLabel?: string
  /** Entrega el punto elegido y la dirección que sugiere el mapa (si se pudo averiguar). */
  onConfirm: (point: MapPoint, suggestion: AddressSuggestion | null) => void
  onCancel: () => void
}

/** Plaza de Armas de Ayacucho: punto de partida cuando todavía no hay ubicación. */
const DEFAULT_CENTER: [number, number] = [-13.15878, -74.22321]

const PIN_SVG = `<svg viewBox="0 0 32 42" width="38" height="50" aria-hidden="true">
  <path d="M16 1C8.3 1 2 7.2 2 14.8 2 25 16 41 16 41s14-16 14-26.2C30 7.2 23.7 1 16 1z" fill="#EF5002" stroke="#fff" stroke-width="2"/>
  <circle cx="16" cy="15" r="5" fill="#fff"/></svg>`

function round(value: number): number {
  return Math.round(value * 1_000_000) / 1_000_000
}

type Lookup = { kind: 'loading' } | { kind: 'done'; suggestion: AddressSuggestion | null }

/**
 * Mapa con un pin que se arrastra (o se coloca tocando el mapa) hasta la puerta de la casa. Al soltarlo se consulta
 * qué dirección corresponde al punto. Los mosaicos y la consulta son de OpenStreetMap; solo viajan coordenadas.
 */
export default function LocationPicker({ initial, confirmLabel = 'Confirmar ubicación', onConfirm, onCancel }: LocationPickerProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<L.Map | null>(null)
  const markerRef = useRef<L.Marker | null>(null)
  const [point, setPoint] = useState<MapPoint>(() => initial ?? { latitude: DEFAULT_CENTER[0], longitude: DEFAULT_CENTER[1] })
  const [lookup, setLookup] = useState<Lookup>({ kind: 'loading' })
  const [locating, setLocating] = useState(false)
  const [notice, setNotice] = useState<{ text: string; tone: 'info' | 'error' } | null>(null)
  const [accuracy, setAccuracy] = useState<number | null>(null)
  const watchRef = useRef<number | null>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const circleRef = useRef<L.Circle | null>(null)

  /** Deja de escuchar al GPS; se usa al terminar, al fallar y cuando la persona toma el control del pin. */
  function stopLocating() {
    if (watchRef.current !== null) { navigator.geolocation.clearWatch(watchRef.current); watchRef.current = null }
    if (timerRef.current) { clearTimeout(timerRef.current); timerRef.current = null }
    setLocating(false)
  }

  function dropAccuracyCircle() {
    circleRef.current?.remove()
    circleRef.current = null
    setAccuracy(null)
  }

  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    const start: [number, number] = initial ? [initial.latitude, initial.longitude] : DEFAULT_CENTER
    const map = L.map(container, { center: start, zoom: initial ? 18 : 16, maxZoom: 19, zoomControl: false })
    L.control.zoom({ position: 'bottomright', zoomInTitle: 'Acercar', zoomOutTitle: 'Alejar' }).addTo(map)
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a>',
    }).addTo(map)

    const marker = L.marker(start, {
      draggable: true,
      autoPan: true,
      keyboard: true,
      title: 'Punto de entrega: arrástralo hasta tu puerta',
      icon: L.divIcon({ className: 'locpick-marker', html: PIN_SVG, iconSize: [38, 50], iconAnchor: [19, 49] }),
    }).addTo(map)
    marker.bindTooltip('Punto de entrega', { permanent: true, direction: 'top', offset: [0, -46], className: 'locpick-tip' })

    const settle = () => {
      const { lat, lng } = marker.getLatLng()
      setPoint({ latitude: round(lat), longitude: round(lng) })
    }
    marker.on('dragstart', () => { stopLocating(); dropAccuracyCircle(); marker.getElement()?.classList.add('locpick-marker-lifted') })
    marker.on('dragend', () => { marker.getElement()?.classList.remove('locpick-marker-lifted'); settle() })
    map.on('click', (event: L.LeafletMouseEvent) => { stopLocating(); dropAccuracyCircle(); marker.setLatLng(event.latlng); settle() })

    mapRef.current = map
    markerRef.current = marker
    return () => { stopLocating(); map.remove(); mapRef.current = null; markerRef.current = null; circleRef.current = null }
    // El mapa se crea una sola vez; el punto inicial solo decide dónde abre.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Cada vez que el pin se asienta, se averigua qué dirección corresponde (una consulta, sin ráfagas).
  useEffect(() => {
    const controller = new AbortController()
    const timer = setTimeout(() => {
      setLookup({ kind: 'loading' })
      reverseGeocode(point.latitude, point.longitude, controller.signal).then((suggestion) => {
        if (!controller.signal.aborted) setLookup({ kind: 'done', suggestion })
      })
    }, 500)
    return () => { clearTimeout(timer); controller.abort() }
  }, [point])

  /** Coloca el pin y el círculo de precisión en la mejor lectura que se tenga hasta ahora. */
  function applyReading(position: GeolocationPosition) {
    const map = mapRef.current
    const marker = markerRef.current
    if (!map || !marker) return
    const latlng = L.latLng(position.coords.latitude, position.coords.longitude)
    const meters = Math.round(position.coords.accuracy)
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    marker.setLatLng(latlng)
    map.setView(latlng, meters <= 30 ? 19 : meters <= 120 ? 18 : 17, { animate: !reduce })
    if (circleRef.current) circleRef.current.setLatLng(latlng).setRadius(meters)
    else circleRef.current = L.circle(latlng, { radius: meters, color: '#EF5002', weight: 1, fillColor: '#EF5002', fillOpacity: 0.12, interactive: false }).addTo(map)
    setAccuracy(meters)
    setPoint({ latitude: round(latlng.lat), longitude: round(latlng.lng) })
  }

  /**
   * Escucha al GPS unos segundos y se queda con la lectura más precisa: la primera suele ser aproximada
   * (por Wi-Fi o red) y las siguientes mejoran. Termina al llegar a ±20 m o a los 15 s.
   */
  function locate() {
    if (!('geolocation' in navigator)) {
      setNotice({ text: 'Tu navegador no permite ubicarte. Arrastra el pin hasta tu puerta.', tone: 'error' })
      return
    }
    stopLocating()
    dropAccuracyCircle()
    setNotice(null)
    setLocating(true)
    let best: GeolocationPosition | null = null

    const finish = () => {
      stopLocating()
      if (!best) { setNotice({ text: 'No pudimos ubicarte. Arrastra el pin hasta tu puerta.', tone: 'error' }); return }
      const meters = Math.round(best.coords.accuracy)
      setNotice({
        text: meters > 60
          ? `Tu dispositivo ubicó la zona con un margen de ±${meters} m (en computadoras suele ser aproximado). Arrastra el pin hasta tu puerta.`
          : `Ubicación con precisión de ±${meters} m. Ajusta el pin si hace falta.`,
        tone: 'info',
      })
    }

    timerRef.current = setTimeout(finish, 15_000)
    watchRef.current = navigator.geolocation.watchPosition(
      (position) => {
        if (!best || position.coords.accuracy < best.coords.accuracy) {
          best = position
          applyReading(position)
        }
        if (position.coords.accuracy <= 20) finish()
      },
      (error) => {
        // Los fallos puntuales (señal perdida un instante, lectura tardía) no cortan la búsqueda: el GPS reintenta
        // y el límite de 15 s cierra el proceso. Solo la falta de permiso es definitiva.
        if (error.code !== error.PERMISSION_DENIED) return
        stopLocating()
        setNotice({ text: 'No tenemos permiso para ver tu ubicación. Actívalo en el navegador o arrastra el pin hasta tu puerta.', tone: 'error' })
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: 15_000 },
    )
  }

  const suggestion = lookup.kind === 'done' ? lookup.suggestion : null
  return (
    <div className="locpick">
      <p className="locpick-help">Arrastra el pin hasta la puerta de tu casa, o toca el mapa para colocarlo.</p>
      <div className="locpick-frame">
        <div ref={containerRef} className="locpick-map" role="region" aria-label="Mapa para marcar el punto de entrega" />
        <button type="button" className="locpick-locate" onClick={locate} disabled={locating}>
          <svg className="app-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v3M12 18v3M3 12h3M18 12h3M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" /></svg>
          {locating ? (accuracy !== null ? `Afinando… ±${accuracy} m` : 'Buscando…') : 'Usar mi ubicación actual'}
        </button>
      </div>

      <div className="locpick-address" role="status" aria-live="polite">
        <span className="locpick-address-icon"><AppIcon name="pin" /></span>
        <span className="locpick-address-text">
          {lookup.kind === 'loading' && <small>Buscando la dirección…</small>}
          {lookup.kind === 'done' && suggestion && (<><strong>{suggestion.street}</strong><small>{suggestion.district ?? 'Ayacucho'}</small></>)}
          {lookup.kind === 'done' && !suggestion && <small>No encontramos el nombre de la calle. Podrás escribirla en el siguiente paso.</small>}
        </span>
      </div>

      {notice && <p className={notice.tone === 'error' ? 'auth-alert' : 'auth-notice'} role="status">{notice.text}</p>}
      <div className="locpick-actions">
        <button type="button" className="auth-submit" onClick={() => onConfirm(point, suggestion)} disabled={lookup.kind === 'loading'}>{confirmLabel}</button>
        <button type="button" className="auth-submit auth-submit-secondary" onClick={onCancel}>Cancelar</button>
      </div>
    </div>
  )
}
