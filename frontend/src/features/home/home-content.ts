import type { ActionItem, CategoryItem, NavigationItem } from './home-types'

export const heroContent = {
  title: 'Tu antojo, en camino.',
  description: 'Descubre restaurantes cerca de ti y disfruta tus favoritos sin salir de casa.',
} as const

export const navigation: readonly NavigationItem[] = [
  { id: 'inicio', label: 'Inicio', order: 1, behavior: 'home', appearance: 'active' },
  { id: 'sobre-nosotros', label: 'Sobre nosotros', order: 2, behavior: 'page', appearance: 'normal' },
  { id: 'como-funciona', label: 'Cómo funciona', order: 3, behavior: 'page', appearance: 'normal' },
  { id: 'para-restaurantes', label: 'Para restaurantes', order: 4, behavior: 'page', appearance: 'normal' },
  { id: 'se-repartidor', label: 'Sé repartidor', order: 5, behavior: 'page', appearance: 'normal' },
]

export const headerAction: ActionItem = {
  id: 'pedir-ahora', label: 'Pedir ahora', order: 6, behavior: 'coming-soon', appearance: 'primary',
}

export const heroActions: readonly ActionItem[] = [
  { id: 'empezar-a-pedir', label: 'Empezar a pedir', order: 1, behavior: 'coming-soon', appearance: 'primary' },
  { id: 'crear-cuenta', label: 'Crear cuenta', order: 2, behavior: 'route', to: '/registro', appearance: 'secondary' },
]

export const categories: readonly CategoryItem[] = [
  { id: 'hamburguesas', label: 'Hamburguesas', imageId: 'categoryBurger', order: 1, behavior: 'coming-soon' },
  { id: 'pollo', label: 'Pollo', imageId: 'chicken', order: 2, behavior: 'coming-soon' },
  { id: 'pizza', label: 'Pizza', imageId: 'pizzaSlice', order: 3, behavior: 'coming-soon' },
  { id: 'comida-peruana', label: 'Comida peruana', imageId: 'peruvianFood', order: 4, behavior: 'coming-soon' },
  { id: 'postres', label: 'Postres', imageId: 'dessert', order: 5, behavior: 'coming-soon' },
  { id: 'bebidas', label: 'Bebidas', imageId: 'drink', order: 6, behavior: 'coming-soon' },
]
