/** Lógica pura del chat con el asistente (spec 013): historial, lector de SSE y textos de error. */

export type ChatRole = 'user' | 'assistant'

export interface ChatMessage {
  role: ChatRole
  text: string
  /** Mensaje de aviso generado por la interfaz (no lo dijo el asistente): no se envía al servidor. */
  notice?: boolean
}

export type SseEvent =
  | { type: 'delta'; text: string }
  | { type: 'done' }
  | { type: 'error'; code: string; message: string }

/** El servidor solo admite los últimos 10 mensajes y 500 caracteres por mensaje (contrato). */
export const MAX_HISTORY = 10
export const MAX_LENGTH = 500
export const STORAGE_KEY = 'mikuna.assistant'

export const GREETING =
  '¡Hola! Soy el asistente de Mikuna. Puedo recomendarte platos con datos reales y decirte qué restaurantes están abiertos. ' +
  'Por favor, no compartas datos personales aquí.'

export const SUGGESTIONS = ['¿Qué está abierto ahora?', 'Algo vegetariano bajo 20 soles', '¿Cómo hago un pedido?']

/** Últimos {@code max} mensajes (los avisos de la interfaz no cuentan). */
export function trimHistory(messages: ChatMessage[], max: number = MAX_HISTORY): ChatMessage[] {
  const real = messages.filter((m) => !m.notice && m.text.trim() !== '')
  return real.slice(Math.max(0, real.length - max))
}

/** Cuerpo de la petición: últimos 10 mensajes reales, sin espacios sobrantes y con el tope de longitud. */
export function toRequestMessages(messages: ChatMessage[]): { role: ChatRole; text: string }[] {
  return trimHistory(messages).map((m) => ({ role: m.role, text: m.text.trim().slice(0, MAX_LENGTH) }))
}

/** Concatena un fragmento al último mensaje del asistente (o lo crea si el último es del cliente). */
export function appendDelta(messages: ChatMessage[], text: string): ChatMessage[] {
  const last = messages[messages.length - 1]
  if (last && last.role === 'assistant' && !last.notice) {
    return [...messages.slice(0, -1), { ...last, text: last.text + text }]
  }
  return [...messages, { role: 'assistant', text }]
}

/**
 * Extrae los eventos completos de un búfer de SSE; lo que queda sin terminar (un evento partido entre dos lecturas)
 * se devuelve en {@code rest} para unirlo con la siguiente lectura.
 */
export function parseSseBuffer(buffer: string): { events: SseEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n')
  const blocks = normalized.split('\n\n')
  const rest = blocks.pop() ?? ''
  const events: SseEvent[] = []
  for (const block of blocks) {
    const event = parseBlock(block)
    if (event) events.push(event)
  }
  return { events, rest }
}

function parseBlock(block: string): SseEvent | null {
  let name = ''
  const data: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).trim())
  }
  if (name === 'done') return { type: 'done' }
  if (data.length === 0) return null
  try {
    const payload = JSON.parse(data.join('\n')) as { text?: unknown; code?: unknown; message?: unknown }
    if (name === 'delta' && typeof payload.text === 'string') return { type: 'delta', text: payload.text }
    if (name === 'error') {
      return {
        type: 'error',
        code: typeof payload.code === 'string' ? payload.code : 'ASSISTANT_UNAVAILABLE',
        message: typeof payload.message === 'string' ? payload.message : UNAVAILABLE,
      }
    }
  } catch {
    return null
  }
  return null
}

export const UNAVAILABLE = 'El asistente no está disponible por ahora. Intenta más tarde.'
export const SESSION_EXPIRED = 'Tu sesión venció. Inicia sesión de nuevo para seguir conversando.'
export const NETWORK_ERROR = 'No hay conexión con Mikuna. Revisa tu internet e inténtalo de nuevo.'

/** Texto amable para un fallo HTTP previo al flujo; nunca muestra detalles técnicos. */
export function friendlyHttpError(status: number, detail: string): string {
  if (status === 0) return NETWORK_ERROR
  if (status === 401) return SESSION_EXPIRED
  if (status === 429 || status === 400) return detail
  return UNAVAILABLE
}

export interface TextPiece {
  text: string
  bold: boolean
  italic: boolean
}

/** Convierte **negrita** y *cursiva* en piezas de texto para pintar sin HTML; los asteriscos sueltos se quitan. */
export function formatInline(text: string): TextPiece[] {
  const pieces: TextPiece[] = []
  for (const part of text.split(/(\*\*.+?\*\*|\*[^*\s][^*]*?\*)/g)) {
    if (part === '') continue
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      pieces.push({ text: part.slice(2, -2), bold: true, italic: false })
    } else if (part.startsWith('*') && part.endsWith('*') && part.length > 2) {
      pieces.push({ text: part.slice(1, -1), bold: false, italic: true })
    } else {
      pieces.push({ text: part.replace(/\*/g, ''), bold: false, italic: false })
    }
  }
  return pieces.filter((piece) => piece.text !== '')
}

export type Block =
  | { type: 'p'; pieces: TextPiece[] }
  | { type: 'ul' | 'ol'; items: TextPiece[][] }

const BULLET = /^\s*[*•-]\s+(.*)$/
const NUMBERED = /^\s*\d+[.)]\s+(.*)$/

/**
 * Convierte la respuesta del asistente en bloques: párrafos, listas con viñeta y listas numeradas. Las líneas con
 * sangría que siguen a un elemento se unen a él (detalle del plato). Todo se pinta sin HTML.
 */
export function formatBlocks(text: string): Block[] {
  const blocks: Block[] = []
  let paragraph: string[] = []
  const flush = () => {
    if (paragraph.length > 0) blocks.push({ type: 'p', pieces: formatInline(paragraph.join(' ')) })
    paragraph = []
  }
  for (const line of text.replace(/\r\n/g, '\n').split('\n')) {
    const bullet = BULLET.exec(line)
    const numbered = bullet ? null : NUMBERED.exec(line)
    const match = bullet ?? numbered
    if (match) {
      flush()
      const type = bullet ? 'ul' : 'ol'
      const last = blocks[blocks.length - 1]
      const item = formatInline(match[1])
      if (last && last.type === type) last.items.push(item)
      else blocks.push({ type, items: [item] })
    } else if (line.trim() === '') {
      flush()
    } else {
      const last = blocks[blocks.length - 1]
      if (paragraph.length === 0 && last && last.type !== 'p' && /^\s+/.test(line)) {
        // Línea con sangría tras un elemento de lista: continúa ese elemento.
        last.items[last.items.length - 1].push(...formatInline(' ' + line.trim()))
      } else {
        paragraph.push(line.trim())
      }
    }
  }
  flush()
  return blocks
}

function storage(): Storage | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

export function loadHistory(): ChatMessage[] {
  try {
    const raw = storage()?.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(
        (m): m is ChatMessage =>
          typeof m === 'object' &&
          m !== null &&
          ((m as ChatMessage).role === 'user' || (m as ChatMessage).role === 'assistant') &&
          typeof (m as ChatMessage).text === 'string',
      )
      .slice(-50)
  } catch {
    return []
  }
}

export function saveHistory(messages: ChatMessage[]): void {
  try {
    storage()?.setItem(STORAGE_KEY, JSON.stringify(messages.slice(-50)))
  } catch {
    // Sin almacenamiento la conversación solo dura mientras la pantalla siga abierta.
  }
}

export function clearAssistantHistory(): void {
  try {
    storage()?.removeItem(STORAGE_KEY)
  } catch {
    // Nada que limpiar.
  }
}
