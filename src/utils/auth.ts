/**
 * 登录凭证的本地存储
 * 单独抽出来，便于将来从 localStorage 换成 cookie 或内存存储时只改一处。
 */

const TOKEN_KEY = 'wsw_token'
const USER_KEY = 'wsw_user'

/** 读取凭证 */
export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

/** 保存凭证 */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 清除凭证（退出登录、凭证失效时调用） */
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 缓存用户信息，刷新页面时可先渲染再静默更新 */
export function setCachedUser(user: unknown): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 读取缓存的用户信息 */
export function getCachedUser<T>(): T | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}
