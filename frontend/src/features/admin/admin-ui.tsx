import type { ReactNode } from 'react'

import type { Tone } from './admin-logic'

export function Badge({ tone = 'info', children }: { tone?: Tone; children: ReactNode }) {
  return <span className="adm-badge" data-tone={tone}>{children}</span>
}

export function LoadingBlock({ label = 'Cargando…' }: { label?: string }) {
  return (
    <div className="adm-skeleton" role="status" aria-label={label}>
      <span /><span /><span />
    </div>
  )
}

export function EmptyState({ title, text }: { title: string; text?: string }) {
  return (
    <div className="adm-empty" role="status">
      <strong>{title}</strong>
      {text && <p>{text}</p>}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="adm-empty" role="alert">
      <strong>No pudimos cargar la información</strong>
      <p>{message}</p>
      <button type="button" className="adm-btn" onClick={onRetry}>Reintentar</button>
    </div>
  )
}

interface PagerProps {
  summary: string
  page: number
  totalPages: number
  onChange: (page: number) => void
}

export function Pager({ summary, page, totalPages, onChange }: PagerProps) {
  return (
    <div className="adm-pager">
      <span role="status">{summary}</span>
      <div className="adm-actions">
        <button type="button" className="adm-btn adm-btn-ghost adm-btn-sm" disabled={page === 0} onClick={() => onChange(page - 1)}>Anterior</button>
        <button type="button" className="adm-btn adm-btn-ghost adm-btn-sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Siguiente</button>
      </div>
    </div>
  )
}

const ICONS: Record<string, ReactNode> = {
  home: <path d="M3 11.5 12 4l9 7.5M5.5 10v9.5h13V10" />,
  inbox: <path d="M4 13.5 6.5 5h11L20 13.5M4 13.5V19h16v-5.5M4 13.5h4.5a3.5 3.5 0 0 0 7 0H20" />,
  users: <path d="M16 19v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 17.5V19M10 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6M20 19v-1.5a3.5 3.5 0 0 0-2.5-3.3M15.5 5.2a3 3 0 0 1 0 5.6" />,
  receipt: <path d="M6 3h12v18l-3-2-3 2-3-2-3 2zM9.5 8h5M9.5 12h5" />,
  truck: <path d="M3 6h11v10H3zM14 9.5h4l3 3.5V16h-7M7 19a1.7 1.7 0 1 0 0-.01M17 19a1.7 1.7 0 1 0 0-.01" />,
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  logout: <path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h10" />,
  store: <path d="M4 10v10h16V10M3 4h18l1.5 5a3 3 0 0 1-5.2 1.7 3 3 0 0 1-4.6 0 3 3 0 0 1-4.6 0A3 3 0 0 1 1.5 9zM9.5 20v-5h5v5" />,
  scooter: <path d="M6 19a2.5 2.5 0 1 0 0-.01M18 19a2.5 2.5 0 1 0 0-.01M8.5 18.5h6.5l3-7-1.5-5H13M3 12h6l2 4" />,
  refresh: <path d="M20 11a8 8 0 0 0-14.5-4.5L4 8M4 4v4h4M4 13a8 8 0 0 0 14.5 4.5L20 16M20 20v-4h-4" />,
  alert: <path d="M12 9v4M12 17h.01M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0" />,
}

export type IconName = keyof typeof ICONS

export function Icon({ name }: { name: IconName }) {
  return <svg className="adm-icon" viewBox="0 0 24 24" aria-hidden="true">{ICONS[name]}</svg>
}
