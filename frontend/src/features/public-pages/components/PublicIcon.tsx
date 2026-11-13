import type { ReactNode } from 'react'

export type PublicIconName = 'pin' | 'search' | 'store' | 'basket' | 'check' | 'scooter' | 'menu' | 'bag' | 'calendar' | 'history'

const paths: Record<PublicIconName, ReactNode> = {
  pin: <><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
  search: <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></>,
  store: <><path d="M4 10v11h16V10M3 4h18l2 6c-1 3-4 3-5 0-1 3-4 3-6 0-1 3-4 3-6 0-1 3-4 3-5 0l2-6ZM9 21v-7h6v7" /><path className="icon-accent" d="m8 4-2 6m10-6 2 6" /></>,
  basket: <><path d="M3 9h18l-2 12H5L3 9ZM8 13v4m8-4v4m-4-4v4" /><path className="icon-accent" d="m6 9 4-7m8 7-4-7" /></>,
  check: <><rect x="3" y="3" width="18" height="18" rx="5" /><path className="icon-accent" d="m7 12 3 3 7-7" /></>,
  scooter: <><circle cx="6" cy="18" r="3" /><circle cx="19" cy="18" r="3" /><path d="M9 18h5l4-8-2-6h-4l2 6-4 4H5l1-6H2m13 10h1" /><path className="icon-accent" d="M1 11h3m-4 3h3" /></>,
  menu: <><rect x="5" y="2" width="16" height="20" rx="2" /><path d="M3 6h4M3 11h4M3 16h4m7-10v1m-5 8a5 5 0 0 1 10 0H9Zm-1 3h12" /></>,
  bag: <><path d="M5 7h14l3 15H2L5 7Zm3 1V5a4 4 0 0 1 8 0v3M9 12v5m6-5v5" /></>,
  calendar: <><rect x="3" y="5" width="18" height="17" rx="3" /><path d="M7 2v6m10-6v6M3 11h18" /><path className="icon-accent" d="M7 15h2m6 0h2M7 18h2m6 0h2" /></>,
  history: <><path d="M8 4H5a2 2 0 0 0-2 2v15a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-3M8 2h8v5H8V2Zm5 10h4m-4 6h4" /><path className="icon-accent" d="m6 12 1 1 2-2m-3 7 1 1 2-2" /></>,
}

export function PublicIcon({ name, className = '' }: { name: PublicIconName; className?: string }) {
  return <svg className={'public-icon ' + className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}

