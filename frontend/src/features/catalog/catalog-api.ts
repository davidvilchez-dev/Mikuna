import { apiRequest } from '../auth/auth-api'

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

/** Situación de atención de un restaurante visible. */
export type Attention = 'OPEN' | 'OUTSIDE_HOURS' | 'PAUSED'

export const attentionLabels: Record<Attention, string> = {
  OPEN: 'Abierto',
  OUTSIDE_HOURS: 'Fuera de horario',
  PAUSED: 'Sin atención por ahora',
}

// ---------- Gestión del catálogo propio (rol RESTAURANT, con token) ----------

export interface Product {
  id: string
  name: string
  description: string | null
  category: string
  price: number
  imageUrl: string
  available: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface ProductData {
  name: string
  category: string
  description: string
  price: string
}

const PRODUCTS = '/api/restaurant/me/products'

function auth(token: string): HeadersInit {
  return { Authorization: 'Bearer ' + token }
}

/** Parte JSON «product» + parte binaria «image» (la versión solo se envía al editar). */
function productForm(data: ProductData, version: number | null, image: File | null): FormData {
  const form = new FormData()
  const body = {
    name: data.name.trim(),
    category: data.category.trim(),
    description: data.description.trim() || null,
    price: Number(data.price.trim()),
    ...(version === null ? {} : { version }),
  }
  form.append('product', new Blob([JSON.stringify(body)], { type: 'application/json' }))
  if (image) form.append('image', image)
  return form
}

export function listMyProducts(token: string, page: number): Promise<PageResponse<Product>> {
  return apiRequest(`${PRODUCTS}?page=${page}&size=10`, { headers: auth(token) })
}

export function createProduct(token: string, data: ProductData, image: File): Promise<Product> {
  return apiRequest(PRODUCTS, { method: 'POST', headers: auth(token), body: productForm(data, null, image) })
}

export function updateProduct(token: string, id: string, version: number, data: ProductData, image: File | null): Promise<Product> {
  return apiRequest(`${PRODUCTS}/${id}`, { method: 'PUT', headers: auth(token), body: productForm(data, version, image) })
}

export function setProductAvailability(token: string, id: string, available: boolean, version: number): Promise<Product> {
  return apiRequest(`${PRODUCTS}/${id}/availability`, {
    method: 'PUT',
    headers: auth(token),
    body: JSON.stringify({ available, version }),
  })
}

export function withdrawProduct(token: string, id: string, version: number): Promise<void> {
  return apiRequest(`${PRODUCTS}/${id}?version=${version}`, { method: 'DELETE', headers: auth(token) })
}

// ---------- Consulta pública (sin sesión: nunca se envía Authorization) ----------

export interface PublicRestaurant {
  id: string
  name: string
  category: string
  description: string | null
  zone: string
  attention: Attention
  logoUrl: string | null
  coverUrl: string | null
}

export interface PublicProduct {
  id: string
  name: string
  description: string | null
  category: string
  price: number
  imageUrl: string
}

export interface Catalog {
  restaurant: PublicRestaurant
  products: PageResponse<PublicProduct>
}

export interface Filters {
  categories: string[]
  zones: string[]
}

export interface SearchCriteria {
  name: string
  category: string
  zone: string
  page: number
}

const PUBLIC = '/api/public/restaurants'

export function searchRestaurants(criteria: SearchCriteria): Promise<PageResponse<PublicRestaurant>> {
  const params = new URLSearchParams({ page: String(criteria.page), size: '12' })
  if (criteria.name.trim()) params.set('name', criteria.name.trim())
  if (criteria.category) params.set('category', criteria.category)
  if (criteria.zone) params.set('zone', criteria.zone)
  return apiRequest(`${PUBLIC}?${params.toString()}`)
}

export function getFilters(): Promise<Filters> {
  return apiRequest(`${PUBLIC}/filters`)
}

export function getCatalog(restaurantId: string, page: number): Promise<Catalog> {
  return apiRequest(`${PUBLIC}/${encodeURIComponent(restaurantId)}/catalog?page=${page}&size=24`)
}
