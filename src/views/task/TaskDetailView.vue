<script setup lang="ts">
/**
 * 任务详情页（TK-03）
 *
 * 这是消息会话的入口页（修正了原需求中「依赖不存在的任务详情页」的问题）。
 *
 * 按「角色 + 任务状态」决定显示哪些操作：
 * - 游客：只能看，操作时引导登录
 * - 接单方（未接取）：接单、议价、收藏、举报
 * - 接单方（已接取）：联系对方、提交完成、放弃任务
 * - 委托方：取消（待接取时）、确认完成、拒绝确认、申请仲裁
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { taskApi } from '@/api'
import type { Task } from '@/api/types'
import StatusTag from '@/components/StatusTag.vue'
import { useUserStore } from '@/stores/user'
import { TASK_TYPE_META } from '@/utils/constants'
import { formatCoin, formatDateTime, timeLeft } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const taskId = computed(() => Number(route.params.id))
const task = ref<Task | null>(null)
const loading = ref(false)

/* ---------------- 角色判断 ---------------- */
const isPublisher = computed(() => task.value?.publisherId === userStore.user?.id)
const isTaker = computed(() => Boolean(task.value?.takerId) && task.value?.takerId === userStore.user?.id)
/** 是否与当前任务无关的第三方（可接单/议价） */
const isOutsider = computed(() => !isPublisher.value && !isTaker.value)

const typeMeta = computed(() => (task.value ? TASK_TYPE_META[task.value.type] : null))
const left = computed(() => (task.value ? timeLeft(task.value.deadline) : null))

/* ---------------- 数据加载 ---------------- */
async function load() {
  loading.value = true
  try {
    task.value = await taskApi.fetchTaskDetail(taskId.value)
  } catch {
    router.replace('/tasks')
  } finally {
    loading.value = false
  }
}

/* ---------------- 操作前登录检查 ---------------- */
function requireLogin(): boolean {
  if (userStore.isLogin) return true
  ElMessage.warning('请先登录')
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

/* ---------------- 接单 ---------------- */
async function handleClaim() {
  if (!requireLogin() || !task.value) return
  try {
    await ElMessageBox.confirm(
      `确认接取「${task.value.title}」吗？接单后请按时完成，无故放弃会扣信用分。`,
      '接取任务',
      { type: 'warning', confirmButtonText: '确认接单' },
    )
  } catch {
    return
  }
  try {
    await taskApi.claimTask(task.value.id)
    ElMessage.success('接单成功')
    await load()
  } catch {
    await load()
  }
}

/* ---------------- 收藏 ---------------- */
async function handleFavorite() {
  if (!requireLogin() || !task.value) return
  if (task.value.favorited) {
    await taskApi.unfavoriteTask(task.value.id)
    task.value.favorited = false
    ElMessage.success('已取消收藏')
  } else {
    await taskApi.favoriteTask(task.value.id)
    task.value.favorited = true
    ElMessage.success('已收藏')
  }
}

/* ---------------- 进入会话 ---------------- */
async function openChat() {
  if (!requireLogin()) return
  router.push({ path: '/messages', query: { taskId: String(taskId.value) } })
}

/* ---------------- 提交完成 ---------------- */
const submitVisible = ref(false)
const submitForm = reactive({ content: '' })

function openSubmit() {
  submitForm.content = ''
  submitVisible.value = true
}

async function doSubmit() {
  if (!task.value) return
  if (!submitForm.content.trim()) {
    ElMessage.warning('请填写交付说明')
    return
  }
  await taskApi.submitTask(task.value.id, { content: submitForm.content })
  submitVisible.value = false
  ElMessage.success('已提交，等待委托方确认')
  await load()
}

/* ---------------- 确认完成 ---------------- */
async function doConfirm() {
  if (!task.value) return
  try {
    await ElMessageBox.confirm(
      '确认后报酬会立即划转给接单方，且不可撤销。确定完成了吗？',
      '确认完成',
      { type: 'warning', confirmButtonText: '确认完成' },
    )
  } catch {
    return
  }
  await taskApi.confirmTask(task.value.id)
  ElMessage.success('已确认完成，报酬已划转')
  await load()
}

/* ---------------- 拒绝确认 ---------------- */
async function doReject() {
  if (!task.value) return
  try {
    const { value } = await ElMessageBox.prompt('请说明拒绝的原因（接单方会看到）', '拒绝确认', {
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim().length >= 5 ? true : '请至少填写 5 个字'),
    })
    await taskApi.rejectTask(task.value.id, value)
    ElMessage.success('已拒绝确认，任务回到进行中')
    await load()
  } catch {
    // 用户取消
  }
}

