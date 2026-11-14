/**
 * ============================================================
 * 接口模块五：个人中心（UC）与钱包（PF）
 * 对应需求：UC-01 个人信息 / UC-02 我的任务 / UC-03 我的收藏
 *          UC-04 浏览历史 / UC-05 我的钱包
 *          PF-01 虚拟货币账户体系
 * ============================================================
 */
import { del, get, post, put } from './request'
import type {
  CreditDetail,
  CursorPage,
  FavTargetType,
  FavoriteItem,
  FrozenItem,
  HistoryItem,
  User,
  WalletAccount,
  WalletTransaction,
} from './types'

/* ---------------- UC-01 个人信息 ---------------- */

/** 查看他人主页（公开信息，不含手机号） */
export function fetchUserProfile(id: number) {
  return get<User>(`/users/${id}`)
}

/**
 * 信誉分明细：分数、五项指标、统计窗口、最近参与计算的接单记录。
 * 传 'me' 表示查自己（后端 /users/me 也走同一套）。
 */
export function fetchCreditDetail(userId: number | 'me') {
  return get<CreditDetail>(`/users/${userId}/credit`)
}

/**
 * 修改个人信息（UC-01）
 * 可修改：昵称、头像、简介、擅长标签、联系方式
 * 不可修改：用户名、信用分、账号状态
 */
export function updateMyProfile(data: {
  nickname?: string
  avatar?: string
  intro?: string
  tags?: string[]
}) {
  return put<User>('/users/me', data)
}

/* ---------------- UC-03 我的收藏 ---------------- */

/** 我的收藏（任务 + 帖子，可按类型筛选） */
export function fetchFavorites(params: {
  targetType?: FavTargetType
  cursor?: string
  size?: number
}) {
  return get<CursorPage<FavoriteItem>>('/me/favorites', params as Record<string, unknown>)
}

/** 取消收藏（任务或帖子统一入口）
 *  后端用的是 @RequestParam，必须走 query；
 *  之前把参数当 body 发，后端收不到 → 「请求参数不合法：缺少参数 targetType」
 */
export function removeFavorite(targetType: FavTargetType, targetId: number) {
  return del<void>('/me/favorites', undefined, { params: { targetType, targetId } })
}

/* ---------------- UC-04 浏览历史 ---------------- */

/** 浏览历史（最多保留最近 200 条） */
export function fetchHistory(cursor?: string, size = 20) {
  return get<CursorPage<HistoryItem>>('/me/history', { cursor, size })
}

/** 删除单条浏览记录 */
export function deleteHistoryItem(id: number) {
  return del<void>(`/me/history/${id}`)
}

/** 清空浏览历史 */
export function clearHistory() {
  return del<void>('/me/history')
}

/* ---------------- UC-05 我的钱包 ---------------- */

/** 钱包概览：可用 / 冻结 / 欠款 / 总资产 */
export function fetchWallet() {
  return get<WalletAccount>('/me/wallet')
}

/**
 * 收支流水（游标分页，只增不改的账本）
 * @param type 可选，按变动类型筛选
 */
export function fetchTransactions(params: { type?: string; cursor?: string; size?: number }) {
  return get<CursorPage<WalletTransaction>>(
    '/me/wallet/transactions',
    params as Record<string, unknown>,
  )
}

/** 冻结明细：当前被哪些任务占用了屋币 */
export function fetchFrozenItems() {
  return get<FrozenItem[]>('/me/wallet/frozen')
}

/* ---------------- 通用：心跳与心跳外的杂项 ---------------- */

/**
 * 上报心跳 / 刷新在线状态（可选）
 * 若后端暂未实现可忽略，不影响其他功能。
 */
export function heartbeat() {
  return post<void>('/users/heartbeat')
}
