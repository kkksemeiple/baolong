/**
 * ============================================================
 * 全局类型定义
 * ------------------------------------------------------------
 * 约定：
 * 1. 后端统一返回 { code, message, data }，code === 0 表示成功
 * 2. 枚举值用「常量对象 + 联合类型」实现，既能当值用（下拉框选项），
 *    又有类型约束（传错值编译期报错）
 * 3. 所有 id 用 number（后端 Long 类型）
 * ============================================================
 */

/* ============================================================
 * 一、通用结构
 * ============================================================ */

/** 后端统一响应体 */
export interface ApiResult<T = unknown> {
  /** 业务状态码，0 表示成功 */
  code: number
  /** 提示信息 */
  message: string
  /** 业务数据 */
  data: T
}

/** 游标分页结果（任务大厅、流水、消息都用它，避免深分页变慢） */
export interface CursorPage<T> {
  list: T[]
  /** 下一页游标，为 null 表示没有更多 */
  nextCursor: string | null
  hasMore: boolean
}

/** 页码分页结果（管理后台列表用） */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

/** 分页查询基础参数 */
export interface PageQuery {
  page?: number
  size?: number
}

/* ============================================================
 * 二、枚举
 * ============================================================ */

/** 任务状态（与状态图的 8 个状态一一对应） */
export const TaskStatus = {
  /** 待接取 */
  OPEN: 'OPEN',
  /** 进行中（已接单，履约中） */
  TAKEN: 'TAKEN',
  /** 待确认（接单方已提交完成） */
  SUBMITTED: 'SUBMITTED',
  /** 已完成 */
  COMPLETED: 'COMPLETED',
  /** 已结算（资金已划转，终态） */
  SETTLED: 'SETTLED',
  /** 争议中（仲裁处理中） */
  DISPUTED: 'DISPUTED',
  /** 已取消（终态） */
  CANCELLED: 'CANCELLED',
  /** 已过期（终态） */
  EXPIRED: 'EXPIRED',
} as const
export type TaskStatus = (typeof TaskStatus)[keyof typeof TaskStatus]

/** 任务类型 */
export const TaskType = {
  PICKUP: 'PICKUP', // 代取
  BUY: 'BUY', // 代买
  RENT: 'RENT', // 租赁
  TUTOR: 'TUTOR', // 家教
  MOVE: 'MOVE', // 搬运
  OTHER: 'OTHER', // 其他
} as const
export type TaskType = (typeof TaskType)[keyof typeof TaskType]

/** 用户账号状态 */
export const UserStatus = {
  NORMAL: 'NORMAL', // 正常
  LIMITED: 'LIMITED', // 受限（信用分过低）
  BANNED: 'BANNED', // 封禁
} as const
export type UserStatus = (typeof UserStatus)[keyof typeof UserStatus]

/** 学号实名认证状态 */
export const AuthStatus = {
  NONE: 'NONE', // 未提交
  PENDING: 'PENDING', // 审核中
  APPROVED: 'APPROVED', // 已通过
  REJECTED: 'REJECTED', // 已驳回
} as const
export type AuthStatus = (typeof AuthStatus)[keyof typeof AuthStatus]

/** 接单记录状态 */
export const ClaimStatus = {
  ACTIVE: 'ACTIVE', // 进行中
  FINISHED: 'FINISHED', // 已完成
  CANCELLED: 'CANCELLED', // 已取消
} as const
export type ClaimStatus = (typeof ClaimStatus)[keyof typeof ClaimStatus]

/** 资金流水变动类型：钱是怎么动的 */
export const TransType = {
  FREEZE: 'FREEZE', // 冻结（发布任务）
  UNFREEZE: 'UNFREEZE', // 解冻（取消/过期）
  INCOME: 'INCOME', // 收入（任务完成）
  PAY: 'PAY', // 支出（任务支付）
  PENALTY: 'PENALTY', // 违约扣罚
  GRANT: 'GRANT', // 管理员发放
  REVERSAL: 'REVERSAL', // 冲正
} as const
export type TransType = (typeof TransType)[keyof typeof TransType]

