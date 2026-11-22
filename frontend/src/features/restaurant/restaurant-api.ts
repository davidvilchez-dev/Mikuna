import { apiRequest } from '../auth/auth-api'
import { panelHeaders } from './branch-storage'

export type RestaurantStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface OpeningHour {
  day: string
  opensAt: string | null
  closesAt: string | null
}

export interface RestaurantInput {
  name: string
  category: string
  description: string
  address: string
  zone: string
  phone: string
  /** Ubicación del local en el mapa; opcional. */
  latitude?: number | null
  longitude?: number | null
}

export interface Restaurant extends RestaurantInput {
  id: string
  status: RestaurantStatus
  submittedAt: string
  rejectionReason: string | null
  resolvedAt: string | null
  available: boolean
  schedule: OpeningHour[]
  acceptingOrders: boolean
  logoUrl: string | null
  coverUrl: string | null
}

export interface AdminRestaurant extends Restaurant {
  ownerName: string
  ownerEmail: string
}

const ME = '/api/restaurant/me'
const ADMIN = '/api/admin/restaurants'

function call<T>(url: string, token: string, method: string, body?: unknown): Promise<T> {
  return apiRequest<T>(url, {
    method,
    headers: url.startsWith(ME) ? panelHeaders(token) : { Authorization: 'Bearer ' + token },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
}

export function getMyRestaurant(token: string): Promise<Restaurant> {
  return call(ME, token, 'GET')
}

/** Sucursales de la cuenta, de la más antigua (principal) a la más reciente. */
export function listBranches(token: string): Promise<Restaurant[]> {
  return call(ME + '/branches', token, 'GET')
}

export function addBranch(token: string, input: RestaurantInput): Promise<Restaurant> {
  return call(ME + '/branches', token, 'POST', input)
}

export function submitRestaurant(token: string, input: RestaurantInput): Promise<Restaurant> {
  return call(ME, token, 'POST', input)
}

export function updateMyRestaurant(token: string, input: RestaurantInput): Promise<Restaurant> {
  return call(ME, token, 'PUT', input)
}

export function resubmitMyRestaurant(token: string): Promise<Restaurant> {
  return call(ME + '/resubmit', token, 'POST')
}

export function updateSchedule(token: string, days: OpeningHour[]): Promise<Restaurant> {
  return call(ME + '/schedule', token, 'PUT', { days })
}

export function updateImages(token: string, logo: File | null, cover: File | null): Promise<Restaurant> {
  const form = new FormData()
  if (logo) form.append('logo', logo)
  if (cover) form.append('cover', cover)
  return apiRequest<Restaurant>(ME + '/images', { method: 'PUT', headers: panelHeaders(token), body: form })
}

export function setAvailability(token: string, available: boolean): Promise<Restaurant> {
  return call(ME + '/availability', token, 'PUT', { available })
}

export function listRestaurants(token: string, status: RestaurantStatus): Promise<AdminRestaurant[]> {
  return call(ADMIN + '?status=' + status, token, 'GET')
}

export function approveRestaurant(token: string, id: string): Promise<AdminRestaurant> {
  return call(ADMIN + '/' + id + '/approve', token, 'POST')
}

export function rejectRestaurant(token: string, id: string, reason: string): Promise<AdminRestaurant> {
  return call(ADMIN + '/' + id + '/reject', token, 'POST', { reason })
}
