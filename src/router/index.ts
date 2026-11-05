/**
 * 路由表与守卫
 *
 * 结构：
 *   /login 等        —— 无布局的独立页面
 *   /               —— 用户端布局（DefaultLayout）
 *   /admin          —— 管理后台布局（AdminLayout）
 */
import { createRouter, createWebHistory } from 'vue-router'

import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 需要登录才能访问 */
    requiresAuth?: boolean
    /** 需要管理员角色 */
    requiresAdmin?: boolean
    /** 页面标题 */
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    /* ---------------- 认证相关（无布局） ---------------- */
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/auth/RegisterView.vue'),
      meta: { title: '注册' },
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/views/auth/ForgotPasswordView.vue'),
      meta: { title: '找回密码' },
    },

    /* ---------------- 用户端 ---------------- */
    {
      path: '/',
      component: () => import('@/layouts/DefaultLayout.vue'),
      children: [
        { path: '', redirect: '/tasks' },
        {
          path: 'tasks',
          name: 'task-hall',
          component: () => import('@/views/task/TaskHallView.vue'),
          meta: { title: '任务大厅' },
        },
        {
          path: 'tasks/:id(\\d+)',
          name: 'task-detail',
          component: () => import('@/views/task/TaskDetailView.vue'),
          meta: { title: '任务详情' },
        },
        {
          path: 'publish',
          name: 'task-publish',
          component: () => import('@/views/task/TaskPublishView.vue'),
          meta: { title: '发布任务', requiresAuth: true },
        },
        {
          path: 'my-tasks',
          name: 'my-tasks',
          component: () => import('@/views/task/MyTasksView.vue'),
          meta: { title: '我的任务', requiresAuth: true },
        },
        {
          path: 'messages',
          name: 'messages',
          component: () => import('@/views/message/ChatView.vue'),
          meta: { title: '消息', requiresAuth: true },
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('@/views/message/NotificationView.vue'),
          meta: { title: '通知中心', requiresAuth: true },
        },
        {
          path: 'forum',
          name: 'forum',
          component: () => import('@/views/forum/ForumListView.vue'),
          meta: { title: '社区论坛' },
        },
        {
          path: 'forum/create',
          name: 'post-create',
          component: () => import('@/views/forum/PostCreateView.vue'),
          meta: { title: '发帖', requiresAuth: true },
        },
        {
          path: 'forum/:id(\\d+)',
          name: 'post-detail',
          component: () => import('@/views/forum/PostDetailView.vue'),
          meta: { title: '帖子详情' },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/user/ProfileView.vue'),
          meta: { title: '个人中心', requiresAuth: true },
        },
        {
          path: 'wallet',
          name: 'wallet',
          component: () => import('@/views/user/WalletView.vue'),
          meta: { title: '我的钱包', requiresAuth: true },
        },
        {
          path: 'favorites',
          name: 'favorites',
          component: () => import('@/views/user/FavoritesView.vue'),
          meta: { title: '我的收藏', requiresAuth: true },
        },
        {
          path: 'history',
          name: 'history',
          component: () => import('@/views/user/HistoryView.vue'),
          meta: { title: '浏览历史', requiresAuth: true },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/user/SettingsView.vue'),
          meta: { title: '账号设置', requiresAuth: true },
        },
      ],
    },

    /* ---------------- 管理后台 ---------------- */
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/views/admin/AdminDashboardView.vue'),
          meta: { title: '数据看板', requiresAuth: true, requiresAdmin: true },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/views/admin/AdminUserView.vue'),
          meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true },
        },
        {
          path: 'content',
          name: 'admin-content',
          component: () => import('@/views/admin/AdminContentView.vue'),
          meta: { title: '内容审核与仲裁', requiresAuth: true, requiresAdmin: true },
        },
        {
          path: 'tasks',
          name: 'admin-tasks',
          component: () => import('@/views/admin/AdminTaskView.vue'),
          meta: { title: '任务管理', requiresAuth: true, requiresAdmin: true },
        },
        {
          path: 'coins',
          name: 'admin-coins',
          component: () => import('@/views/admin/AdminCoinView.vue'),
          meta: { title: '虚拟货币管理', requiresAuth: true, requiresAdmin: true },
        },
      ],
    },

    /* ---------------- 404 ---------------- */
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

/* ------------------------------------------------------------
 * 全局前置守卫
 * ---------------------------------------------------------- */
router.beforeEach((to) => {
  // 1. 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} · ${import.meta.env.VITE_APP_TITLE}`
  }

  const token = getToken()

  // 2. 需要登录但没有凭证 → 去登录页，并记住原本要去的地址
  if (to.meta.requiresAuth && !token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 3. 已登录还想去登录页 → 直接回任务大厅
  if (to.name === 'login' && token) {
    return { path: '/tasks' }
  }

  // 4. 需要管理员角色
  if (to.meta.requiresAdmin) {
    const userStore = useUserStore()
    // 用户信息还没拉到时先放行，页面内接口会做真正的权限校验
    if (userStore.user && !userStore.isAdmin) {
      return { name: 'task-hall' }
    }
  }

  return true
})

export default router
