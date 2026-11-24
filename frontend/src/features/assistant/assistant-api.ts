import { ApiError, GENERIC_ERROR } from '../auth/auth-api'
import { parseSseBuffer, toRequestMessages, type ChatMessage } from './assistant-logic'

const CHAT = '/api/customer/assistant/chat'

export interface StreamHandlers {
  onDelta: (text: string) => void
  /** Fallo con el flujo ya abierto (proveedor ocupado o caído). */
  onError: (code: string, message: string) => void
  onDone: () => void
}

/**
 * Envía la conversación y entrega la respuesta a medida que llega. Se usa fetch y no EventSource porque este no
 * admite POST ni la cabecera Authorization. Los fallos previos al flujo (400, 401, 429, 503) lanzan ApiError.
 */
export async function streamChat(
  token: string,
  messages: ChatMessage[],
  handlers: StreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  let response: Response
  try {
    response = await fetch(CHAT, {
      method: 'POST',
      signal,
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ messages: toRequestMessages(messages) }),
    })
  } catch (error) {
    if (signal.aborted) throw error
    throw new ApiError(0, GENERIC_ERROR)
  }

  if (!response.ok || !response.body) {
    let detail = GENERIC_ERROR
    let code: string | null = null
    try {
      const problem = (await response.json()) as { detail?: string; code?: string }
      detail = problem.detail ?? detail
      code = problem.code ?? null
    } catch {
      // Respuesta sin cuerpo JSON: se usa el texto genérico.
    }
    throw new ApiError(response.status, detail, {}, code)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false
  while (!finished) {
    let chunk: ReadableStreamReadResult<Uint8Array>
    try {
      chunk = await reader.read()
    } catch (error) {
      if (signal.aborted) throw error
      throw new ApiError(0, GENERIC_ERROR)
    }
    if (chunk.done) break
    buffer += decoder.decode(chunk.value, { stream: true })
    const { events, rest } = parseSseBuffer(buffer)
    buffer = rest
    for (const event of events) {
      if (event.type === 'delta') handlers.onDelta(event.text)
      else if (event.type === 'error') {
        handlers.onError(event.code, event.message)
        finished = true
      } else {
        handlers.onDone()
        finished = true
      }
    }
  }
  if (finished) await reader.cancel().catch(() => undefined)
}