/** 资金流水业务类型：钱是因为什么动的 */
export const BizType = {
  TASK: 'TASK',
  DISPUTE: 'DISPUTE',
  ADMIN: 'ADMIN',
  REGISTER: 'REGISTER',
} as const
export type BizType = (typeof BizType)[keyof typeof BizType]

/** 通知类型 */
export const NotifType = {
  TASK: 'TASK', // 任务动态
  FUND: 'FUND', // 资金变动
  FORUM: 'FORUM', // 论坛互动
  SYSTEM: 'SYSTEM', // 系统消息
} as const
export type NotifType = (typeof NotifType)[keyof typeof NotifType]

/** 收藏目标类型 */
export const FavTargetType = {
  TASK: 'TASK',
  POST: 'POST',
} as const
export type FavTargetType = (typeof FavTargetType)[keyof typeof FavTargetType]

/** 消息内容类型 */
export const MsgType = {
  TEXT: 'TEXT',
  IMAGE: 'IMAGE',
} as const
export type MsgType = (typeof MsgType)[keyof typeof MsgType]

/** 举报类型 */
export const ReportType = {
  ILLEGAL_CONTENT: 'ILLEGAL_CONTENT', // 违规内容
  NO_SHOW: 'NO_SHOW', // 恶意爽约
  FAKE_DELIVERY: 'FAKE_DELIVERY', // 虚假交付
  HARASS: 'HARASS', // 骚扰
  OTHER: 'OTHER',
} as const
export type ReportType = (typeof ReportType)[keyof typeof ReportType]

/** 仲裁裁决结果 */
export const JudgeResult = {
  PUBLISHER: 'PUBLISHER', // 支持委托方（全额退回）
  TAKER: 'TAKER', // 支持接单方（全额划转）
  PARTIAL: 'PARTIAL', // 部分支持（按比例拆分）
  INSUFFICIENT: 'INSUFFICIENT', // 证据不足
} as const
export type JudgeResult = (typeof JudgeResult)[keyof typeof JudgeResult]

/* ============================================================
 * 三、实体
 * ============================================================ */

/** 用户角色 */
export const UserRole = {
  /** 普通用户 */
  USER: 'USER',
  /** 管理员 */
  ADMIN: 'ADMIN',
  /** 超级管理员（可做资金冲正与系统配置） */
  SADMIN: 'SADMIN',
} as const
export type UserRole = (typeof UserRole)[keyof typeof UserRole]

/** 用户 */
export interface User {
  id: number
  /** 登录名（不叫 account，避免与钱包账户混淆） */
  username: string
  /** 角色，决定能否进入管理后台 */
  role: UserRole
  nickname: string
  avatar: string | null
  /** 手机号（脱敏后返回，如 138****1234） */
  phone: string
  /** 信用分 0-120，低于 60 不可发布/接单 */
  creditScore: number
  /** 账号状态 */
  status: UserStatus
  /** 学号实名认证状态 */
  authStatus: AuthStatus
  /** 完成单数 */
  finishedCount: number
  /** 好评率 0-100 */
  goodRate: number
  /** 擅长类型标签 */
  tags: string[]
  /** 个人简介 */
  intro: string
  createdAt: string
}

/** 登录返回的凭证信息 */
export interface LoginResult {
  /** 访问凭证（JWT） */
  token: string
  /** 凭证有效期（秒） */
  expiresIn: number
  /** 当前用户信息 */
  user: User
}

