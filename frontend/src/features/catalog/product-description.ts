export interface ParsedDescription {
  /** Frase corta para la tarjeta y la cabecera del detalle. */
  summary: string
  /** Lo que contiene el plato: una viñeta por línea que empieza con «- » (o «• »). */
  items: string[]
}

const BULLET = /^\s*[-•]\s+/

/**
 * La descripción es un solo texto: las líneas normales forman la frase corta y las que empiezan con
 * «- » son las viñetas del detalle. Un texto sin viñetas es solo frase corta.
 */
export function parseDescription(text: string | null | undefined): ParsedDescription {
  const summary: string[] = []
  const items: string[] = []
  for (const raw of (text ?? '').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line) continue
    if (BULLET.test(raw)) items.push(line.replace(BULLET, '').trim())
    else summary.push(line)
  }
  return { summary: summary.join(' '), items: items.filter(Boolean) }
}
