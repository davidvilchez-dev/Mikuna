import { panelHeaders } from './branch-storage'
import { apiRequest } from '../auth/auth-api'
import type { HistoryItem, HistoryPage } from '../history/history-api'

const MAX_PAGES = 10

/** Todos los pedidos del restaurante en el rango (aaaa-mm-dd, ambos inclusive), de 100 en 100 y hasta 1000. */
export async function fetchSalesHistory(token: string, from: string, to: string): Promise<HistoryItem[]> {
  const items: HistoryItem[] = []
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const query = `?size=100&page=${page}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
    const result = await apiRequest<HistoryPage>('/api/restaurant/orders/history' + query, { headers: panelHeaders(token) })
    items.push(...result.items)
    if (page + 1 >= result.totalPages) break
  }
  return items
}
