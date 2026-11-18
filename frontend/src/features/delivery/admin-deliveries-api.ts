import { apiRequest } from '../auth/auth-api'

const BASE = '/api/admin/deliveries'

export interface Candidate {
  courierId: string
  name: string
  freeSince: string | null
}

export interface PendingDelivery {
  orderId: string
  status: 'READY' | 'ASSIGNED'
  restaurantName: string
  district: string
  readyAt: string
  courierId: string | null
  courierDisabled: boolean
  candidates: Candidate[]
}

function headers(token: string): HeadersInit {
  return { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }
}

export function listPending(token: string): Promise<PendingDelivery[]> {
  return apiRequest(BASE + '/pending', { headers: headers(token) })
}

export function assignDelivery(token: string, orderId: string, courierId: string): Promise<PendingDelivery> {
  return apiRequest(BASE + '/' + encodeURIComponent(orderId) + '/assign', {
    method: 'POST',
    headers: headers(token),
    body: JSON.stringify({ courierId }),
  })
}

export function releaseDelivery(token: string, orderId: string): Promise<PendingDelivery> {
  return apiRequest(BASE + '/' + encodeURIComponent(orderId) + '/release', { method: 'POST', headers: headers(token) })
}
