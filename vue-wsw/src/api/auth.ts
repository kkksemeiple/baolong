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
 * 忘记密码 —— 第一步：校验「登录名 + 注册手机号」是否匹配（UA-06）
 *
 * 本项目没有接短信服务，所以没有真正的验证码：
 * 后端只做匹配校验，返回打码后的手机号，真正的重置在下一步完成。
 */
export function verifyResetAccount(username: string, phone: string) {
  return post<{ phone: string; message: string }>('/auth/password/forgot', { username, phone })
}

/**
 * 重置密码（UA-06）
 * 后端会再次校验登录名与手机号，通过后改密并让该用户所有已签发凭证失效。
 */
export function resetPassword(data: { username: string; phone: string; newPassword: string }) {
  return post<void>('/auth/password/reset', data)
}

/** 修改密码（登录态下，需要校验旧密码） */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return put<void>('/auth/password', data)
}

/** 查询学号实名认证状态（UA-07） */
export function fetchAuthStatus() {
  return get<{
    authStatus: AuthStatus
    studentNo: string | null
    realName: string | null
    /** 学生证照片 URL，管理员审核时看 */
    certImage: string | null
    rejectReason: string | null
  }>('/auth/student-cert')
}

/**
 * 提交学号实名认证（UA-07）
 * @param realName 真实姓名（后端必填，用于人工核对）
 * @param studentNo 学号
 * @param certImage 学生证照片 URL（前端上传后拿到）
 */
export function submitStudentCert(data: {
  realName: string
  studentNo: string
  certImage?: string
}) {
  return post<void>('/auth/student-cert', data)
}

/** 查询最近登录记录（UA-08，最近 10 条） */
export function fetchLoginLogs() {
  return get<LoginLogItem[]>('/auth/login-logs')
}
