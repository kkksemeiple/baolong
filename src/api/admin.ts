/**
 * ============================================================
 * 接口模块六：管理后台（AD）
 * 对应需求：AD-01 用户管理 / AD-02 内容审核 / AD-03 举报与仲裁
 *          AD-04 任务管理 / AD-05 数据统计 / AD-06 虚拟货币管理
 *          PF-04 操作日志
 * 注意：本模块所有接口都要求当前用户具有 ADMIN 或 SADMIN 角色
 * ============================================================
 */
import { del, get, post, put } from './request'
import type {
  AdminOverview,
  AdminUserRow,
  AuditItem,
  Dispute,
  JudgeResult,
  PageQuery,
  PageResult,
  Report,
  SystemConfig,
  Task,
  TrendPoint,
  UserStatus,
} from './types'

/* ---------------- AD-05 数据统计 ---------------- */

/** 概览指标：用户数、任务数、完成率、屋币发行与流通 */
export function fetchOverview() {
  return get<AdminOverview>('/admin/stats/overview')
}

/** 活跃趋势（近 N 天） */
export function fetchTrend(days = 30) {
  return get<TrendPoint[]>('/admin/stats/trend', { days })
}

/* ---------------- AD-01 用户管理 ---------------- */

/** 用户列表 */
export function fetchAdminUsers(params: PageQuery & { keyword?: string; status?: UserStatus }) {
  return get<PageResult<AdminUserRow>>(
    '/admin/users',
    params as unknown as Record<string, unknown>,
  )
}

/** 封禁用户（需填原因，可设解封时间） */
export function banUser(id: number, data: { reason: string; until?: string }) {
  return put<void>(`/admin/users/${id}/ban`, data)
}

/** 解封用户 */
export function unbanUser(id: number) {
  return put<void>(`/admin/users/${id}/unban`)
}

/** 删除违规账号（需二次确认 + 原因） */
export function deleteUser(id: number, reason: string) {
  return del<void>(`/admin/users/${id}`, { reason })
}

/** 审核用户提交的学号实名认证（配合 UA-07） */
export function reviewStudentCert(userId: number, data: { pass: boolean; reason?: string }) {
  return put<void>(`/admin/users/${userId}/student-cert`, data)
}

/* ---------------- AD-02 内容审核 ---------------- */

/** 待审核内容列表（自动命中违禁词 + 用户举报） */
export function fetchAudits(params: PageQuery & { targetType?: string; status?: string }) {
  return get<PageResult<AuditItem>>('/admin/audits', params as unknown as Record<string, unknown>)
}

/** 审核通过 */
export function approveAudit(id: number) {
  return post<void>(`/admin/audits/${id}/approve`)
}

/** 审核驳回 / 下架（需填原因，会通知作者） */
export function rejectAudit(id: number, reason: string) {
  return post<void>(`/admin/audits/${id}/reject`, { reason })
}

/* ---------------- AD-03 举报与仲裁 ---------------- */

/** 举报列表 */
export function fetchReports(params: PageQuery & { status?: string }) {
  return get<PageResult<Report>>('/admin/reports', params as unknown as Record<string, unknown>)
}

/** 处理举报 */
export function handleReport(
  id: number,
  data: { pass: boolean; result: string; penalty?: number },
) {
  return post<void>(`/admin/reports/${id}/handle`, data)
}

/** 争议列表 */
export function fetchDisputes(params: PageQuery & { status?: string }) {
  return get<PageResult<Dispute>>('/admin/disputes', params as unknown as Record<string, unknown>)
}

/**
 * 裁决争议
 * @param result 裁决结果：支持委托方 / 支持接单方 / 部分支持 / 证据不足
 * @param ratio  部分支持时给接单方的比例（0-100）
 * @param remark 裁决理由，至少 20 字，终局不可撤销
 */
export function judgeDispute(
  id: number,
  data: { result: JudgeResult; ratio?: number; remark: string },
) {
  return post<void>(`/admin/disputes/${id}/judge`, data)
}

/* ---------------- AD-04 任务管理 ---------------- */

/** 管理端任务列表 */
export function fetchAdminTasks(
  params: PageQuery & { status?: string; keyword?: string },
) {
  return get<PageResult<Task>>(
    '/admin/tasks',
    params as unknown as Record<string, unknown>,
  )
}

/** 下架任务（冻结屋币全额退回委托方） */
export function offlineTask(id: number, reason: string) {
  return post<void>(`/admin/tasks/${id}/offline`, { reason })
}

/* ---------------- AD-06 虚拟货币管理 ---------------- */

/** 单人发放屋币 */
export function issueCoin(data: { userId: number; amount: number; reason: string }) {
  return post<void>('/admin/coins/issue', data)
}

/** 批量发放（按用户 ID 列表） */
export function issueCoinBatch(data: { userIds: number[]; amount: number; reason: string }) {
  return post<{ batchId: string }>('/admin/coins/issue-batch', data)
}

/**
 * 资金冲正（仅超级管理员）
 * 不修改历史流水，而是新增一条反向流水，保证账本只增不改。
 */
export function reverseTransaction(data: { transactionId: number; reason: string }) {
  return post<void>('/admin/coins/reverse', data)
}

/** 全平台对账：返回账目不平的用户清单，空数组表示账目平衡 */
export function auditCoins() {
  return get<Array<{ userId: number; username: string; balance: number; calculated: number }>>(
    '/admin/coins/audit',
  )
}

/** 读取系统配置（初始赠送、上限、超时时长等） */
export function fetchSystemConfig() {
  return get<SystemConfig>('/admin/config')
}

/** 修改系统配置 */
export function updateSystemConfig(data: Partial<SystemConfig>) {
  return put<void>('/admin/config', data)
}

/** 操作日志（PF-04） */
export function fetchOperationLogs(params: PageQuery) {
  return get<
    PageResult<{
      id: number
      operator: string
      action: string
      target: string
      before: string
      after: string
      reason: string
      createdAt: string
    }>
  >('/admin/logs', params as unknown as Record<string, unknown>)
}
