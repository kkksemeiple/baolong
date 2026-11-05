<script setup lang="ts">
/**
 * 浏览历史（UC-04）
 * 最多保留最近 200 条，支持删除单条与一键清空。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { userApi } from '@/api'
import type { HistoryItem } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { fromNow } from '@/utils/format'

const router = useRouter()

const list = ref<HistoryItem[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await userApi.fetchHistory(undefined, 50)
    list.value = res.list
  } finally {
    loading.value = false
  }
}

async function removeOne(item: HistoryItem) {
  await userApi.deleteHistoryItem(item.id)
  list.value = list.value.filter((x) => x.id !== item.id)
}

async function clearAll() {
  try {
    await ElMessageBox.confirm('清空后不可恢复，确定清空全部浏览记录吗？', '清空历史', {
      type: 'warning',
    })
    await userApi.clearHistory()
    list.value = []
    ElMessage.success('已清空')
  } catch {
    /* 用户取消 */
  }
}

function open(item: HistoryItem) {
  router.push(item.targetType === 'TASK' ? `/tasks/${item.targetId}` : `/forum/${item.targetId}`)
}

onMounted(load)
</script>

<template>
  <div class="page-container narrow">
    <div class="page-head">
      <h1 class="page-title">浏览历史</h1>
      <el-button :disabled="!list.length" @click="clearAll">清空历史</el-button>
    </div>

    <el-card shadow="never" class="card">
      <div v-loading="loading">
        <div v-if="list.length" class="list">
          <div v-for="item in list" :key="item.id" class="row" @click="open(item)">
            <el-tag size="small" :type="item.targetType === 'TASK' ? 'primary' : 'success'">
              {{ item.targetType === 'TASK' ? '任务' : '帖子' }}
            </el-tag>
            <span class="title ellipsis">{{ item.title }}</span>
            <span class="time">{{ fromNow(item.viewedAt) }}</span>
            <el-button text size="small" @click.stop="removeOne(item)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>

        <EmptyState
          v-else-if="!loading"
          text="还没有浏览记录"
          description="浏览过的任务和帖子会记录在这里"
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

.list {
  display: flex;
  flex-direction: column;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 0;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
}

.row:hover {
  background: #fafcff;
}

.title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: #303133;
}

.time {
  font-size: 12px;
  color: #c0c4cc;
  white-space: nowrap;
}
</style>
