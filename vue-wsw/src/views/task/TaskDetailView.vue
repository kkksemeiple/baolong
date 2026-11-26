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
import type { BargainRecordItem, Task } from '@/api/types'
import StatusTag from '@/components/StatusTag.vue'
import { useUserStore } from '@/stores/user'
import { TASK_TYPE_META, needProof } from '@/utils/constants'
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

/**
 * 是否已过截止时间。
 * 后端大厅已经即时过滤掉过期任务，但「详情页链接」与「页面上停留很久」仍可能看到过期的，
 * 这里让按钮直接变灰，而不是等用户点下去再报 40906。
 */
const expired = computed(() => {
  if (!task.value?.deadline) {
    return false
  }
  return new Date(String(task.value.deadline).replace(/-/g, '/')).getTime() <= Date.now()
})

/* ---------------- 数据加载 ---------------- */
async function load() {
  loading.value = true
  try {
    task.value = await taskApi.fetchTaskDetail(taskId.value)
    await loadBargains()
  } catch {
    router.replace('/tasks')
  } finally {
    loading.value = false
  }
}

/* ---------------- 收到的报价（仅发布方） ---------------- */
const bargains = ref<BargainRecordItem[]>([])
const pendingBargains = computed(() => bargains.value.filter((b) => b.status === 'PENDING'))

async function loadBargains() {
  // 只有发布方、且任务还没被接走时才需要看报价
  if (!task.value || task.value.publisherId !== userStore.user?.id) return
  if (task.value.status !== 'OPEN') return
  try {
    bargains.value = await taskApi.fetchBargains(task.value.id)
  } catch {
    // 报价列表拿不到不影响详情页主体
  }
}

