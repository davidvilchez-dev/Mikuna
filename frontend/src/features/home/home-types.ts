import type { RefObject } from 'react'

export type HomeAssetId =
  | 'logo' | 'wings' | 'pizzaWhole' | 'heroBurger' | 'categoryBurger'
  | 'chicken' | 'pizzaSlice' | 'peruvianFood' | 'dessert' | 'drink'

export interface VisualAsset {
  readonly id: HomeAssetId
  readonly src: string
  readonly alternativeText: string
  readonly width: number
  readonly height: number
  readonly provenance: string
  readonly sha256: string
}

export interface NavigationItem {
  readonly id: string
  readonly label: string
  readonly order: number
  readonly behavior: 'home' | 'page' | 'coming-soon'
  readonly appearance: 'normal' | 'active' | 'primary' | 'secondary'
}

export type ActionItem = NavigationItem
export type CategoryId = 'hamburguesas' | 'pollo' | 'pizza' | 'comida-peruana' | 'postres' | 'bebidas'

export interface CategoryItem {
  readonly id: CategoryId
  readonly label: string
  readonly imageId: HomeAssetId
  readonly order: number
  readonly behavior: 'coming-soon'
}

export type ShowComingSoon = (trigger: HTMLButtonElement) => void
export type PauseReason = 'pointer' | 'focus' | 'document-hidden'
export type MenuToggleRef = RefObject<HTMLButtonElement | null>

export const COMING_SOON_MESSAGE = 'Esta función estará disponible próximamente'
export const NOTICE_DURATION_MS = 5_000
