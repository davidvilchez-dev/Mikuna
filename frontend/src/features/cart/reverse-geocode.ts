import { matchDistrict } from './address-options'

/** Dirección que sugiere el mapa para un punto; el cliente puede corregirla en el formulario. */
export interface AddressSuggestion {
  street: string
  district: string | null
  /** Texto para mostrar bajo el mapa. */
  display: string
}

interface NominatimResponse {
  display_name?: string
  address?: Record<string, string | undefined>
  error?: string
}

const TIMEOUT_MS = 6000

const ROAD_PREFIXES: readonly [RegExp, string][] = [
  [/^jir[oó]n\s+/i, 'Jr. '],
  [/^avenida\s+/i, 'Av. '],
  [/^pasaje\s+/i, 'Psje. '],
  [/^psje\.?\s+/i, 'Psje. '],
]

function abbreviateRoad(road: string): string {
  for (const [pattern, replacement] of ROAD_PREFIXES) {
    if (pattern.test(road)) return road.replace(pattern, replacement)
  }
  return road
}

/** Convierte la respuesta del servicio de direcciones en calle, número y distrito con el formato que se usa en la ciudad. */
export function suggestionFrom(data: NominatimResponse): AddressSuggestion | null {
  const address = data.address
  if (!address) return null

  const road = address.road ?? address.pedestrian ?? address.footway ?? address.path
  const place = address.neighbourhood ?? address.suburb ?? address.quarter
  let street = ''
  if (road) street = abbreviateRoad(road) + (address.house_number ? ' ' + address.house_number : '')
  else if (place) street = place
  else if (data.display_name) street = data.display_name.split(',')[0].trim()
  if (!street) return null

  const candidates = [address.city_district, address.suburb, address.municipality, address.city, address.town, address.village, address.county, address.neighbourhood]
  let district: string | null = null
  for (const candidate of candidates) {
    district = matchDistrict(candidate)
    if (district) break
  }

  return { street, district, display: district ? `${street} · ${district}` : street }
}

/**
 * Pregunta a Nominatim (OpenStreetMap) qué dirección corresponde a un punto. Solo se envían las coordenadas;
 * el servicio pide no hacer más de una consulta por segundo, por eso se llama una vez al soltar el pin.
 */
export async function reverseGeocode(latitude: number, longitude: number, signal?: AbortSignal): Promise<AddressSuggestion | null> {
  const params = new URLSearchParams({
    format: 'jsonv2',
    lat: String(latitude),
    lon: String(longitude),
    zoom: '18',
    addressdetails: '1',
    'accept-language': 'es',
  })
  // Si el servicio no responde pronto se sigue sin sugerencia: el formulario se llena a mano.
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  const cancel = () => controller.abort()
  signal?.addEventListener('abort', cancel)
  try {
    const response = await fetch('https://nominatim.openstreetmap.org/reverse?' + params.toString(), { signal: controller.signal, headers: { Accept: 'application/json' } })
    if (!response.ok) return null
    const data = (await response.json()) as NominatimResponse
    return data.error ? null : suggestionFrom(data)
  } catch {
    return null
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', cancel)
  }
}
