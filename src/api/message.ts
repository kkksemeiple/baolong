/**
 * ============================================================
 * 接口模块三：消息与通知（MS）
 * 对应需求：MS-01 任务会话 / MS-02 通知中心
 * ============================================================
 */
import { get, post } from './request'
import type { Conversation, CursorPage, Message, NotifType, Notification } from './types'

/* ---------------- MS-01 任务会话 ---------------- */

/** 我的会话列表（按最后消息时间倒序） */
export function fetchConversations() {
  return get<Conversation[]>('/conversations')
}

/**
 * 创建或获取与某任务的会话
 * 只有任务双方（委托方与接单方）才能开启会话，第三方调用返回 403。
 */
export function openConversation(taskId: number) {
  return get<Conversation>(`/conversations/by-task/${taskId}`)
}

/**
 * 会话消息（游标分页）
 * @param cursor 上一页最后一条消息的游标，首次不传
 */
export function fetchMessages(conversationId: number, cursor?: string, size = 20) {
  return get<CursorPage<Message>>(`/conversations/${conversationId}/messages`, { cursor, size })
}

/** 发送消息 */
export function sendMessage(
  conversationId: number,
  data: { content: string; contentType?: 'TEXT' | 'IMAGE' },
) {
  return post<Message>(`/conversations/${conversationId}/messages`, data)
}

/** 把整个会话标记为已读（进入会话时调用） */
export function markConversationRead(conversationId: number) {
  return post<void>(`/conversations/${conversationId}/read`)
}

/* ---------------- MS-02 通知中心 ---------------- */

/** 通知列表，可按类型筛选、只看未读 */
export function fetchNotifications(params: {
  type?: NotifType
  unreadOnly?: boolean
  cursor?: string
  size?: number
}) {
  return get<CursorPage<Notification>>('/notifications', params as Record<string, unknown>)
}

/** 未读数量（导航栏红点用） */
export function fetchUnreadCount() {
  return get<{ task: number; fund: number; forum: number; system: number; total: number }>(
    '/notifications/unread-count',
  )
}

/** 把指定通知标记为已读 */
export function markNotificationsRead(ids: number[]) {
  return post<void>('/notifications/read', { ids })
}

/** 全部标记为已读 */
export function markAllNotificationsRead() {
  return post<void>('/notifications/read-all')
}
