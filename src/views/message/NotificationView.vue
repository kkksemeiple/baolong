<script setup lang="ts">
/**
 * 通知中心（MS-02）
 *
 * 支持：按类型筛选、只看未读、单条已读、全部已读、点击跳转。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { messageApi } from '@/api'
import type { NotifType, Notification } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'
import { fromNow } from '@/utils/format'
import { NOTIF_TYPE_META } from '@/utils/constants'

const router = useRouter()
const appStore = useAppStore()

const list = ref<Notification[]>([])
const loading = ref(false)
const typeFilter = ref<NotifType | ''>('')
const unreadOnly = ref(false)

const typeOptions = Object.entries(NOTIF_TYPE_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

async function load() {
  loading.value = true
  try {
    const res = await messageApi.fetchNotifications({
      type: (typeFilter.value || undefined) as NotifType | undefined,
      unreadOnly: unreadOnly.value,
      size: 30,
    })
    list.value = res.list
  } finally {
    loading.value = false
  }
}

/** 点击通知：标记已读并跳转 */
async function handleClick(n: Notification) {
  if (!n.readFlag) {
    await messageApi.markNotificationsRead([n.id])
    n.readFlag = true
    await appStore.refreshUnread()
  }
  if (n.link) router.push(n.link)
}

/** 全部已读 */
async function handleReadAll() {
  await messageApi.markAllNotificationsRead()
  ElMessage.success('已全部标记为已读')
  await load()
  await appStore.refreshUnread()
}

onMounted(load)
</script>

<template>
  <div class="page-container narrow">
    <div class="page-head">
      <h1 class="page-title">通知中心</h1>
      <el-button :disabled="!appStore.hasUnread" @click="handleReadAll">全部已读</el-button>
    </div>

    <el-card shadow="never" class="card">
      <div class="filters">
        <el-select v-model="typeFilter" placeholder="全部类型" clearable class="w-140" @change="load">
          <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>

        <el-checkbox v-model="unreadOnly" @change="load">只看未读</el-checkbox>

        <el-button text :icon="'Refresh'" @click="load">刷新</el-button>
      </div>

      <div v-loading="loading">
        <div v-if="list.length" class="list">
          <div
            v-for="n in list"
            :key="n.id"
            class="item"
            :class="{ unread: !n.readFlag }"
            @click="handleClick(n)"
          >
            <div class="item-head">
              <StatusTag :value="n.type" kind="notif" size="small" />
              <span class="item-title">{{ n.title }}</span>
              <span v-if="!n.readFlag" class="dot" />
              <span class="item-time">{{ fromNow(n.createdAt) }}</span>
            </div>
            <p class="item-content">{{ n.content }}</p>
          </div>
        </div>

        <EmptyState
          v-else-if="!loading"
          text="没有通知"
          description="任务进展、资金变动和论坛互动都会在这里提醒你"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 860px;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 22px;
  color: #303133;
}

.card {
  border-radius: 10px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 14px;
  margin-bottom: 6px;
  border-bottom: 1px solid #f2f6fc;
}

.w-140 {
  width: 140px;
}

.list {
  display: flex;
  flex-direction: column;
}

.item {
  padding: 14px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  border-radius: 6px;
  transition: background 0.2s;
}

.item:hover {
  background: #fafcff;
}

.item.unread {
  background: #f7fbff;
}

.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.dot {
  width: 7px;
  height: 7px;
  background: #f56c6c;
  border-radius: 50%;
}

.item-time {
  margin-left: auto;
  font-size: 12px;
  color: #c0c4cc;
}

.item-content {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}
</style>