async function respondBargain(item: BargainRecordItem, accept: boolean) {
  if (!task.value) return
  try {
    if (!accept) {
      await ElMessageBox.confirm(
        `确定拒绝 ${item.fromNickname || '对方'} 的报价 ${item.amount} WB 吗？`,
        '拒绝报价',
        { type: 'warning' },
      )
    }
    await taskApi.respondBargain(task.value.id, { bargainId: item.id, accept })
    ElMessage.success(accept ? '已同意报价，任务报酬已更新' : '已拒绝该报价')
    await load()
  } catch {
    // 用户取消或后端报错，拦截器已提示
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
const submitForm = reactive({ content: '', images: [] as string[] })
const uploadingProof = ref(false)

/** 与后端的 needProof 规则一致：除「其他」外，提交时都要有凭证图片 */
const mustUploadProof = computed(() => needProof(task.value?.type))

/** 接单方是否已经提交过交付内容（有说明或有凭证就展示） */
const hasDelivery = computed(() => {
  const t = task.value
  if (!t) return false
  const hasContent = !!t.submitContent && t.submitContent.trim().length > 0
  const hasImages = Array.isArray(t.submitImages) && t.submitImages.length > 0
  return hasContent || hasImages
})

function openSubmit() {
  submitForm.content = ''
  submitForm.images = []
  submitVisible.value = true
}

/** el-upload 的自定义上传：走 /files/upload，成功后把 URL 存进表单 */
async function handleProofUpload(options: {
  file: File
  onSuccess?: (res: unknown) => void
  onError?: (err: unknown) => void
}) {
  uploadingProof.value = true
  try {
    const res = await taskApi.uploadImage(options.file, 'delivery')
    submitForm.images.push(res.url)
    options.onSuccess?.(res)
  } catch (e) {
    options.onError?.(e)
    ElMessage.error('凭证上传失败，请重试')
  } finally {
    uploadingProof.value = false
  }
}

function removeProof(url: string) {
  submitForm.images = submitForm.images.filter((item) => item !== url)
}

async function doSubmit() {
  if (!task.value) return
  if (!submitForm.content.trim()) {
    ElMessage.warning('请填写交付说明')
    return
  }
  // 后端对「代取/代买/租赁/家教/搬运」强制要求凭证，前端提前拦一道，
  // 否则用户点提交只会看到一句「该类型任务必须上传交付凭证」。
  if (mustUploadProof.value && !submitForm.images.length) {
    ElMessage.warning('该类型任务必须上传交付凭证（至少 1 张）')
    return
  }
  await taskApi.submitTask(task.value.id, {
    content: submitForm.content,
    images: submitForm.images,
  })
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
  // 已接单且已过截止时间里，发布人属于「对方超时、我主动收回」，提示要说清楚；
  // 取消记录记在发布人名下，所以不会算接单方的违约。
  const overtime = task.value.status === 'TAKEN' && expired.value
  const message = overtime
    ? '该任务已过截止时间但接单方还没提交。取消后报酬会全额退回，且本次取消不计入接单方的违约记录。确定取消吗？'
    : '取消后冻结的报酬会全额退回，确定取消吗？'
  try {
    await ElMessageBox.confirm(message, overtime ? '取消超时任务' : '取消任务', {
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

        <!-- 接单方的交付：说明 + 凭证（发布方据此决定确认还是拒绝） -->
        <section v-if="hasDelivery" class="block">
          <h3 class="block-title">
            交付信息
            <span class="block-sub">由接单方提交，确认前请核对</span>
          </h3>
          <p class="description">{{ task.submitContent || '接单方没有填写交付说明' }}</p>

          <div v-if="task.submitImages && task.submitImages.length" class="deliver-grid">
            <el-image
              v-for="url in task.submitImages"
              :key="url"
              :src="url"
              fit="cover"
              class="deliver-img"
              :preview-src-list="task.submitImages"
              preview-teleported
            />
          </div>
          <p v-else class="deliver-tip">该类型任务不强制凭证，接单方没有上传图片</p>
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

        <!-- 收到的报价：只有发布方在本任务还没被接走时看得到 -->
        <section v-if="isPublisher && pendingBargains.length" class="block">
          <h3 class="block-title">
            收到的报价
            <span class="block-sub">同意后任务报酬会按新金额调整，冻结额同步变化</span>
          </h3>
          <div v-for="b in pendingBargains" :key="b.id" class="bargain-row">
            <div class="bargain-main">
              <span class="bargain-user">{{ b.fromNickname || '匿名用户' }}</span>
              <span class="bargain-amount">{{ b.amount }} WB</span>
              <span class="bargain-time">{{ formatDateTime(b.createdAt) }}</span>
            </div>
            <div class="bargain-actions">
              <el-button size="small" type="primary" @click="respondBargain(b, true)">同意</el-button>
              <el-button size="small" @click="respondBargain(b, false)">拒绝</el-button>
            </div>
          </div>
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
            <template v-if="task.status === 'OPEN' && !expired">
              <el-button type="primary" @click="handleClaim">接取任务</el-button>
              <el-button @click="openBargain">我要议价</el-button>
            </template>
            <el-button v-else-if="task.status === 'OPEN' && expired" disabled>
              已过截止时间
            </el-button>
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
            <!-- 已接单但过了截止时间：发布人不能一直干等，给一个主动收回的出口 -->
            <el-button v-if="task.status === 'TAKEN' && expired" @click="doCancel">
              取消任务（对方超时）
            </el-button>
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
          v-if="isPublisher && task.status === 'TAKEN' && !expired"
          class="mt-16"
          type="info"
          :closable="false"
          show-icon
          title="接单方正在履约中，完成后会通知你确认。"
        />
        <el-alert
          v-if="isPublisher && task.status === 'TAKEN' && expired"
          class="mt-16"
          type="warning"
          :closable="false"
          show-icon
          title="已过截止时间，接单方仍未提交。你可以继续等，也可以点「取消任务」收回报酬；超时满宽限期后系统会自动关闭并退回。"
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

        <el-form-item :required="mustUploadProof">
          <template #label>
            交付凭证
            <span class="proof-hint">
              {{ mustUploadProof ? '（该类型任务必传，最多 3 张）' : '（可选，最多 3 张）' }}
            </span>
          </template>

          <div class="proof-grid">
            <div v-for="url in submitForm.images" :key="url" class="proof-item">
              <el-image :src="url" fit="cover" class="proof-img" :preview-src-list="submitForm.images" />
              <el-icon class="proof-del" @click="removeProof(url)"><Close /></el-icon>
            </div>

            <el-upload
              v-if="submitForm.images.length < 3"
              action="#"
              :show-file-list="false"
              :http-request="handleProofUpload"
              accept="image/*"
            >
              <div class="proof-add" :class="{ uploading: uploadingProof }">
                <el-icon v-if="!uploadingProof"><Plus /></el-icon>
                <span v-else>上传中…</span>
              </div>
            </el-upload>
          </div>
          <div class="proof-tip">
            凭证示例：快递柜取件截图、外卖小票、送达照片、聊天记录截图
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadingProof" @click="doSubmit">提交</el-button>
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

/* ---------------- 交付信息（发布方查看） ---------------- */
.block-sub {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.deliver-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.deliver-img {
  width: 96px;
  height: 96px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  cursor: zoom-in;
}

.deliver-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #c0c4cc;
}

/* ---------------- 收到的报价 ---------------- */
.bargain-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  margin-top: 8px;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.bargain-main {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.bargain-user {
  font-weight: 600;
  color: #303133;
}

.bargain-amount {
  font-weight: 600;
  color: #e6a23c;
}

.bargain-time {
  font-size: 12px;
  color: #c0c4cc;
}

.bargain-actions {
  display: flex;
  gap: 8px;
}

/* ---------------- 交付凭证上传 ---------------- */
.proof-hint {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.proof-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.proof-item,
.proof-add {
  position: relative;
  width: 84px;
  height: 84px;
  border-radius: 8px;
}

.proof-item {
  overflow: hidden;
  border: 1px solid #ebeef5;
}

.proof-img {
  width: 100%;
  height: 100%;
  display: block;
}

.proof-del {
  position: absolute;
  top: 2px;
  right: 2px;
  padding: 2px;
  font-size: 14px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 50%;
  cursor: pointer;
}

.proof-add {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  background: #fafafa;
  border: 1px dashed #dcdfe6;
  cursor: pointer;
}

.proof-add:hover {
  color: #409eff;
  border-color: #409eff;
}

.proof-add.uploading {
  cursor: not-allowed;
  color: #c0c4cc;
}

.proof-tip {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #c0c4cc;
}

@media (max-width: 720px) {
  .facts {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