/** 任务 */
export interface Task {
  id: number
  title: string
  description: string
  type: TaskType
  /** 报酬（屋币），整数 */
  reward: number
  /** 截止时间 ISO 字符串 */
  deadline: string
  location: string
  /** 人数需求 */
  peopleNeeded: number
  /** 图片附件 URL 列表 */
  images: string[]
  status: TaskStatus
  /** 委托方 ID */
  publisherId: number
  /** 发布者摘要信息（列表卡片需要） */
  publisher?: UserBrief
  /** 当前接单人 ID（为空表示还没人接） */
  takerId: number | null
  /** 当前接单人摘要 */
  taker?: UserBrief
  /** 是否已被当前登录用户收藏 */
  favorited: boolean
  /** 当前登录用户是否已接此单 */
  mine: boolean
  createdAt: string
  /** 提交完成时间 */
  submittedAt: string | null
  /** 确认完成时间 */
  confirmedAt: string | null
}

/** 用户摘要（列表里避免传输完整用户对象） */
export interface UserBrief {
  id: number
  nickname: string
  avatar: string | null
  creditScore: number
}

/** 发布任务入参 */
export interface TaskCreateParams {
  title: string
  description: string
  type: TaskType
  reward: number
  deadline: string
  location: string
  peopleNeeded?: number
  images?: string[]
}

/** 任务大厅查询参数 */
export interface TaskQuery {
  /** 关键词（标题 + 描述） */
  keyword?: string
  /** 任务类型 */
  type?: TaskType
  /** 报酬下限 */
  minReward?: number
  /** 报酬上限 */
  maxReward?: number
  /** 排序：综合 / 报酬 / 最新 / 即将截止 */
  sortBy?: 'DEFAULT' | 'REWARD' | 'LATEST' | 'DEADLINE'
  /** 游标分页 */
  cursor?: string
  size?: number
}

/** 提交完成入参 */
export interface TaskSubmitParams {
  /** 交付说明 */
  content: string
  /** 交付凭证图片 */
  images?: string[]
}

/** 议价请求 */
export interface BargainParams {
  /** 我方提出的金额 */
  amount: number
}

/** 议价应答 */
export interface BargainRespondParams {
  /** 是否接受对方报价 */
  accept: boolean
  /** 不接受时我可给出的还价（可选） */
  counterAmount?: number
}

/** 任务分类 */
export interface TaskCategory {
  id: number
  name: string
  icon: string
}

/* ---------------- 钱包与资金 ---------------- */

/** 钱包账户 */
export interface WalletAccount {
  userId: number
  /** 可用余额 */
  available: number
  /** 冻结余额 */
  frozen: number
  /** 欠款（余额不足扣罚时产生） */
  debt: number
  /** 总资产 = 可用 + 冻结 - 欠款 */
  total: number
}

/** 资金流水（只增不改） */
export interface WalletTransaction {
  id: number
  type: TransType
  /** 变动金额，正数入账，负数出账 */
  amount: number
  /** 变动后可用余额 */
  balanceAfter: number
  /** 业务类型 */
  bizType: BizType
  /** 关联业务 ID（任务 ID 等） */
  bizId: number | null
  /** 关联任务标题，便于展示 */
  bizTitle: string | null
  remark: string
  createdAt: string
}

/** 冻结明细（我的钱包里展开查看被哪些任务占用） */
export interface FrozenItem {
  taskId: number
  taskTitle: string
  amount: number
  frozenAt: string
}

/* ---------------- 消息与通知 ---------------- */

/** 会话 */
export interface Conversation {
  id: number
  taskId: number
  taskTitle: string
  /** 对方用户摘要 */
  peer: UserBrief
  lastMessage: string
  lastMessageAt: string
  unreadCount: number
}

/** 消息 */
export interface Message {
  id: number
  conversationId: number
  fromUserId: number
  content: string
  contentType: MsgType
  readFlag: boolean
  createdAt: string
}

/** 通知 */
export interface Notification {
  id: number
  type: NotifType
  title: string
  content: string
  /** 关联业务 ID，点击可跳转 */
  bizId: number | null
  /** 跳转路径，后端可直接给出 */
  link: string | null
  readFlag: boolean
  createdAt: string
}