/* ---------------- 取消任务 ---------------- */
async function doCancel() {
  if (!task.value) return
  try {
    await ElMessageBox.confirm('取消后冻结的报酬会全额退回，确定取消吗？', '取消任务', {
      type: 'warning',
    })
    await taskApi.cancelTask(task.value.id)
    ElMessage.success('任务已取消，报酬已退回')
    await load()
  } catch {
    /* 取消操作 */
  }
}

/* ---------------- 放弃任务 ---------------- */
async function doAbandon() {
  if (!task.value) return
  try {
    await ElMessageBox.confirm(
      '放弃后任务会重新回到大厅，多次无故放弃会影响信用分。确定放弃吗？',
      '放弃任务',
      { type: 'warning' },
    )
    await taskApi.abandonTask(task.value.id)
    ElMessage.success('已放弃任务')
    await load()
  } catch {
    /* 取消操作 */
  }
}

/* ---------------- 申请仲裁 ---------------- */
async function doDispute() {
  if (!task.value) return
  try {
    const { value } = await ElMessageBox.prompt('请说明争议原因', '申请仲裁', {
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim().length >= 10 ? true : '请至少填写 10 个字'),
    })
    await taskApi.disputeTask(task.value.id, { reason: value })
    ElMessage.success('已申请仲裁，等待管理员处理')
    await load()
  } catch {
    /* 取消操作 */
  }
}

/* ---------------- 举报 ---------------- */
async function doReport() {
  if (!requireLogin() || !task.value) return
  try {
    const { value } = await ElMessageBox.prompt('请说明举报原因', '举报任务', {
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim().length >= 5 ? true : '请至少填写 5 个字'),
    })
    await taskApi.reportTask(task.value.id, { type: 'OTHER', reason: value })
    ElMessage.success('举报已提交，我们会尽快处理')
  } catch {
    /* 取消操作 */
  }
}

/* ---------------- 议价 ---------------- */
const bargainVisible = ref(false)
const bargainForm = reactive({ amount: 0 })

function openBargain() {
  if (!requireLogin() || !task.value) return
  bargainForm.amount = task.value.reward
  bargainVisible.value = true
}

