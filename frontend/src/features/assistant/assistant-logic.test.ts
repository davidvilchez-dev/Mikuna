import { afterEach, describe, expect, it } from 'vitest'
import {
  appendDelta,
  clearAssistantHistory,
  formatBlocks,
  formatInline,
  friendlyHttpError,
  loadHistory,
  parseSseBuffer,
  saveHistory,
  STORAGE_KEY,
  toRequestMessages,
  trimHistory,
  type ChatMessage,
} from './assistant-logic'

afterEach(() => sessionStorage.clear())

function conversation(count: number): ChatMessage[] {
  return Array.from({ length: count }, (_, i) => ({ role: i % 2 === 0 ? 'user' : 'assistant', text: `m${i}` }))
}

describe('historial', () => {
  it('conserva solo los últimos 10 mensajes reales', () => {
    const trimmed = trimHistory(conversation(14))
    expect(trimmed).toHaveLength(10)
    expect(trimmed[0].text).toBe('m4')
    expect(trimmed[9].text).toBe('m13')
  })

  it('no cuenta los avisos de la interfaz ni los mensajes vacíos', () => {
    const messages: ChatMessage[] = [
      { role: 'user', text: 'hola' },
      { role: 'assistant', text: 'El asistente está ocupado', notice: true },
      { role: 'assistant', text: '   ' },
    ]
    expect(trimHistory(messages)).toEqual([{ role: 'user', text: 'hola' }])
  })

  it('la petición recorta espacios y limita cada mensaje a 500 caracteres', () => {
    const request = toRequestMessages([{ role: 'user', text: `  ${'a'.repeat(600)}  ` }])
    expect(request).toHaveLength(1)
    expect(request[0].text).toHaveLength(500)
  })

  it('appendDelta añade al último mensaje del asistente o crea uno nuevo', () => {
    const afterUser = appendDelta([{ role: 'user', text: 'hola' }], 'Ho')
    expect(afterUser).toEqual([{ role: 'user', text: 'hola' }, { role: 'assistant', text: 'Ho' }])
    expect(appendDelta(afterUser, 'la')[1].text).toBe('Hola')
  })

  it('appendDelta no mezcla el texto con un aviso de la interfaz', () => {
    const result = appendDelta([{ role: 'assistant', text: 'aviso', notice: true }], 'nuevo')
    expect(result).toHaveLength(2)
    expect(result[1]).toEqual({ role: 'assistant', text: 'nuevo' })
  })
})

describe('lector de SSE', () => {
  it('reconoce delta, done y error', () => {
    const { events, rest } = parseSseBuffer(
      'event:delta\ndata:{"text":"Hola "}\n\nevent:delta\ndata:{"text":"mundo"}\n\nevent:done\ndata:{}\n\n',
    )
    expect(events).toEqual([
      { type: 'delta', text: 'Hola ' },
      { type: 'delta', text: 'mundo' },
      { type: 'done' },
    ])
    expect(rest).toBe('')

    const error = parseSseBuffer('event:error\ndata:{"code":"ASSISTANT_BUSY","message":"Ocupado"}\n\n')
    expect(error.events).toEqual([{ type: 'error', code: 'ASSISTANT_BUSY', message: 'Ocupado' }])
  })

  it('un evento partido entre dos lecturas se completa con la siguiente', () => {
    const first = parseSseBuffer('event:delta\ndata:{"text":"Ho')
    expect(first.events).toEqual([])
    const second = parseSseBuffer(first.rest + 'la"}\n\nevent:done\ndata:{}\n\n')
    expect(second.events).toEqual([{ type: 'delta', text: 'Hola' }, { type: 'done' }])
  })

  it('acepta saltos de línea CRLF y datos con acentos', () => {
    const { events } = parseSseBuffer('event: delta\r\ndata: {"text":"Ají"}\r\n\r\n')
    expect(events).toEqual([{ type: 'delta', text: 'Ají' }])
  })

  it('ignora bloques sin datos, con JSON roto o desconocidos', () => {
    const { events } = parseSseBuffer(': comentario\n\nevent:delta\ndata:{roto\n\nevent:otro\ndata:{"x":1}\n\n')
    expect(events).toEqual([])
  })

  it('un error sin campos usa el texto de indisponibilidad', () => {
    const { events } = parseSseBuffer('event:error\ndata:{}\n\n')
    expect(events[0]).toMatchObject({ type: 'error', code: 'ASSISTANT_UNAVAILABLE' })
  })
})

