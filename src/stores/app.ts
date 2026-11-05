/**
 * 应用级状态
 * 负责：导航栏未读角标、任务分类字典缓存
 */
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { messageApi, taskApi } from '@/api'
import type { TaskCategory } from '@/api/types'

export const useAppStore = defineStore('app', () => {
  /** 各类型未读数 */
  const unread = ref({ task: 0, fund: 0, forum: 0, system: 0, total: 0 })
  /** 任务分类（下拉框用，只需拉一次） */
  const categories = ref<TaskCategory[]>([])
  const categoriesLoaded = ref(false)

  const hasUnread = computed(() => unread.value.total > 0)

  /** 刷新未读数量（登录后调用，之后可配合轮询） */
  async function refreshUnread() {
    try {
      unread.value = await messageApi.fetchUnreadCount()
    } catch {
      // 未登录或网络问题时静默失败，不影响主流程
    }
  }

  /** 加载任务分类 */
  async function loadCategories() {
    if (categoriesLoaded.value) return categories.value
    const list = await taskApi.fetchTaskCategories()
    categories.value = list
    categoriesLoaded.value = true
    return list
  }

  /** 退出登录时清空 */
  function reset() {
    unread.value = { task: 0, fund: 0, forum: 0, system: 0, total: 0 }
  }

  return { unread, hasUnread, categories, refreshUnread, loadCategories, reset }
})
