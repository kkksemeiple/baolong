<script setup lang="ts">
/**
 * 管理后台布局
 * 左侧菜单 + 右侧内容
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const menus = [
  { path: '/admin/dashboard', label: '数据看板', icon: 'DataLine' },
  { path: '/admin/users', label: '用户管理', icon: 'User' },
  { path: '/admin/content', label: '内容审核与仲裁', icon: 'DocumentChecked' },
  { path: '/admin/tasks', label: '任务管理', icon: 'Tickets' },
  { path: '/admin/coins', label: '虚拟货币管理', icon: 'Coin' },
]

const activeMenu = computed(() => route.path)
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <span class="brand-mark">暴</span>
        <div>
          <div class="brand-title">暴龙万事屋</div>
          <div class="brand-sub">管理后台</div>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <el-button text @click="router.push('/tasks')">
          <el-icon class="mr-4"><Back /></el-icon>
          返回用户端
        </el-button>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ route.meta.title }}</span>
        <div class="header-right">
          <el-tag type="warning" size="small" effect="plain">
            {{ userStore.isSadmin ? '超级管理员' : '管理员' }}
          </el-tag>
          <span class="user">{{ userStore.displayName }}</span>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.aside {
  display: flex;
  flex-direction: column;
  background: #1f2d3d;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #2f7ce0);
  border-radius: 8px;
}

.brand-title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
}

.brand-sub {
  font-size: 12px;
  color: #8b98a5;
}

.menu {
  flex: 1;
  border-right: none;
  background: transparent;
}

.menu :deep(.el-menu-item) {
  color: #c0c4cc;
}

.menu :deep(.el-menu-item:hover) {
  color: #fff;
  background: #2b3a4d;
}

.menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: #409eff;
}

.aside-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.aside-footer :deep(.el-button) {
  color: #c0c4cc;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user {
  font-size: 14px;
  color: #606266;
}

.main {
  padding: 20px;
  background: #f5f7fa;
}

.mr-4 {
  margin-right: 4px;
}
</style>
