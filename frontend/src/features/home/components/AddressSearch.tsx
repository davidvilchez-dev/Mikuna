import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { getFilters } from '../../catalog/catalog-api'

function fold(text: string) {
  return text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().replace(/\s+/g, ' ').trim()
}

type Hint = { tone: 'info' | 'error'; text: string }

const INITIAL_HINT: Hint = { tone: 'info', text: 'Te mostramos los restaurantes que reparten en tu zona.' }

/** Busca restaurantes por dirección: reconoce el distrito dentro del texto y abre la búsqueda filtrada por zona. */
export function AddressSearch() {
  const navigate = useNavigate()
  const [address, setAddress] = useState('')
  const [zones, setZones] = useState<string[]>([])
  const [hint, setHint] = useState<Hint>(INITIAL_HINT)

  useEffect(() => {
    let active = true
    getFilters().then((filters) => { if (active) setZones(filters.zones) }).catch(() => { /* sin zonas: se abre la lista completa */ })
    return () => { active = false }
  }, [])

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const text = fold(address)
    if (!text) {
      setHint({ tone: 'error', text: 'Escribe tu dirección o tu distrito para ver qué restaurantes llegan.' })
      return
    }
    if (zones.length === 0) {
      navigate('/restaurantes')
      return
    }
    const match = zones.find((zone) => text.includes(fold(zone)))
    if (match) {
      navigate('/restaurantes?zona=' + encodeURIComponent(match))
      return
    }
    setHint({ tone: 'error', text: 'Todavía no hay restaurantes que lleguen ahí. Zonas disponibles: ' + zones.join(', ') + '.' })
  }

  return (
    <form className="address-search" role="search" aria-label="Buscar restaurantes por dirección" onSubmit={submit} noValidate>
      <label htmlFor="hero-address" className="visually-hidden">Tu dirección o distrito</label>
      <div className="address-field">
        <svg className="address-pin" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" />
        </svg>
        <input
          id="hero-address"
          className="address-input"
          type="text"
          list="hero-zones"
          autoComplete="street-address"
          placeholder="Tu dirección o distrito"
          value={address}
          aria-describedby="hero-address-hint"
          aria-invalid={hint.tone === 'error'}
          onChange={(event) => { setAddress(event.target.value); if (hint.tone === 'error') setHint(INITIAL_HINT) }}
        />
        <datalist id="hero-zones">{zones.map((zone) => <option key={zone} value={zone} />)}</datalist>
        <button type="submit" className="address-submit">Ver restaurantes</button>
      </div>
      <p id="hero-address-hint" className="address-hint" data-tone={hint.tone} role={hint.tone === 'error' ? 'alert' : undefined}>{hint.text}</p>
    </form>
  )
}
