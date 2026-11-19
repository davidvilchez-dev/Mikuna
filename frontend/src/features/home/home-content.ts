import type { ActionItem, CategoryItem } from './home-types'

export const heroContent = {
  title: 'Tu antojo, en camino.',
  description: 'Descubre restaurantes cerca de ti y disfruta tus favoritos sin salir de casa.',
} as const

export const mainNavigation = [
  { to: '/restaurantes', label: 'Restaurantes' },
  { to: '/como-funciona', label: 'Cómo funciona' },
] as const

export const partnerNavigation = [
  { to: '/para-restaurantes', label: 'Para restaurantes', hint: 'Vende más con tu local en Mikuna' },
  { to: '/se-repartidor', label: 'Sé repartidor', hint: 'Genera ingresos entregando pedidos' },
] as const

export const heroHighlights = [
  { icon: 'scooter', title: 'Entrega rápida', text: 'Tu pedido sale en cuanto está listo.' },
  { icon: 'store', title: 'Restaurantes locales', text: 'Lo mejor de Ayacucho en un solo lugar.' },
  { icon: 'pin', title: 'Sigue tu pedido', text: 'Sabe dónde va tu comida en todo momento.' },
] as const

export const heroActions: readonly ActionItem[] = [
  { id: 'crear-cuenta', label: 'Crear cuenta', order: 1, behavior: 'route', to: '/registro', appearance: 'secondary' },
]

export const categories: readonly CategoryItem[] = [
  { id: 'hamburguesas', label: 'Hamburguesas', imageId: 'categoryBurger', order: 1, behavior: 'coming-soon' },
  { id: 'pollo', label: 'Pollo', imageId: 'chicken', order: 2, behavior: 'coming-soon' },
  { id: 'pizza', label: 'Pizza', imageId: 'pizzaSlice', order: 3, behavior: 'coming-soon' },
  { id: 'comida-peruana', label: 'Comida peruana', imageId: 'peruvianFood', order: 4, behavior: 'coming-soon' },
  { id: 'postres', label: 'Postres', imageId: 'dessert', order: 5, behavior: 'coming-soon' },
  { id: 'bebidas', label: 'Bebidas', imageId: 'drink', order: 6, behavior: 'coming-soon' },
]
