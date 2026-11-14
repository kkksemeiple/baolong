<script setup lang="ts">
/**
 * 用户端布局
 * 顶部导航 + 内容区 + 页脚
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

/** 顶部导航项 */
const navs = [
  { name: 'task-hall', path: '/tasks', label: '任务大厅', icon: 'Shop' },
  { name: 'forum', path: '/forum', label: '社区论坛', icon: 'ChatDotRound' },
]

/** 当前高亮的导航（任务详情也高亮任务大厅） */
const activeNav = computed(() => {
  if (route.path.startsWith('/forum')) return '/forum'
  return '/tasks'
})

const keyword = ref('')

/** 顶部搜索：跳到任务大厅并带上关键词 */
function handleSearch() {
  router.push({ path: '/tasks', query: keyword.value ? { keyword: keyword.value } : {} })
}

/** 下拉菜单点击 */
async function handleCommand(command: string) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    } catch {
      return
    }
    await userStore.logout()
    appStore.reset()
    ElMessage.success('已退出登录')
    router.push('/login')
    return
  }
  router.push(command)
}

onMounted(async () => {
  if (!userStore.isLogin) return
  // 静默刷新用户信息与未读数
  try {
    await userStore.fetchMe()
    await appStore.refreshUnread()
  } catch {
    // 失败不阻塞页面
  }
})
</script>

<template>
  <div class="layout">
    <header class="header">
      <div class="header-inner">
        <!-- 品牌 -->
        <router-link to="/tasks" class="brand">
          <span class="brand-mark">暴</span>
          <span class="brand-text">暴龙万事屋</span>
        </router-link>

        <!-- 主导航 -->
        <nav class="nav">
          <router-link
            v-for="item in navs"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: activeNav === item.path }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </nav>

        <!-- 搜索 -->
        <div class="search">
          <el-input
            v-model="keyword"
            placeholder="搜索任务关键词"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #append>
              <el-button :icon="'Search'" @click="handleSearch" />
            </template>
          </el-input>
        </div>

        <!-- 右侧操作区 -->
        <div class="actions">
          <el-button type="primary" round @click="router.push('/publish')">
            <el-icon class="mr-4"><Plus /></el-icon>
            发布任务
          </el-button>

          <template v-if="userStore.isLogin">
            <!-- 通知 -->
            <el-badge
              :value="appStore.unread.total"
              :hidden="!appStore.hasUnread"
              :max="99"
              class="ml-12"
            >
              <el-button circle text @click="router.push('/notifications')">
                <el-icon :size="18"><Bell /></el-icon>
              </el-button>
            </el-badge>

            <!-- 用户菜单 -->
            <el-dropdown trigger="click" @command="handleCommand">
              <div class="user-entry">
                <el-avatar :size="32" :src="userStore.user?.avatar ?? undefined">
                  {{ userStore.displayName.slice(0, 1) }}
                </el-avatar>
                <span class="user-name">{{ userStore.displayName }}</span>
                <span class="credit">{{ userStore.creditScore }} 分</span>
                <el-icon><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="/my-tasks">我的任务</el-dropdown-item>
                  <el-dropdown-item command="/wallet">我的钱包</el-dropdown-item>
                  <el-dropdown-item command="/favorites">我的收藏</el-dropdown-item>
                  <el-dropdown-item command="/history">浏览历史</el-dropdown-item>
                  <el-dropdown-item command="/messages">消息</el-dropdown-item>
                  <el-dropdown-item command="/settings" divided>账号设置</el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" command="/admin/dashboard">
                    管理后台
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>

          <template v-else>
            <el-button text @click="router.push('/login')">登录</el-button>
            <el-button type="primary" plain @click="router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="main">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>

    <footer class="footer">
      <span>暴龙万事屋 · 校园委托互助平台</span>
      <span class="dot">·</span>
      <span>屋币仅限平台内使用，不可充值、提现或转让</span>
    </footer>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 20px;
  max-width: 1180px;
  height: 60px;
  margin: 0 auto;
  padding: 0 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #2f7ce0);
  border-radius: 8px;
}

.brand-text {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.nav {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  font-size: 14px;
  color: #606266;
  border-radius: 6px;
  transition: all 0.2s;
}

.nav-item:hover {
  color: #409eff;
  background: #f2f7ff;
}

.nav-item.active {
  color: #409eff;
  font-weight: 600;
  background: #ecf5ff;
}

.search {
  flex: 1;
  max-width: 360px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  cursor: pointer;
  border-radius: 20px;
  transition: background 0.2s;
}

.user-entry:hover {
  background: #f5f7fa;
}

.user-name {
  font-size: 14px;
  color: #303133;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.credit {
  font-size: 12px;
  color: #e6a23c;
}

.mr-4 {
  margin-right: 4px;
}

.ml-12 {
  margin-left: 12px;
}

.main {
  flex: 1;
  width: 100%;
}

.footer {
  padding: 20px 16px;
  font-size: 12px;
  color: #909399;
  text-align: center;
  background: #fff;
  border-top: 1px solid #ebeef5;
}

.dot {
  margin: 0 6px;
}

@media (max-width: 960px) {
  .search,
  .brand-text,
  .credit {
    display: none;
  }
}
</style>
