/**
 * 用户状态
 * 负责：登录凭证的持有、当前用户信息、角色判断
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { authApi } from '@/api'
import type { LoginResult, User } from '@/api/types'
import { clearToken, getCachedUser, getToken, setCachedUser, setToken } from '@/utils/auth'
import type { RegisterParams } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  /** 登录凭证 */
  const token = ref<string>(getToken())
  /** 当前用户；先用本地缓存渲染，再静默刷新 */
  const user = ref<User | null>(getCachedUser<User>())
  /** 是否正在拉取用户信息 */
  const loading = ref(false)

  const isLogin = computed(() => Boolean(token.value))
  const isAdmin = computed(
    () => user.value?.role === 'ADMIN' || user.value?.role === 'SADMIN',
  )
  const isSadmin = computed(() => user.value?.role === 'SADMIN')
  const displayName = computed(() => user.value?.nickname ?? '游客')
  const creditScore = computed(() => user.value?.creditScore ?? 0)
  /** 信用分低于 60 不能发布/接单 */
  const canTrade = computed(() => creditScore.value >= 60)

  /** 保存登录结果 */
  function applyLogin(res: LoginResult) {
    token.value = res.token
    user.value = res.user
    setToken(res.token)
    setCachedUser(res.user)
  }

  /** 登录 */
  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    applyLogin(res)
    return res
  }

  /** 注册（注册成功后自动登录） */
  async function register(data: RegisterParams) {
    const res = await authApi.register(data)
    applyLogin(res)
    return res
  }

  /** 拉取最新用户信息 */
  async function fetchMe() {
    if (!token.value) return null
    loading.value = true
    try {
      const res = await authApi.fetchCurrentUser()
      user.value = res
      setCachedUser(res)
      return res
    } finally {
      loading.value = false
    }
  }

  /** 退出登录 */
  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 退出失败也要清本地，避免用户卡住
    }
    reset()
  }

  /** 清空本地登录态 */
  function reset() {
    token.value = ''
    user.value = null
    clearToken()
  }

  return {
    token,
    user,
    loading,
    isLogin,
    isAdmin,
    isSadmin,
    displayName,
    creditScore,
    canTrade,
    login,
    register,
    fetchMe,
    logout,
    reset,
    applyLogin,
  }
})