/* ---------------- 收藏与历史 ---------------- */

/** 收藏项 */
export interface FavoriteItem {
  id: number
  targetType: FavTargetType
  targetId: number
  /** 目标标题（任务标题或帖子标题） */
  title: string
  /** 目标摘要 */
  summary: string
  /** 目标是否已失效（任务被接取/过期，帖子被删除） */
  invalid: boolean
  createdAt: string
}

/** 浏览历史项 */
export interface HistoryItem {
  id: number
  targetType: FavTargetType
  targetId: number
  title: string
  viewedAt: string
}

/* ---------------- 论坛 ---------------- */

/** 帖子 */
export interface Post {
  id: number
  title: string
  content: string
  authorId: number
  author?: UserBrief
  /** 标签/板块 */
  tags: string[]
  replyCount: number
  likeCount: number
  /** 是否已点赞 */
  liked: boolean
  /** 是否已收藏 */
  favorited: boolean
  createdAt: string
}

/** 回帖 */
export interface Reply {
  id: number
  postId: number
  /** 父回复 ID，二级回复用 */
  parentId: number | null
  content: string
  authorId: number
  author?: UserBrief
  createdAt: string
  /** 子回复（二级） */
  children?: Reply[]
}

/** 发帖入参 */
export interface PostCreateParams {
  title: string
  content: string
  tags?: string[]
}

/* ============================================================
 * 四、管理后台
 * ============================================================ */

/** 概览统计 */
export interface AdminOverview {
  userCount: number
  taskCount: number
  postCount: number
  /** 任务完成率 0-100 */
  finishRate: number
  /** 纠纷率 0-100 */
  disputeRate: number
  /** 屋币累计发行 */
  coinIssued: number
  /** 当前流通量 */
  coinCirculating: number
  /** 冻结量 */
  coinFrozen: number
  /** 回收量 */
  coinRecycled: number
  /** 欠款总额 */
  coinDebt: number
}

/** 趋势数据点 */
export interface TrendPoint {
  date: string
  /** 日活 */
  activeUsers: number
  /** 发布量 */
  published: number
  /** 接单量 */
  claimed: number
}

/** 举报 */
export interface Report {
  id: number
  type: ReportType
  taskId: number | null
  postId: number | null
  /** 举报对象摘要 */
  targetTitle: string
  reporter: UserBrief
  reason: string
  evidence: string[]
  status: 'PENDING' | 'RESOLVED' | 'REJECTED'
  result: string | null
  createdAt: string
}

/** 争议 */
export interface Dispute {
  id: number
  taskId: number
  taskTitle: string
  publisher: UserBrief
  taker: UserBrief
  /** 冻结金额 */
  frozenAmount: number
  initiatorId: number
  reason: string
  evidence: string[]
  status: 'PENDING' | 'JUDGED'
  result: JudgeResult | null
  remark: string | null
  createdAt: string
}

/** 待审核内容 */
export interface AuditItem {
  id: number
  /** 内容类型 */
  targetType: 'TASK' | 'POST' | 'REPLY'
  targetId: number
  title: string
  content: string
  /** 命中原因（违禁词） */
  hitReason: string
  /** 作者 */
  author: UserBrief
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  createdAt: string
}

/** 管理端用户行 */
export interface AdminUserRow extends User {
  /** 是否被举报过 */
  reportedCount: number
  /** 发布任务数 */
  publishedCount: number
  /** 接单数 */
  claimedCount: number
}

/** 系统配置 */
export interface SystemConfig {
  /** 新用户注册赠送 */
  initGrant: number
  /** 单人持有上限 */
  maxHolding: number
  /** 单次发放上限 */
  grantLimit: number
  /** 自动确认时长（小时） */
  autoConfirmHours: number
  /** 违约金-无责取消额度外 */
  penaltyCancel: number
  /** 违约金-爽约 */
  penaltyNoShow: number
}
