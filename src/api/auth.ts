/**
 * ============================================================
 * 接口模块一：认证与账号（UA）
 * 对应需求：UA-01 注册 / UA-02 登录 / UA-03 退出登录
 *          UA-04 访问控制 / UA-05 游客访问与登录引导
 *          迭代三 UA-06 密码找回 / UA-07 学号实名认证 / UA-08 登录安全
 * ============================================================
 */
import { get, post, put } from './request'
import type { AuthStatus, LoginResult, User } from './types'

/** 登录日志条目 */
export interface LoginLogItem {
  id: number
  ip: string
  /** 登录地点（可由后端解析 IP 得到） */
  location: string
  /** 登录设备 */
  userAgent: string
  /** 是否成功 */
  success: boolean
  /** 失败原因 */
  failReason: string | null
  loginAt: string
}

/** 注册参数 */
export interface RegisterParams {
  /** 登录名，3-20 位字母数字 */
  username: string
  /** 密码，8-20 位，需含字母与数字 */
  password: string
  /** 确认密码，仅前端校验，不必发给后端（这里保留以便后端二次校验） */
  confirmPassword: string
  /** 手机号，11 位 */
  phone: string
  /** 昵称，可选，默认等于 username */
  nickname?: string
}

/** 注册（UA-01） */
export function register(data: RegisterParams) {
  return post<LoginResult>('/auth/register', data)
}

/** 登录（UA-02）；连续 5 次失败会被锁定 10 分钟 */
export function login(data: { username: string; password: string }) {
  return post<LoginResult>('/auth/login', data)
}

/**
 * 退出登录（UA-03）
 * 后端会把当前凭证的 jti 加入黑名单，使这个 token 立即失效。
 */
export function logout() {
  return post<void>('/auth/logout')
}

/** 获取当前登录用户信息（进入应用时调用，用于刷新用户状态） */
export function fetchCurrentUser() {
  return get<User>('/auth/me')
}

/**
 * 忘记密码 —— 发送重置令牌（UA-06）
 * 后端校验手机号是否已注册，通过短信发送 6 位验证码或一次性令牌。
 * 无论手机号是否注册，接口都返回相同提示，避免账号枚举。
 */
export function sendResetCode(phone: string) {
  return post<{ expireIn: number }>('/auth/password/forgot', { phone })
}

/**
 * 重置密码（UA-06）
 * 令牌一次性有效，15 分钟内可用；成功后该用户所有已签发凭证立即失效。
 */
export function resetPassword(data: { phone: string; code: string; newPassword: string }) {
  return post<void>('/auth/password/reset', data)
}

/** 修改密码（登录态下，需要校验旧密码） */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return put<void>('/auth/password', data)
}

/** 查询学号实名认证状态（UA-07） */
export function fetchAuthStatus() {
  return get<{ authStatus: AuthStatus; studentNo: string | null; rejectReason: string | null }>(
    '/auth/student-cert',
  )
}

/**
 * 提交学号实名认证（UA-07）
 * @param studentNo 学号
 * @param certImage 学生证照片 URL（需先调用上传接口拿到 URL）
 */
export function submitStudentCert(data: { studentNo: string; certImage: string }) {
  return post<void>('/auth/student-cert', data)
}

/** 查询最近登录记录（UA-08，最近 10 条） */
export function fetchLoginLogs() {
  return get<LoginLogItem[]>('/auth/login-logs')
}
