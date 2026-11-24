import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { ApiError } from '../auth/auth-api'
import { streamChat } from './assistant-api'
import {
  appendDelta,
  formatInline,
  friendlyHttpError,
  GREETING,
  loadHistory,
  MAX_LENGTH,
  saveHistory,
  SUGGESTIONS,
  UNAVAILABLE,
  type ChatMessage,
} from './assistant-logic'
import './assistant.css'

interface AssistantChatProps {
  /** Token de la sesión del cliente; el contenedor remonta el componente si cambia (otra sesión, otra conversación). */
  token: string
}

/** Botón flotante y panel de chat con el asistente de IA (spec 013). Solo se monta para clientes con sesión. */
export function AssistantChat({ token }: AssistantChatProps) {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>(() => loadHistory())
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const abort = useRef<AbortController | null>(null)
  const endRef = useRef<HTMLDivElement | null>(null)
  const inputRef = useRef<HTMLTextAreaElement | null>(null)

  useEffect(() => {
    saveHistory(messages.filter((m) => !m.notice))
  }, [messages])

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' })
  }, [messages, open])

  useEffect(() => {
    if (open) inputRef.current?.focus()
  }, [open])

  useEffect(() => () => abort.current?.abort(), [])

  async function send(text: string) {
    const clean = text.trim()
    if (!clean || sending) return
    const history: ChatMessage[] = [...messages.filter((m) => !m.notice), { role: 'user', text: clean }]
    setMessages(history)
    setDraft('')
    setSending(true)
    const controller = new AbortController()
    abort.current = controller
    try {
      await streamChat(
        token,
        history,
        {
          onDelta: (delta) => setMessages((current) => appendDelta(current, delta)),
          onError: (_code, message) =>
            setMessages((current) => [...current, { role: 'assistant', text: message, notice: true }]),
          onDone: () => undefined,
        },
        controller.signal,
      )
    } catch (error) {
      if (!controller.signal.aborted) {
        const message = error instanceof ApiError ? friendlyHttpError(error.status, error.message) : UNAVAILABLE
        setMessages((current) => [...current, { role: 'assistant', text: message, notice: true }])
      }
    } finally {
      abort.current = null
      setSending(false)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void send(draft)
  }

  function onKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape') setOpen(false)
  }

  function onInputKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void send(draft)
    }
  }

  function stop() {
    abort.current?.abort()
  }

  return (
    <div className="assistant" onKeyDown={onKeyDown}>
      {open && (
        <section className="assistant-panel" role="dialog" aria-label="Asistente de Mikuna">
          <header className="assistant-header">
            <span className="assistant-avatar" aria-hidden="true">
              <Sparkle />
            </span>
            <div className="assistant-title">
              <strong>Asistente Mikuna</strong>
              <span>Recomendaciones con datos reales</span>
            </div>
            <button type="button" className="assistant-close" aria-label="Cerrar el asistente" onClick={() => setOpen(false)}>
              <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </button>
          </header>

          <div className="assistant-messages" role="log" aria-live="polite" aria-label="Conversación">
            <p className="assistant-bubble assistant-bubble-bot">{GREETING}</p>
            {messages.map((message, index) => (
              <p
                key={index}
                className={
                  message.role === 'user'
                    ? 'assistant-bubble assistant-bubble-user'
                    : message.notice
                      ? 'assistant-bubble assistant-bubble-notice'
                      : 'assistant-bubble assistant-bubble-bot'
                }
              >
                {formatInline(message.text).map((piece, i) =>
                  piece.bold ? <strong key={i}>{piece.text}</strong> : <span key={i}>{piece.text}</span>,
                )}
              </p>
            ))}
            {sending && messages[messages.length - 1]?.role === 'user' && (
              <p className="assistant-bubble assistant-bubble-bot assistant-typing" aria-label="El asistente está escribiendo">
                <span />
                <span />
                <span />
              </p>
            )}
            {messages.length === 0 && (
              <div className="assistant-suggestions">
                {SUGGESTIONS.map((suggestion) => (
                  <button key={suggestion} type="button" onClick={() => void send(suggestion)}>
                    {suggestion}
                  </button>
                ))}
              </div>
            )}
            <div ref={endRef} />
          </div>

          <form className="assistant-form" onSubmit={submit}>
            <textarea
              ref={inputRef}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={onInputKeyDown}
              placeholder="Escribe tu pregunta…"
              aria-label="Escribe tu pregunta"
              maxLength={MAX_LENGTH}
              rows={1}
            />
            {sending ? (
              <button type="button" className="assistant-send" aria-label="Detener la respuesta" onClick={stop}>
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                  <rect x="6" y="6" width="12" height="12" rx="2" fill="currentColor" />
                </svg>
              </button>
            ) : (
              <button type="submit" className="assistant-send" aria-label="Enviar" disabled={draft.trim() === ''}>
                <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
                  <path d="M12 19V5M5 12l7-7 7 7" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </button>
            )}
          </form>
        </section>
      )}

      <button
        type="button"
        className="assistant-fab"
        aria-label={open ? 'Cerrar el asistente' : 'Abrir el asistente de Mikuna'}
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
      >
        {open ? (
          <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true">
            <path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        ) : (
          <Sparkle size={26} />
        )}
      </button>
    </div>
  )
}

function Sparkle({ size = 20 }: { size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden="true">
      <path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9L12 3z" fill="currentColor" />
      <path d="M18.5 15l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8.8-2.2z" fill="currentColor" />
    </svg>
  )
}
