<script setup lang="ts">
/**
 * 任务卡片
 * 任务大厅、我的任务、推荐列表共用。
 */
import { computed } from 'vue'

import type { Task } from '@/api/types'
import { TASK_TYPE_META } from '@/utils/constants'
import { formatCoin, fromNow, timeLeft } from '@/utils/format'
import StatusTag from './StatusTag.vue'

const props = defineProps<{ task: Task; showActions?: boolean }>()
const emit = defineEmits<{
  favorite: [task: Task]
  claim: [task: Task]
  click: [task: Task]
}>()

const typeMeta = computed(() => TASK_TYPE_META[props.task.type] ?? TASK_TYPE_META.OTHER)
const left = computed(() => timeLeft(props.task.deadline))

/** 是否处于「可以接单」的状态 */
const canClaim = computed(() => props.task.status === 'OPEN' && !props.task.mine && props.showActions)
</script>

<template>
  <el-card shadow="hover" class="task-card" @click="emit('click', task)">
    <div class="head">
      <div class="title-row">
        <el-icon class="type-icon"><component :is="typeMeta.icon" /></el-icon>
        <span class="title ellipsis">{{ task.title }}</span>
      </div>
      <StatusTag :value="task.status" kind="task" size="small" />
    </div>

    <p class="desc ellipsis-2">{{ task.description }}</p>

    <div class="meta">
      <span class="meta-item">
        <el-icon><Location /></el-icon>
        <span class="ellipsis">{{ task.location || '地点待定' }}</span>
      </span>
      <span class="meta-item">
        <el-icon><Clock /></el-icon>
        <span :class="{ urgent: left.urgent }">{{ left.text }}</span>
      </span>
    </div>

    <div class="footer">
      <div class="publisher">
        <el-avatar :size="22" :src="task.publisher?.avatar ?? undefined">
          {{ task.publisher?.nickname?.slice(0, 1) ?? '?' }}
        </el-avatar>
        <span class="name">{{ task.publisher?.nickname ?? '匿名' }}</span>
        <span class="credit">信用 {{ task.publisher?.creditScore ?? '—' }}</span>
      </div>

      <div class="right">
        <span class="reward">{{ formatCoin(task.reward) }}</span>

        <el-button
          v-if="task.status === 'OPEN'"
          text
          :type="task.favorited ? 'warning' : 'info'"
          :icon="task.favorited ? 'StarFilled' : 'Star'"
          @click.stop="emit('favorite', task)"
        />

        <el-button
          v-if="canClaim"
          type="primary"
          size="small"
          round
          @click.stop="emit('claim', task)"
        >
          接单
        </el-button>
      </div>
    </div>

    <div class="time">{{ fromNow(task.createdAt) }}</div>
  </el-card>
</template>

<style scoped>
.task-card {
  cursor: pointer;
  transition: transform 0.2s;
}

.task-card:hover {
  transform: translateY(-2px);
}

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.type-icon {
  color: #409eff;
  flex-shrink: 0;
}

.title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.desc {
  margin: 10px 0;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
}

.meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.urgent {
  color: #f56c6c;
  font-weight: 600;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
}

.publisher {
  display: flex;
  align-items: center;
  gap: 6px;
}

.name {
  font-size: 12px;
  color: #606266;
}

.credit {
  font-size: 12px;
  color: #e6a23c;
}

.right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.reward {
  font-size: 16px;
  font-weight: 700;
  color: #e6a23c;
}

.time {
  margin-top: 8px;
  font-size: 12px;
  color: #c0c4cc;
  text-align: right;
}
</style>