describe('textos de error', () => {
  it('traduce cada fallo a un mensaje amable', () => {
    expect(friendlyHttpError(0, 'x')).toMatch(/conexión/)
    expect(friendlyHttpError(401, 'x')).toMatch(/sesión venció/)
    expect(friendlyHttpError(429, 'Intenta en 20 segundos')).toBe('Intenta en 20 segundos')
    expect(friendlyHttpError(503, 'trazas internas')).toMatch(/no está disponible/)
    expect(friendlyHttpError(503, 'trazas internas')).not.toMatch(/trazas/)
  })
})

describe('formato', () => {
  const plain = (text: string) => ({ text, bold: false, italic: false })

  it('convierte **negrita** y *cursiva* en piezas y deja el resto igual', () => {
    expect(formatInline('Prueba **Ensalada** hoy')).toEqual([
      plain('Prueba '),
      { text: 'Ensalada', bold: true, italic: false },
      plain(' hoy'),
    ])
    expect(formatInline('S/ 22.00 · *mozzarella y jamón*')).toEqual([
      plain('S/ 22.00 · '),
      { text: 'mozzarella y jamón', bold: false, italic: true },
    ])
    expect(formatInline('sin formato')).toEqual([plain('sin formato')])
    expect(formatInline('')).toEqual([])
  })

  it('quita los asteriscos sueltos', () => {
    expect(formatInline('a * b')).toEqual([plain('a  b')])
    expect(formatInline('**')).toEqual([])
  })

  it('arma párrafos y listas con viñeta, sea con * o con -', () => {
    const blocks = formatBlocks('Opciones:\n\n* **Pizza americana** — S/ 22.00\n- **Pizza argentina** — S/ 24.00\n\nCierre')
    expect(blocks.map((b) => b.type)).toEqual(['p', 'ul', 'p'])
    const list = blocks[1]
    expect(list.type === 'ul' && list.items).toHaveLength(2)
    expect(list.type === 'ul' && list.items[0][0]).toEqual({ text: 'Pizza americana', bold: true, italic: false })
  })

  it('una línea con sangría continúa el elemento anterior', () => {
    const blocks = formatBlocks('* **Pizza** — S/ 22.00\n  *(Mozzarella y jamón)*')
    expect(blocks).toHaveLength(1)
    const list = blocks[0]
    expect(list.type === 'ul' && list.items).toHaveLength(1)
    expect(list.type === 'ul' && list.items[0].some((piece) => piece.italic)).toBe(true)
  })

  it('reconoce listas numeradas y no pierde texto en construcción', () => {
    const blocks = formatBlocks('1. **Elige** un restaurante\n2) Agrega al carrito')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].type).toBe('ol')
    expect(formatBlocks('**Piz')).toEqual([{ type: 'p', pieces: [plain('Piz')] }])
    expect(formatBlocks('')).toEqual([])
  })
})


describe('almacenamiento', () => {
  it('guarda, recupera y borra la conversación', () => {
    saveHistory(conversation(3))
    expect(loadHistory()).toHaveLength(3)
    clearAssistantHistory()
    expect(loadHistory()).toEqual([])
  })

  it('descarta datos corruptos o con forma inesperada', () => {
    sessionStorage.setItem(STORAGE_KEY, '{no es json')
    expect(loadHistory()).toEqual([])
    sessionStorage.setItem(STORAGE_KEY, '{"a":1}')
    expect(loadHistory()).toEqual([])
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify([{ role: 'system', text: 'x' }, { role: 'user', text: 'ok' }]))
    expect(loadHistory()).toEqual([{ role: 'user', text: 'ok' }])
  })
})
