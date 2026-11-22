const PATHS = {
  store: 'M4 10v10h16V10M3 4h18l1.5 5a3 3 0 0 1-5.2 1.7 3 3 0 0 1-4.6 0 3 3 0 0 1-4.6 0A3 3 0 0 1 1.5 9zM9.5 20v-5h5v5',
  cart: 'M3 4h2.2l2.1 10.2a1 1 0 0 0 1 .8h8.4a1 1 0 0 0 1-.7L20 8H6M9.5 19.5a.5.5 0 1 0 0 .01M16.5 19.5a.5.5 0 1 0 0 .01',
  pin: 'M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21zM12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  receipt: 'M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6',
  bell: 'M6 17h12l-1.5-2.2V10a4.5 4.5 0 0 0-9 0v4.8zM10 20a2 2 0 0 0 4 0',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4.5 20a7.5 7.5 0 0 1 15 0',
  logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 16l-4-4 4-4M6 12h10',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM20 20l-4-4',
  scooter: 'M6 19a2.5 2.5 0 1 0 0-.01M18 19a2.5 2.5 0 1 0 0-.01M8.5 18.5h6.5l3-7-1.5-5H13M3 12h6l2 4',
  box: 'M4 7.5 12 3l8 4.5v9L12 21l-8-4.5zM4 7.5 12 12l8-4.5M12 12v9',
  menu: 'M4 7h16M4 12h16M4 17h16',
  arrow: 'M5 12h14M13 6l6 6-6 6',
  back: 'M15 6l-6 6 6 6',
  home: 'M3 11l9-8 9 8M5 9.5V20h5v-6h4v6h5V9.5',
  chevron: 'M6 9l6 6 6-6',
  phone: 'M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z',
  navigate: 'M3 11l18-8-8 18-2-8z',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  clock: 'M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  camera: 'M4 8h3l1.5-2h7L17 8h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1zM12 17a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z',
} as const

export type AppIconName = keyof typeof PATHS

/** Icono de trazo para la interfaz con sesión; decorativo, el texto lo acompaña. */
export function AppIcon({ name, className }: { name: AppIconName; className?: string }) {
  return (
    <svg className={className ?? 'app-icon'} viewBox="0 0 24 24" aria-hidden="true">
      <path d={PATHS[name]} />
    </svg>
  )
}
