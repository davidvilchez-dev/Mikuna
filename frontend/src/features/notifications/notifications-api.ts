import { apiRequest } from '../auth/auth-api'

const BASE = '/api/notifications'

export type NotificationType = 'NEW_ORDER' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'PREPARING' | 'READY'
  | 'ASSIGNED' | 'PICKED_UP' | 'DELIVERED'

export interface Notice {
  id: string
  orderId: string
  type: NotificationType
  message: string
  createdAt: string
  read: boolean
}

export interface NoticePage {
  items: Notice[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

function headers(token: string): HeadersInit {
  return { Authorization: 'Bearer ' + token }
}

export function listNotices(token: string, page: number, unreadOnly: boolean): Promise<NoticePage> {
  return apiRequest(`${BASE}?page=${page}&size=20&unreadOnly=${unreadOnly}`, { headers: headers(token) })
}

export function getUnreadCount(token: string): Promise<{ unread: number }> {
  return apiRequest(BASE + '/unread-count', { headers: headers(token) })
}

export function markNoticeRead(token: string, id: string): Promise<void> {
  return apiRequest(BASE + '/' + encodeURIComponent(id) + '/read', { method: 'POST', headers: headers(token) })
}

export function markAllNoticesRead(token: string): Promise<void> {
  return apiRequest(BASE + '/read-all', { method: 'POST', headers: headers(token) })
}
