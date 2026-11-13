import chef from '../../assets/public-pages/restaurant-chef.png'
import courier from '../../assets/public-pages/courier.png'
import customer from '../../assets/public-pages/customer.png'
import type { PublicIconName } from './components/PublicIcon'

export const publicPhotos = {
  chef: { src: chef, alt: 'Chef peruana cerrando un pedido junto a una bolsa de Mikuna' },
  courier: { src: courier, alt: 'Repartidor de Mikuna con casco junto a su scooter y mochila de entregas' },
  customer: { src: customer, alt: 'Mujer abriendo su pedido de Mikuna en casa' },
} as const

export interface FeatureItem {
  readonly icon: PublicIconName
  readonly title: string
  readonly description: string
}

export const orderSteps: readonly FeatureItem[] = [
  { icon: 'store', title: 'Elige tu restaurante', description: 'Explora las opciones y encuentra tus platos favoritos.' },
  { icon: 'basket', title: 'Arma tu pedido', description: 'Agrega lo que te gusta y revisa tu carrito.' },
  { icon: 'check', title: 'Confirma tu pedido', description: 'Completa tu dirección y revisa el pago.' },
  { icon: 'scooter', title: 'Recibe y disfruta', description: 'Consulta el estado y espera tu pedido.' },
]

export const restaurantFeatures: readonly FeatureItem[] = [
  { icon: 'menu', title: 'Administra tu carta', description: 'Actualiza tus productos, precios y disponibilidad.' },
  { icon: 'bag', title: 'Gestiona tus pedidos', description: 'Recibe solicitudes y acompaña su preparación.' },
  { icon: 'calendar', title: 'Consulta tu historial', description: 'Revisa los pedidos de tu restaurante.' },
]

export const courierFeatures: readonly FeatureItem[] = [
  { icon: 'calendar', title: 'Activa tu disponibilidad', description: 'Indica cuándo puedes recibir entregas.' },
  { icon: 'scooter', title: 'Gestiona cada entrega', description: 'Acepta solicitudes y actualiza su estado.' },
  { icon: 'history', title: 'Consulta tu historial', description: 'Revisa las entregas que realizaste.' },
]