async function doBargain() {
  if (!task.value) return
  if (bargainForm.amount <= 0) {
    ElMessage.warning('请输入有效的金额')
    return
  }
  await taskApi.bargainTask(task.value.id, { amount: bargainForm.amount })
  bargainVisible.value = false
  ElMessage.success('议价请求已发出，请在会话中继续沟通')
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-container">
    <el-page-header class="mb-16" @back="router.back()">
      <template #content>
        <span class="header-title">任务详情</span>
      </template>
    </el-page-header>

    <template v-if="task">
      <el-card shadow="never" class="detail-card">
        <!-- 标题行 -->
        <div class="title-row">
          <div class="title-left">
            <el-icon v-if="typeMeta" class="type-icon"><component :is="typeMeta.icon" /></el-icon>
            <h1 class="title">{{ task.title }}</h1>
          </div>
          <StatusTag :value="task.status" kind="task" />
        </div>

        <!-- 关键信息 -->
        <div class="facts">
          <div class="fact reward">
            <div class="fact-label">报酬</div>
            <div class="fact-value accent">{{ formatCoin(task.reward) }}</div>
          </div>
          <div class="fact">
            <div class="fact-label">类型</div>
            <div class="fact-value">{{ typeMeta?.label }}</div>
          </div>
          <div class="fact">
            <div class="fact-label">人数需求</div>
            <div class="fact-value">{{ task.peopleNeeded }} 人</div>
          </div>
          <div class="fact">
            <div class="fact-label">剩余时间</div>
            <div class="fact-value" :class="{ urgent: left?.urgent }">{{ left?.text }}</div>
          </div>
        </div>

        <el-divider />

        <!-- 描述 -->
        <section class="block">
          <h3 class="block-title">任务描述</h3>
          <p class="description">{{ task.description }}</p>
        </section>

        <!-- 地点与时间 -->
        <section class="block">
          <h3 class="block-title">地点与时间</h3>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="交付地点">{{ task.location || '待沟通' }}</el-descriptions-item>
            <el-descriptions-item label="截止时间">{{ formatDateTime(task.deadline) }}</el-descriptions-item>
            <el-descriptions-item label="发布时间">{{ formatDateTime(task.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="提交完成">
              {{ formatDateTime(task.submittedAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <!-- 双方信息 -->
        <section class="block">
          <h3 class="block-title">参与人</h3>
          <div class="people">
            <div class="person">
              <el-avatar :size="40" :src="task.publisher?.avatar ?? undefined">
                {{ task.publisher?.nickname?.slice(0, 1) ?? '?' }}
              </el-avatar>
              <div>
                <div class="person-name">{{ task.publisher?.nickname }} <el-tag size="small" type="info">委托方</el-tag></div>
                <div class="person-sub">信用分 {{ task.publisher?.creditScore }}</div>
              </div>
            </div>

            <div v-if="task.taker" class="person">
              <el-avatar :size="40" :src="task.taker.avatar ?? undefined">
                {{ task.taker.nickname?.slice(0, 1) ?? '?' }}
              </el-avatar>
              <div>
                <div class="person-name">{{ task.taker.nickname }} <el-tag size="small" type="success">接单方</el-tag></div>
                <div class="person-sub">信用分 {{ task.taker.creditScore }}</div>
              </div>
            </div>
            <div v-else class="person-empty">暂无人接单</div>
          </div>
        </section>

        <el-divider />

        <!-- 操作区：按角色与状态显示 -->
        <div class="actions">
          <!-- 游客 -->
          <template v-if="!userStore.isLogin">
            <el-button type="primary" @click="requireLogin()">登录后接单</el-button>
          </template>

          <!-- 第三方（未参与） -->
          <template v-else-if="isOutsider">
            <template v-if="task.status === 'OPEN'">
              <el-button type="primary" @click="handleClaim">接取任务</el-button>
              <el-button @click="openBargain">我要议价</el-button>
            </template>
            <el-button @click="handleFavorite">
              {{ task.favorited ? '取消收藏' : '收藏任务' }}
            </el-button>
          </template>

          <!-- 接单方 -->
          <template v-else-if="isTaker">
            <el-button v-if="task.status === 'TAKEN'" type="primary" @click="openChat">联系委托方</el-button>
            <el-button v-if="task.status === 'TAKEN'" type="success" @click="openSubmit">提交完成</el-button>
            <el-button v-if="task.status === 'TAKEN'" @click="doAbandon">放弃任务</el-button>
            <el-button v-if="task.status === 'SUBMITTED'" @click="openChat">联系委托方</el-button>
          </template>

          <!-- 委托方 -->
          <template v-else-if="isPublisher">
            <el-button v-if="task.status === 'OPEN'" @click="doCancel">取消任务</el-button>
            <el-button v-if="['TAKEN', 'SUBMITTED'].includes(task.status)" @click="openChat">
              联系接单方
            </el-button>
            <el-button v-if="task.status === 'SUBMITTED'" type="success" @click="doConfirm">
              确认完成并付款
            </el-button>
            <el-button v-if="task.status === 'SUBMITTED'" @click="doReject">拒绝确认</el-button>
            <el-button v-if="['TAKEN', 'SUBMITTED'].includes(task.status)" @click="doDispute">
              申请仲裁
            </el-button>
          </template>

          <!-- 通用：举报 -->
          <el-button v-if="userStore.isLogin && !isPublisher" text type="danger" @click="doReport">
            举报
          </el-button>
        </div>

        <!-- 委托方自看提示 -->
        <el-alert
          v-if="isPublisher && task.status === 'TAKEN'"
          class="mt-16"
          type="info"
          :closable="false"
          show-icon
          title="接单方正在履约中，完成后会通知你确认。"
        />
      </el-card>
    </template>

    <!-- 提交完成弹窗 -->
    <el-dialog v-model="submitVisible" title="提交完成" width="480px">
      <el-form label-position="top">
        <el-form-item label="交付说明" required>
          <el-input
            v-model="submitForm.content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="说明你完成了哪些内容，方便委托方确认"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" @click="doSubmit">提交</el-button>
      </template>
    </el-dialog>

    <!-- 议价弹窗 -->
    <el-dialog v-model="bargainVisible" title="我要议价" width="420px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="议价最多 5 轮，达成一致后委托方的冻结金额会自动调整"
        class="mb-16"
      />
      <el-form label-position="top">
        <el-form-item label="我希望的金额（WB）">
          <el-input-number v-model="bargainForm.amount" :min="1" :max="500" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bargainVisible = false">取消</el-button>
        <el-button type="primary" @click="doBargain">发起议价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.header-title {
  font-size: 15px;
  font-weight: 600;
}

.detail-card {
  border-radius: 10px;
}

.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.title-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-icon {
  font-size: 20px;
  color: #409eff;
}

.title {
  margin: 0;
  font-size: 22px;
  color: #303133;
}

.facts {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 20px;
  padding: 16px;
  background: #f7f9fc;
  border-radius: 8px;
}

.fact-label {
  font-size: 12px;
  color: #909399;
}

.fact-value {
  margin-top: 4px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.fact-value.accent {
  color: #e6a23c;
  font-size: 20px;
}

.urgent {
  color: #f56c6c;
}

.block {
  margin-bottom: 24px;
}

.block-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.description {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: #606266;
  white-space: pre-wrap;
}

.people {
  display: flex;
  gap: 32px;
}

.person {
  display: flex;
  align-items: center;
  gap: 10px;
}

.person-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #303133;
}

.person-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}

.person-empty {
  font-size: 13px;
  color: #c0c4cc;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.w-full {
  width: 100%;
}

@media (max-width: 720px) {
  .facts {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
