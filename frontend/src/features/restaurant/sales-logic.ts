import type { HistoryItem } from '../history/history-api'
import type { OrderStatus } from './orders-logic'

/** Pedidos que ya cuentan como venta: el restaurante los aceptó (y el pago se cobró) o ya se entregaron. */
export const SALE_STATUSES: readonly OrderStatus[] = ['ACCEPTED', 'PREPARING', 'READY', 'ASSIGNED', 'PICKED_UP', 'DELIVERED']
/** Pedidos que no se concretaron. */
export const LOST_STATUSES: readonly OrderStatus[] = ['REJECTED', 'CANCELLED']

export type RangePreset = 'today' | 'week' | 'month'

export const RANGE_LABELS: Record<RangePreset, string> = { today: 'Hoy', week: 'Últimos 7 días', month: 'Últimos 30 días' }

/** Fecha local aaaa-mm-dd (la que entiende el historial). */
export function isoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export function rangeFor(preset: RangePreset, now: Date): { from: string; to: string } {
  const days = preset === 'today' ? 0 : preset === 'week' ? 6 : 29
  const start = new Date(now)
  start.setDate(start.getDate() - days)
  return { from: isoDate(start), to: isoDate(now) }
}

export interface SalesSummary {
  /** Valor de los productos vendidos; no incluye envío ni comisión del servicio. */
  sales: number
  orders: number
  average: number
  lost: number
}

export function summarize(items: readonly HistoryItem[]): SalesSummary {
  let sales = 0
  let orders = 0
  let lost = 0
  for (const item of items) {
    if (SALE_STATUSES.includes(item.status)) {
      orders += 1
      sales += item.totals?.subtotal ?? 0
    } else if (LOST_STATUSES.includes(item.status)) lost += 1
  }
  return { sales: Math.round(sales * 100) / 100, orders, average: orders ? Math.round((sales / orders) * 100) / 100 : 0, lost }
}
