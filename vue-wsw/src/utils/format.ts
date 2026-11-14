/**
 * 格式化工具
 */
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

/** 标准日期时间：2026-10-25 14:30 */
export function formatDateTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—'
}

/** 只到日：2026-10-25 */
export function formatDate(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD') : '—'
}

/** 相对时间：3 小时前 / 2 天前 */
export function fromNow(value?: string | null): string {
  return value ? dayjs(value).fromNow() : '—'
}

/**
 * 距离截止还有多久
 * 返回 { text, urgent }，urgent 用于前端标红
 */
export function timeLeft(deadline: string): { text: string; urgent: boolean; expired: boolean } {
  const diff = dayjs(deadline).diff(dayjs(), 'minute')
  if (diff <= 0) return { text: '已截止', urgent: true, expired: true }
  if (diff < 60) return { text: `剩 ${diff} 分钟`, urgent: true, expired: false }
  if (diff < 60 * 24) return { text: `剩 ${Math.floor(diff / 60)} 小时`, urgent: true, expired: false }
  return { text: `剩 ${Math.floor(diff / (60 * 24))} 天`, urgent: false, expired: false }
}

/** 屋币展示：+30 WB / -7 WB */
export function formatCoin(amount: number, withSign = false): string {
  if (!withSign) return `${amount} WB`
  return `${amount > 0 ? '+' : ''}${amount} WB`
}

/** 千分位 */
export function formatNumber(n: number): string {
  return n.toLocaleString('zh-CN')
}

/** 手机号脱敏（后端已脱敏时这里做个兜底） */
export function maskPhone(phone: string): string {
  return phone.length === 11 ? `${phone.slice(0, 3)}****${phone.slice(7)}` : phone
}
