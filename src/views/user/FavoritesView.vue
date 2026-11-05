<script setup lang="ts">
/**
 * 我的收藏（UC-03）
 * 任务与帖子统一管理，失效内容会标灰且不可点击。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { userApi } from '@/api'
import type { FavTargetType, FavoriteItem } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const list = ref<FavoriteItem[]>([])
const loading = ref(false)
const targetType = ref<FavTargetType | ''>('')

async function load() {
  loading.value = true
  try {
    const res = await userApi.fetchFavorites({
      targetType: (targetType.value || undefined) as FavTargetType | undefined,
      size: 50,
    })
    list.value = res.list
  } finally {
    loading.value = false
  }
}

async function remove(item: FavoriteItem) {
  await userApi.removeFavorite(item.targetType, item.targetId)
  ElMessage.success('已取消收藏')
  await load()
}

function open(item: FavoriteItem) {
  if (item.invalid) {
    ElMessage.info('该内容已失效')
    return
  }
  router.push(item.targetType === 'TASK' ? `/tasks/${item.targetId}` : `/forum/${item.targetId}`)
}

onMounted(load)
</script>

<template>
  <div class="page-container narrow">
    <h1 class="page-title">我的收藏</h1>

    <el-card shadow="never" class="card">
      <el-radio-group v-model="targetType" class="mb-16" @change="load">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="TASK">任务</el-radio-button>
        <el-radio-button value="POST">帖子</el-radio-button>
      </el-radio-group>

      <div v-loading="loading">
        <div v-if="list.length" class="list">
          <div
            v-for="item in list"
            :key="item.id"
            class="row"
            :class="{ invalid: item.invalid }"
            @click="open(item)"
          >
            <el-tag size="small" :type="item.targetType === 'TASK' ? 'primary' : 'success'">
              {{ item.targetType === 'TASK' ? '任务' : '帖子' }}
            </el-tag>

            <div class="main">
              <div class="title ellipsis">{{ item.title }}</div>
              <div class="summary ellipsis">{{ item.summary }}</div>
            </div>

            <span class="time">{{ formatDateTime(item.createdAt) }}</span>

            <el-button text size="small" type="danger" @click.stop="remove(item)">
              取消收藏
            </el-button>
          </div>
        </div>

        <EmptyState
          v-else-if="!loading"
          text="还没有收藏内容"
          description="在任务详情或帖子页面点收藏，就会出现在这里"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 860px;
}

.page-title {
  margin: 0 0 20px;
  font-size: 22px;
  color: #303133;
}

.card {
  border-radius: 10px;
}

.mb-16 {
  margin-bottom: 16px;
}

.list {
  display: flex;
  flex-direction: column;
}

.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 0;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
}

.row:hover {
  background: #fafcff;
}

.row.invalid {
  cursor: not-allowed;
  opacity: 0.5;
}

.main {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.summary {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.time {
  font-size: 12px;
  color: #c0c4cc;
  white-space: nowrap;
}
</style>
