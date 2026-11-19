/** Distritos urbanos de Huamanga como atajo; el servidor decide la cobertura real y rechaza el resto. */
export const DISTRICTS = [
  { value: 'Ayacucho', aliases: ['ayacucho'] },
  { value: 'San Juan Bautista', aliases: ['san juan bautista'] },
  { value: 'Carmen Alto', aliases: ['carmen alto'] },
  { value: 'Jesús Nazareno', aliases: ['jesus nazareno'] },
  { value: 'Andrés Avelino Cáceres Dorregaray', aliases: ['andres avelino caceres', 'caceres dorregaray'] },
] as const

/** Minúsculas y sin tildes, para comparar nombres escritos de distinta forma. */
export function fold(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim()
}

/** Distrito conocido que corresponde al texto (por ejemplo «Distrito de San Juan Bautista»), o `null`. */
export function matchDistrict(text: string | undefined | null): string | null {
  if (!text) return null
  const folded = fold(text)
  const found = DISTRICTS.find((district) => district.aliases.some((alias) => folded.includes(alias)))
  return found ? found.value : null
}
