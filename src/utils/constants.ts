/**
 * 枚举 → 文案 / 颜色的映射
 * 集中放这里，页面里不要再散落 if-else 判断状态文案。
 */
import {
  AuthStatus,
  BizType,
  JudgeResult,
  NotifType,
  ReportType,
  TaskStatus,
  TaskType,
  TransType,
  UserStatus,
} from '@/api/types'

/** Element Plus 标签的颜色类型 */
export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 任务状态 */
export const TASK_STATUS_META: Record<TaskStatus, { label: string; type: TagType }> = {
  [TaskStatus.OPEN]: { label: '待接取', type: 'success' },
  [TaskStatus.TAKEN]: { label: '进行中', type: 'primary' },
  [TaskStatus.SUBMITTED]: { label: '待确认', type: 'warning' },
  [TaskStatus.COMPLETED]: { label: '已完成', type: 'info' },
  [TaskStatus.SETTLED]: { label: '已结算', type: 'info' },
  [TaskStatus.DISPUTED]: { label: '争议中', type: 'danger' },
  [TaskStatus.CANCELLED]: { label: '已取消', type: 'info' },
  [TaskStatus.EXPIRED]: { label: '已过期', type: 'info' },
}

/** 任务类型 */
export const TASK_TYPE_META: Record<TaskType, { label: string; icon: string }> = {
  [TaskType.PICKUP]: { label: '代取快递', icon: 'Box' },
  [TaskType.BUY]: { label: '代买', icon: 'ShoppingCart' },
  [TaskType.RENT]: { label: '租赁', icon: 'Files' },
  [TaskType.TUTOR]: { label: '家教', icon: 'Reading' },
  [TaskType.MOVE]: { label: '搬运', icon: 'Van' },
  [TaskType.OTHER]: { label: '其他', icon: 'More' },
}

/** 用户账号状态 */
export const USER_STATUS_META: Record<UserStatus, { label: string; type: TagType }> = {
  [UserStatus.NORMAL]: { label: '正常', type: 'success' },
  [UserStatus.LIMITED]: { label: '受限', type: 'warning' },
  [UserStatus.BANNED]: { label: '封禁', type: 'danger' },
}

/** 实名认证状态 */
export const AUTH_STATUS_META: Record<AuthStatus, { label: string; type: TagType }> = {
  [AuthStatus.NONE]: { label: '未认证', type: 'info' },
  [AuthStatus.PENDING]: { label: '审核中', type: 'warning' },
  [AuthStatus.APPROVED]: { label: '已认证', type: 'success' },
  [AuthStatus.REJECTED]: { label: '已驳回', type: 'danger' },
}

/** 资金变动类型（钱是怎么动的） */
export const TRANS_TYPE_META: Record<TransType, { label: string; income: boolean }> = {
  [TransType.FREEZE]: { label: '冻结', income: false },
  [TransType.UNFREEZE]: { label: '解冻', income: true },
  [TransType.INCOME]: { label: '收入', income: true },
  [TransType.PAY]: { label: '支出', income: false },
  [TransType.PENALTY]: { label: '违约扣罚', income: false },
  [TransType.GRANT]: { label: '发放', income: true },
  [TransType.REVERSAL]: { label: '冲正', income: true },
}

/** 资金业务类型（钱是因为什么动的） */
export const BIZ_TYPE_META: Record<BizType, string> = {
  [BizType.TASK]: '任务',
  [BizType.DISPUTE]: '争议',
  [BizType.ADMIN]: '管理员操作',
  [BizType.REGISTER]: '注册赠送',
}

/** 通知类型 */
export const NOTIF_TYPE_META: Record<NotifType, { label: string; type: TagType }> = {
  [NotifType.TASK]: { label: '任务', type: 'primary' },
  [NotifType.FUND]: { label: '资金', type: 'success' },
  [NotifType.FORUM]: { label: '论坛', type: 'warning' },
  [NotifType.SYSTEM]: { label: '系统', type: 'info' },
}

/** 举报类型 */
export const REPORT_TYPE_META: Record<ReportType, string> = {
  [ReportType.ILLEGAL_CONTENT]: '违规内容',
  [ReportType.NO_SHOW]: '恶意爽约',
  [ReportType.FAKE_DELIVERY]: '虚假交付',
  [ReportType.HARASS]: '骚扰',
  [ReportType.OTHER]: '其他',
}

/** 仲裁结果 */
export const JUDGE_RESULT_META: Record<JudgeResult, { label: string; type: TagType }> = {
  [JudgeResult.PUBLISHER]: { label: '支持委托方（全额退回）', type: 'warning' },
  [JudgeResult.TAKER]: { label: '支持接单方（全额划转）', type: 'success' },
  [JudgeResult.PARTIAL]: { label: '部分支持（按比例拆分）', type: 'primary' },
  [JudgeResult.INSUFFICIENT]: { label: '证据不足（退回委托方）', type: 'info' },
}

/** 排序方式 */
export const TASK_SORT_OPTIONS = [
  { value: 'DEFAULT', label: '综合排序' },
  { value: 'REWARD', label: '报酬从高到低' },
  { value: 'LATEST', label: '最新发布' },
  { value: 'DEADLINE', label: '即将截止' },
] as const

/** 把枚举对象转成下拉框选项 */
export function toOptions<T extends string>(
  meta: Record<T, string | { label: string }>,
): Array<{ value: T; label: string }> {
  return (Object.keys(meta) as T[]).map((key) => {
    const v = meta[key]
    return { value: key, label: typeof v === 'string' ? v : v.label }
  })
}
