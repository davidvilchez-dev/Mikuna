import { apiRequest } from '../auth/auth-api'
import type { Attention } from '../catalog/catalog-api'

const ADDRESSES = '/api/customer/addresses'
const CART = '/api/customer/cart'

/** Aviso a la pantalla de que el carrito o las direcciones cambiaron (el encabezado vuelve a leer su contador). */
export const CART_CHANGED = 'mikuna:cart-changed'

function notifyChange<T>(request: Promise<T>): Promise<T> {
  return request.then((value) => {
    window.dispatchEvent(new Event(CART_CHANGED))
    return value
  })
}

function auth(token: string): HeadersInit {
  return { Authorization: 'Bearer ' + token }
}

// ---------- Direcciones (rol CUSTOMER, con token) ----------

export interface Address {
  id: string
  label: string | null
  street: string
  district: string
  reference: string | null
  contactPhone: string | null
  /** Punto exacto marcado en el mapa; las direcciones anteriores no lo tienen. */
  latitude: number | null
  longitude: number | null
  createdAt: string
  updatedAt: string
}

export interface AddressData {
  label: string
  street: string
  district: string
  reference: string
  contactPhone: string
  latitude: number | null
  longitude: number | null
}

function addressBody(data: AddressData): string {
  return JSON.stringify({
    label: data.label.trim() || null,
    street: data.street.trim(),
    district: data.district.trim(),
    reference: data.reference.trim() || null,
    contactPhone: data.contactPhone.trim() || null,
    latitude: data.latitude,
    longitude: data.longitude,
  })
}

export function listAddresses(token: string): Promise<Address[]> {
  return apiRequest(ADDRESSES, { headers: auth(token) })
}

export function createAddress(token: string, data: AddressData): Promise<Address> {
  return notifyChange(apiRequest(ADDRESSES, { method: 'POST', headers: auth(token), body: addressBody(data) }))
}

export function updateAddress(token: string, id: string, data: AddressData): Promise<Address> {
  return notifyChange(apiRequest(`${ADDRESSES}/${id}`, { method: 'PUT', headers: auth(token), body: addressBody(data) }))
}

export function deleteAddress(token: string, id: string): Promise<void> {
  return notifyChange(apiRequest(`${ADDRESSES}/${id}`, { method: 'DELETE', headers: auth(token) }))
}

// ---------- Carrito (rol CUSTOMER, con token) ----------

export type LineReason = 'PRODUCT_UNAVAILABLE' | 'PRODUCT_WITHDRAWN' | 'RESTAURANT_UNAVAILABLE'

export interface CartLine {
  id: string
  productId: string
  name: string
  imageUrl: string
  unitPrice: number
  quantity: number
  /** `null` si la línea no es elegible: no suma a los importes. */
  lineTotal: number | null
  available: boolean
  reason: LineReason | null
}

/** Importes calculados por el servidor; el navegador nunca los calcula ni los envía. */
export interface Totals {
  subtotal: number
  deliveryFee: number | null
  commission: number | null
  total: number | null
  pending: 'ADDRESS' | 'EMPTY' | null
}

export interface Cart {
  restaurant: { id: string; name: string; attention: Attention | null } | null
  lines: CartLine[]
  address: { id: string; street: string; district: string } | null
  totals: Totals
  notices: string[]
}

export const lineReasonLabels: Record<LineReason, string> = {
  PRODUCT_UNAVAILABLE: 'Producto no disponible',
  PRODUCT_WITHDRAWN: 'Producto retirado',
  RESTAURANT_UNAVAILABLE: 'El restaurante no recibe pedidos ahora',
}

export function getCart(token: string): Promise<Cart> {
  return apiRequest(CART, { headers: auth(token) })
}

export function addToCart(token: string, productId: string, quantity: number): Promise<Cart> {
  return notifyChange(apiRequest(`${CART}/lines`, { method: 'POST', headers: auth(token), body: JSON.stringify({ productId, quantity }) }))
}

export function changeQuantity(token: string, lineId: string, quantity: number): Promise<Cart> {
  return notifyChange(apiRequest(`${CART}/lines/${lineId}`, { method: 'PUT', headers: auth(token), body: JSON.stringify({ quantity }) }))
}

export function removeLine(token: string, lineId: string): Promise<Cart> {
  return notifyChange(apiRequest(`${CART}/lines/${lineId}`, { method: 'DELETE', headers: auth(token) }))
}

export function clearCart(token: string): Promise<Cart> {
  return notifyChange(apiRequest(CART, { method: 'DELETE', headers: auth(token) }))
}

export function selectAddress(token: string, addressId: string): Promise<Cart> {
  return notifyChange(apiRequest(`${CART}/address`, { method: 'PUT', headers: auth(token), body: JSON.stringify({ addressId }) }))
}
