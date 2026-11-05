/**
 * ============================================================
 * 接口模块二：任务（TK）
 * 对应需求：TK-01 任务大厅 / TK-02 发布任务 / TK-03 任务详情
 *          TK-04 搜索 / TK-05 筛选排序 / TK-06 偏好推荐
 *          TK-07 收藏 / TK-08 接取 / TK-09 议价
 *          TK-10 状态流转 / TK-11 提交确认 / TK-12 报酬结算
 * ============================================================
 */
import { del, get, post, put, upload } from './request'
import type {
  BargainParams,
  BargainRespondParams,
  CursorPage,
  Task,
  TaskCategory,
  TaskCreateParams,
  TaskQuery,
  TaskStatus,
  TaskSubmitParams,
} from './types'

/**
 * 任务大厅列表（TK-01）
 * 使用游标分页而不是页码，避免翻到很后面时数据库扫描大量行。
 */
export function fetchTaskList(query: TaskQuery) {
  return get<CursorPage<Task>>('/tasks', query as Record<string, unknown>)
}

/**
 * 搜索任务（TK-04）
 * 独立接口便于后端做全文检索优化（MySQL 需使用 ngram 分词器才能检索中文）。
 */
export function searchTasks(query: { keyword: string; cursor?: string; size?: number }) {
  return get<CursorPage<Task>>('/tasks/search', query)
}

/** 偏好推荐（TK-06）：按历史接单类型 + 收藏 + 浏览历史推荐 */
export function fetchRecommendedTasks(size = 6) {
  return get<Task[]>('/tasks/recommend', { size })
}

/** 任务详情（TK-03） */
export function fetchTaskDetail(id: number) {
  return get<Task>(`/tasks/${id}`)
}

/** 任务分类字典（用于筛选下拉框） */
export function fetchTaskCategories() {
  return get<TaskCategory[]>('/tasks/categories')
}

/**
 * 发布任务（TK-02）
 * 后端会在同一事务内冻结委托方等额屋币；余额不足返回业务错误。
 */
export function createTask(data: TaskCreateParams) {
  return post<Task>('/tasks', data)
}

/** 修改任务（仅「待接取」状态可改；改报酬会增量冻结差额） */
export function updateTask(id: number, data: Partial<TaskCreateParams>) {
  return put<Task>(`/tasks/${id}`, data)
}

/** 取消任务（TK-10）：委托方在无人接单时取消，冻结屋币全额退回 */
export function cancelTask(id: number, reason?: string) {
  return post<void>(`/tasks/${id}/cancel`, { reason })
}

/**
 * 接取任务（TK-08）
 * 并发关键接口：后端用「Redis 锁 + 乐观锁 + 唯一索引」三层防护，
 * 保证同一任务同时只有一人接取成功。
 */
export function claimTask(id: number) {
  return post<void>(`/tasks/${id}/claim`)
}

/** 放弃任务（TK-10）：任务回到「待接取」，可被别人接走 */
export function abandonTask(id: number) {
  return post<void>(`/tasks/${id}/abandon`)
}

/** 提交完成（TK-11）：接单方上传交付凭证，任务进入「待确认」 */
export function submitTask(id: number, data: TaskSubmitParams) {
  return post<void>(`/tasks/${id}/submit`, data)
}

/** 确认完成（TK-11 + TK-12）：触发资金划转；重复调用只生效一次 */
export function confirmTask(id: number) {
  return post<void>(`/tasks/${id}/confirm`)
}

/** 拒绝确认（TK-11）：必须填写理由，任务退回「进行中」 */
export function rejectTask(id: number, reason: string) {
  return post<void>(`/tasks/${id}/reject`, { reason })
}

/** 申请仲裁（争议）：冻结资金保持不动，等待管理员裁决 */
export function disputeTask(id: number, data: { reason: string; evidence?: string[] }) {
  return post<void>(`/tasks/${id}/dispute`, data)
}

/**
 * 发起议价（TK-09）
 * 议价发生在接单前，最多 5 轮；达成一致后冻结额按新金额增量调整。
 */
export function bargainTask(id: number, data: BargainParams) {
  return post<void>(`/tasks/${id}/bargain`, data)
}

/** 回应议价（TK-09）：接受或还价 */
export function respondBargain(id: number, data: BargainRespondParams) {
  return post<void>(`/tasks/${id}/bargain/respond`, data)
}

/** 我发布的任务 / 我接取的任务（UC-02），可按状态筛选 */
export function fetchMyTasks(params: {
  role: 'publisher' | 'taker'
  status?: TaskStatus
  page?: number
  size?: number
}) {
  return get<CursorPage<Task>>('/tasks/mine', params as Record<string, unknown>)
}

/** 收藏任务（TK-07） */
export function favoriteTask(id: number) {
  return post<void>(`/tasks/${id}/favorite`)
}

/** 取消收藏任务（TK-07） */
export function unfavoriteTask(id: number) {
  return del<void>(`/tasks/${id}/favorite`)
}

/** 举报任务 */
export function reportTask(
  id: number,
  data: { type: string; reason: string; evidence?: string[] },
) {
  return post<void>(`/tasks/${id}/report`, data)
}

/**
 * 上传图片（任务附件、交付凭证、学生证等共用）
 * 返回图片 URL，业务接口只接收 URL。
 */
export function uploadImage(file: File, scene: 'task' | 'delivery' | 'cert' | 'post' = 'task') {
  return upload<{ url: string }>('/files/upload', file, { scene })
}
