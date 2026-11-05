<script setup lang="ts">
/**
 * 我的任务（UC-02）
 *
 * 两个页签：我发布的 / 我接取的
 * 每个任务按状态显示可执行的操作：
 * - 我发布的（待接取）：取消任务
 * - 我发布的（待确认）：确认完成 / 拒绝确认
 * - 我接取的（进行中）：提交完成 / 放弃
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { taskApi } from '@/api'
import type { Task, TaskStatus } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { TASK_TYPE_META } from '@/utils/constants'
import { formatCoin, formatDateTime, timeLeft } from '@/utils/format'

const router = useRouter()

/** 当前页签 */
const role = ref<'publisher' | 'taker'>('publisher')
/** 状态筛选 */
const statusFilter = ref<TaskStatus | ''>('')

const list = ref<Task[]>([])
const loading = ref(false)

/** 状态筛选选项（不同页签关心的状态不同） */
const statusOptions = computed(() => {
  const base = [
    { value: 'OPEN', label: '待接取' },
    { value: 'TAKEN', label: '进行中' },
    { value: 'SUBMITTED', label: '待确认' },
    { value: 'SETTLED', label: '已结算' },
    { value: 'DISPUTED', label: '争议中' },
    { value: 'CANCELLED', label: '已取消' },
    { value: 'EXPIRED', label: '已过期' },
  ]
  return role.value === 'taker' ? base.filter((s) => s.value !== 'OPEN') : base
})

async function load() {
  loading.value = true
  try {
    const res = await taskApi.fetchMyTasks({
      role: role.value,
      status: (statusFilter.value || undefined) as TaskStatus | undefined,
      size: 20,
    })
    list.value = res.list
  } finally {
    loading.value = false
  }
}

function switchRole(next: 'publisher' | 'taker') {
  role.value = next
  statusFilter.value = ''
  load()
}

/* ---------------- 操作 ---------------- */

async function cancelTask(task: Task) {
  try {
    await ElMessageBox.confirm('取消后冻结的报酬会全额退回，确定吗？', '取消任务', {
      type: 'warning',
    })
    await taskApi.cancelTask(task.id)
    ElMessage.success('任务已取消')
    await load()
  } catch {
    /* 用户取消 */
  }
}

async function confirmTask(task: Task) {
  try {
    await ElMessageBox.confirm('确认后报酬立即划转给接单方，不可撤销。', '确认完成', {
      type: 'warning',
    })
    await taskApi.confirmTask(task.id)
    ElMessage.success('已确认完成')
    await load()
  } catch {
    /* 用户取消 */
  }
}

async function rejectTask(task: Task) {
  try {
    const { value } = await ElMessageBox.prompt('请说明拒绝原因（至少 5 个字）', '拒绝确认', {
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim().length >= 5 ? true : '请至少填写 5 个字'),
    })
    await taskApi.rejectTask(task.id, value)
    ElMessage.success('已拒绝确认')
    await load()
  } catch {
    /* 用户取消 */
  }
}

async function submitTask(task: Task) {
  try {
    const { value } = await ElMessageBox.prompt('请填写交付说明', '提交完成', {
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim().length >= 5 ? true : '请至少填写 5 个字'),
    })
    await taskApi.submitTask(task.id, { content: value })
    ElMessage.success('已提交，等待委托方确认')
    await load()
  } catch {
    /* 用户取消 */
  }
}

async function abandonTask(task: Task) {
  try {
    await ElMessageBox.confirm('放弃后任务会回到大厅，多次无故放弃会影响信用分。', '放弃任务', {
      type: 'warning',
    })
    await taskApi.abandonTask(task.id)
    ElMessage.success('已放弃任务')
    await load()
  } catch {
    /* 用户取消 */
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <h1 class="page-title">我的任务</h1>
      <el-button type="primary" @click="router.push('/publish')">发布新任务</el-button>
    </div>

    <el-card shadow="never" class="tabs-card">
      <el-tabs :model-value="role" @tab-change="switchRole($event as 'publisher' | 'taker')">
        <el-tab-pane label="我发布的" name="publisher" />
        <el-tab-pane label="我接取的" name="taker" />
      </el-tabs>

      <div class="filters">
        <el-select
          v-model="statusFilter"
          placeholder="全部状态"
          clearable
          class="w-160"
          @change="load"
        >
          <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </div>

      <div v-loading="loading">
        <div v-if="list.length" class="list">
          <div v-for="t in list" :key="t.id" class="row">
            <div class="row-main" @click="router.push(`/tasks/${t.id}`)">
              <div class="row-title">
                <el-icon class="type-icon">
                  <component :is="TASK_TYPE_META[t.type]?.icon ?? 'More'" />
                </el-icon>
                <span class="title ellipsis">{{ t.title }}</span>
                <StatusTag :value="t.status" kind="task" size="small" />
              </div>
              <div class="row-meta">
                <span>{{ formatCoin(t.reward) }}</span>
                <span>·</span>
                <span>{{ role === 'taker' ? '委托方' : '接单方' }}：
                  {{ role === 'taker' ? (t.publisher?.nickname ?? '—') : (t.taker?.nickname ?? '等待接单') }}
                </span>
                <span>·</span>
                <span>截止 {{ formatDateTime(t.deadline) }}</span>
                <span v-if="t.status === 'OPEN'">· {{ timeLeft(t.deadline).text }}</span>
              </div>
            </div>

            <div class="row-actions">
              <!-- 我发布的 -->
              <template v-if="role === 'publisher'">
                <el-button v-if="t.status === 'OPEN'" size="small" @click="cancelTask(t)">
                  取消
                </el-button>
                <el-button
                  v-if="t.status === 'SUBMITTED'"
                  size="small"
                  type="success"
                  @click="confirmTask(t)"
                >
                  确认完成
                </el-button>
                <el-button v-if="t.status === 'SUBMITTED'" size="small" @click="rejectTask(t)">
                  拒绝确认
                </el-button>
              </template>

              <!-- 我接取的 -->
              <template v-else>
                <el-button
                  v-if="t.status === 'TAKEN'"
                  size="small"
                  type="primary"
                  @click="submitTask(t)"
                >
                  提交完成
                </el-button>
                <el-button v-if="t.status === 'TAKEN'" size="small" @click="abandonTask(t)">
                  放弃
                </el-button>
                <el-button
                  v-if="['TAKEN', 'SUBMITTED'].includes(t.status)"
                  size="small"
                  @click="router.push({ path: '/messages', query: { taskId: String(t.id) } })"
                >
                  联系对方
                </el-button>
              </template>

              <el-button size="small" text @click="router.push(`/tasks/${t.id}`)">详情</el-button>
            </div>
          </div>
        </div>

        <EmptyState
          v-else-if="!loading"
          :text="role === 'publisher' ? '你还没有发布过任务' : '你还没有接取过任务'"
          :action-text="role === 'publisher' ? '去发布' : '去大厅看看'"
          @action="router.push(role === 'publisher' ? '/publish' : '/tasks')"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
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

.tabs-card {
  border-radius: 10px;
}

.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.w-160 {
  width: 160px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  background: #fff;
  border-bottom: 1px solid #f2f6fc;
  transition: background 0.2s;
}

.row:last-child {
  border-bottom: none;
}

.row:hover {
  background: #fafcff;
}

.row-main {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.row-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-icon {
  color: #409eff;
}

.title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.row-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex-shrink: 0;
}
</style>
