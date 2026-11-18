import { apiRequest } from '../auth/auth-api'
import type { OrderStatus } from '../restaurant/orders-logic'

const ORDERS = '/api/customer/orders'

export type LocationUnavailableReason = 'NOT_PICKED_UP' | 'FINISHED' | 'NO_LOCATION_YET'

export interface TrackingStage {
  status: OrderStatus
  at: string
}

export interface CourierLocation {
  latitude: number
  longitude: number
  updatedAt: string
}

/** Seguimiento del pedido propio (RF-27, RF-28): la ubicación solo llega con el pedido Recogido. */
export interface Tracking {
  orderId: string
  status: OrderStatus
  stages: TrackingStage[]
  courierLocation: CourierLocation | null
  locationUnavailableReason: LocationUnavailableReason | null
}

export function getTracking(token: string, orderId: string): Promise<Tracking> {
  return apiRequest(ORDERS + '/' + encodeURIComponent(orderId) + '/tracking', {
    headers: { Authorization: 'Bearer ' + token },
  })
}
