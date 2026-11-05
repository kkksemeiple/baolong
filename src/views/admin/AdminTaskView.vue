<script setup lang="ts">
/**
 * 任务管理（AD-04）
 * 管理员可查看全部任务、下架违规或异常任务（下架后冻结屋币全额退回委托方）。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { adminApi } from '@/api'
import type { Task } from '@/api/types'
import StatusTag from '@/components/StatusTag.vue'
import { formatCoin, formatDateTime } from '@/utils/format'
import { TASK_STATUS_META, TASK_TYPE_META } from '@/utils/constants'

const router = useRouter()

const list = ref<Task[]>([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')

const statusOptions = Object.entries(TASK_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

async function load() {
  loading.value = true
  try {
    const res = await adminApi.fetchAdminTasks({
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
      page: 1,
      size: 50,
    })
    list.value = res.list
  } finally {
    loading.value = false
  }
}

async function handleOffline(row: Task) {
  try {
    const { value } = await ElMessageBox.prompt(
      `下架「${row.title}」会将冻结的 ${row.reward} WB 全额退回委托方，请填写原因`,
      '下架任务',
      {
        inputType: 'textarea',
        inputValidator: (v) => (v && v.trim().length >= 2 ? true : '请填写下架原因'),
      },
    )
    await adminApi.offlineTask(row.id, value)
    ElMessage.success('已下架，屋币已退回')
    await load()
  } catch {
    /* 用户取消 */
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never" class="card">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索任务标题"
        clearable
        class="w-240"
        @keyup.enter="load"
      />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="w-140" @change="load">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" plain @click="load">查询</el-button>
      <el-button text :icon="'Refresh'" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="任务" min-width="240">
        <template #default="{ row }">
          <div class="title-cell" @click="router.push(`/tasks/${row.id}`)">
            {{ row.title }}
          </div>
          <div class="sub">{{ TASK_TYPE_META[row.type]?.label }} · {{ row.location || '地点待定' }}</div>
        </template>
      </el-table-column>

      <el-table-column label="委托方" width="120">
        <template #default="{ row }">{{ row.publisher?.nickname ?? '—' }}</template>
      </el-table-column>

      <el-table-column label="接单方" width="120">
        <template #default="{ row }">
          {{ row.taker?.nickname ?? '—' }}
        </template>
      </el-table-column>

      <el-table-column label="报酬" width="100" align="right">
        <template #default="{ row }">
          <span class="reward">{{ formatCoin(row.reward) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <StatusTag :value="row.status" kind="task" size="small" />
        </template>
      </el-table-column>

      <el-table-column label="发布 / 截止" width="180">
        <template #default="{ row }">
          <div class="sub">{{ formatDateTime(row.createdAt) }}</div>
          <div class="sub">{{ formatDateTime(row.deadline) }}</div>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="router.push(`/tasks/${row.id}`)">
            查看
          </el-button>
          <el-button
            v-if="!['SETTLED', 'CANCELLED', 'EXPIRED'].includes(row.status)"
            size="small"
            link
            type="danger"
            @click="handleOffline(row)"
          >
            下架
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.card {
  border-radius: 10px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.w-240 {
  width: 240px;
}

.w-140 {
  width: 140px;
}

.title-cell {
  font-size: 14px;
  color: #303133;
  cursor: pointer;
}

.title-cell:hover {
  color: #409eff;
}

.sub {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.reward {
  font-weight: 600;
  color: #e6a23c;
}
</style>
