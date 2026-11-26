<script setup lang="ts">
/**
 * 虚拟货币管理（AD-06）
 *
 * 四件事：
 * 1. 发放屋币（单人 / 批量）
 * 2. 资金冲正（仅超级管理员，不修改历史流水，而是新增反向流水）
 * 3. 全平台对账
 * 4. 系统参数配置
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { adminApi } from '@/api'
import type { AdminUserRow, RecentTransaction, SystemConfig } from '@/api/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/* ---------------- 发放 ---------------- */
/**
 * 发币对象直接「搜人、选人」，而不是让管理员填数字 ID。
 * 后台管理员记不住 ID，能按昵称/用户名搜更符合直觉；选中后拿到的仍是 userId。
 */
const issueForm = reactive({ userId: undefined as number | undefined, amount: 50, reason: '' })
const issueLoading = ref(false)
const userOptions = ref<AdminUserRow[]>([])
const userLoading = ref(false)

async function searchUsers(keyword: string) {
  userLoading.value = true
  try {
    const res = await adminApi.fetchAdminUsers({
      keyword: keyword?.trim() || undefined,
      page: 1,
      size: 20,
    })
    userOptions.value = res.list
  } finally {
    userLoading.value = false
  }
}

onMounted(() => {
  // 先拉一页默认用户，避免必须输入才能看到可选项
  searchUsers('')
  loadTxs()
})

async function handleIssue() {
  if (!issueForm.userId) {
    ElMessage.warning('请选择发币对象')
    return
  }
  if (!issueForm.reason.trim()) {
    ElMessage.warning('请填写发放原因')
    return
  }
  issueLoading.value = true
  try {
    await adminApi.issueCoin({
      userId: issueForm.userId,
      amount: issueForm.amount,
      reason: issueForm.reason,
    })
    ElMessage.success(`已发放 ${issueForm.amount} WB`)
    issueForm.reason = ''
    await loadTxs(true)
  } finally {
    issueLoading.value = false
  }
}

/* ---------------- 冲正 ---------------- */
const reverseForm = reactive({ transactionIdText: '', reason: '' })
const reverseLoading = ref(false)

/**
 * 资金流水查询（冲正选单）。
 * 只看最近 20 条不够用——要冲的很可能是几天前发错的那笔，
 * 所以支持按用户、类型、关键词（流水号或业务标题）过滤，并分页。
 */
const txFilters = reactive({ userId: undefined as number | undefined, transType: '', keyword: '' })
const txList = ref<RecentTransaction[]>([])
const txTotal = ref(0)
const txPage = ref(1)
const txPageSize = 8
const txLoading = ref(false)

const TX_TYPE_OPTIONS = [
  { value: 'GRANT', label: '发放' },
  { value: 'FREEZE', label: '冻结' },
  { value: 'UNFREEZE', label: '解冻' },
  { value: 'INCOME', label: '收入' },
  { value: 'PAY', label: '支付' },
  { value: 'PENALTY', label: '扣罚' },
  { value: 'REVERSAL', label: '冲正' },
]

async function loadTxs(reset = false) {
  if (reset) {
    txPage.value = 1
  }
  txLoading.value = true
  try {
    const res = await adminApi.fetchTransactions({
      userId: txFilters.userId,
      transType: txFilters.transType || undefined,
      keyword: txFilters.keyword.trim() || undefined,
      page: txPage.value,
      size: txPageSize,
    })
    txList.value = res.list
    txTotal.value = res.total
  } finally {
    txLoading.value = false
  }
}

function pickTx(tx: RecentTransaction) {
  reverseForm.transactionIdText = String(tx.id)
}

async function handleReverse() {
  if (!userStore.isSadmin) {
    ElMessage.warning('只有超级管理员可以执行资金冲正')
    return
  }
  const transactionId = Number(reverseForm.transactionIdText)
  if (!Number.isInteger(transactionId) || transactionId <= 0) {
    ElMessage.warning('请填写正确的流水 ID')
    return
  }
  try {
    await ElMessageBox.confirm(
      '冲正不会修改原流水，而是新增一条反向流水。确定继续吗？',
      '资金冲正',
      { type: 'warning' },
    )
  } catch {
    return
  }
  reverseLoading.value = true
  try {
    await adminApi.reverseTransaction({
      transactionId,
      reason: reverseForm.reason,
    })
    ElMessage.success('冲正成功')
    reverseForm.transactionIdText = ''
    reverseForm.reason = ''
    await loadTxs(true)
  } finally {
    reverseLoading.value = false
  }
}

/* ---------------- 对账 ---------------- */
const auditResult = ref<Array<{ userId: number; username: string; balance: number; calculated: number }> | null>(null)
const auditLoading = ref(false)

async function handleAudit() {
  auditLoading.value = true
  try {
    auditResult.value = await adminApi.auditCoins()
    if (!auditResult.value.length) {
      ElMessage.success('对账通过，全平台账目平衡')
    } else {
      ElMessage.warning(`发现 ${auditResult.value.length} 个账目异常的用户`)
    }
  } finally {
    auditLoading.value = false
  }
}

/* ---------------- 系统配置 ---------------- */
const config = ref<SystemConfig | null>(null)
const configLoading = ref(false)

/** 后端 system_config.value 是 VARCHAR，返回的是 "100" 这种字符串；
 *  el-input-number 只接受 Number，所以读出来先转一次类型。 */
const CONFIG_NUMBER_KEYS = [
  'initGrant',
  'maxHolding',
  'grantLimit',
  'autoConfirmHours',
  'claimTimeoutHours',
  'penaltyCancel',
  'penaltyNoShow',
] as const

async function loadConfig() {
  const raw = (await adminApi.fetchSystemConfig()) as unknown as Record<string, unknown>
  const next = {} as SystemConfig
  for (const key of CONFIG_NUMBER_KEYS) {
    const num = Number(raw[key])
    next[key] = Number.isFinite(num) ? num : 0
  }
  config.value = next
}

async function saveConfig() {
  if (!config.value) return
  configLoading.value = true
  try {
    await adminApi.updateSystemConfig(config.value)
    ElMessage.success('配置已保存，立即生效')
  } finally {
    configLoading.value = false
  }
}

onMounted(() => {
  loadConfig()
})
</script>

<template>
  <div class="grid">
    <!-- 发放 -->
    <el-card shadow="never" class="card">
      <template #header><span class="card-title">发放屋币</span></template>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="发币对象">
          <el-select
            v-model="issueForm.userId"
            filterable
            remote
            clearable
            reserve-keyword
            :remote-method="searchUsers"
            :loading="userLoading"
            placeholder="输入昵称或用户名搜索"
            class="w-full"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="`${u.nickname || u.username}（${u.username}）#${u.id}`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="发放数量（WB）">
          <el-input-number v-model="issueForm.amount" :min="1" :max="500" class="w-full" />
        </el-form-item>

        <el-form-item label="发放原因" required>
          <el-input v-model="issueForm.reason" type="textarea" :rows="2" maxlength="100" show-word-limit />
        </el-form-item>

        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="单次发放上限、单人持有上限由下方配置决定"
          class="mb-16"
        />

        <el-button type="primary" :loading="issueLoading" @click="handleIssue">确认发放</el-button>
      </el-form>
    </el-card>

    <!-- 冲正 -->
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span class="card-title">资金冲正</span>
          <el-tag v-if="!userStore.isSadmin" size="small" type="info">需要超级管理员</el-tag>
        </div>
      </template>

      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="冲正不会修改或删除历史流水，而是新增一条反向流水；同一条流水只能冲正一次"
        class="mb-16"
      />

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="原流水 ID">
          <el-input
            v-model="reverseForm.transactionIdText"
            inputmode="numeric"
            placeholder="输入要冲正的流水 ID，或从下方列表点选"
            class="w-full"
            :disabled="!userStore.isSadmin"
          />
        </el-form-item>

        <div class="recent-tx">
          <div class="recent-tx-head">
            <span>查询流水（点一行即可选中）</span>
            <el-button link size="small" :loading="txLoading" @click="loadTxs()">刷新</el-button>
          </div>

          <div class="tx-filter">
            <el-select
              v-model="txFilters.userId"
              filterable
              remote
              clearable
              reserve-keyword
              :remote-method="searchUsers"
              :loading="userLoading"
              placeholder="全部用户"
              size="small"
            >
              <el-option
                v-for="u in userOptions"
                :key="u.id"
                :label="`${u.nickname || u.username}（${u.username}）#${u.id}`"
                :value="u.id"
              />
            </el-select>
            <el-select v-model="txFilters.transType" clearable placeholder="全部类型" size="small">
              <el-option v-for="t in TX_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-input
              v-model="txFilters.keyword"
              clearable
              size="small"
              placeholder="流水号或业务标题"
              @keyup.enter="loadTxs(true)"
            />
            <el-button size="small" type="primary" :loading="txLoading" @click="loadTxs(true)">
              查询
            </el-button>
          </div>

          <div v-if="!txList.length" class="recent-tx-empty">没有符合条件的流水</div>
          <button
            v-for="tx in txList"
            :key="tx.id"
            type="button"
            class="recent-tx-row"
            :class="{ active: reverseForm.transactionIdText === String(tx.id) }"
            @click="pickTx(tx)"
          >
            <span class="tx-id">#{{ tx.id }}</span>
            <span class="tx-user">{{ tx.nickname }}</span>
            <span class="tx-type">{{ tx.transLabel }}</span>
            <span class="tx-amount" :class="tx.amount >= 0 ? 'plus' : 'minus'">
              {{ tx.amount >= 0 ? '+' : '' }}{{ tx.amount }}
            </span>
            <span class="tx-title">{{ tx.bizTitle }}</span>
          </button>

          <el-pagination
            v-if="txTotal > txPageSize"
            v-model:current-page="txPage"
            class="tx-pager"
            layout="prev, pager, next"
            :page-size="txPageSize"
            :total="txTotal"
            small
            @current-change="loadTxs()"
          />
        </div>

        <el-form-item label="冲正原因" required>
          <el-input
            v-model="reverseForm.reason"
            type="textarea"
            :rows="2"
            maxlength="100"
            show-word-limit
            :disabled="!userStore.isSadmin"
          />
        </el-form-item>

        <el-button
          type="danger"
          plain
          :loading="reverseLoading"
          :disabled="!userStore.isSadmin"
          @click="handleReverse"
        >
          执行冲正
        </el-button>
      </el-form>
    </el-card>

    <!-- 对账 -->
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span class="card-title">全平台对账</span>
          <el-button size="small" type="primary" plain :loading="auditLoading" @click="handleAudit">
            开始对账
          </el-button>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="校验每个用户的「可用 − 欠款」是否等于其全部流水之和"
        description="冻结中的屋币不参与对账：冻结那一刻可用已经减了、流水也记过了，冻结额只是「这笔钱被哪条任务占着」的镜像。"
        class="mb-16"
      />

      <template v-if="auditResult !== null">
        <el-result
          v-if="!auditResult.length"
          icon="success"
          title="对账通过"
          sub-title="全平台账目平衡，没有发现异常"
        />
        <template v-else>
          <el-alert type="error" :closable="false" show-icon :title="`发现 ${auditResult.length} 个异常用户`" class="mb-16" />
          <el-table :data="auditResult" size="small" border>
            <el-table-column prop="userId" label="用户 ID" width="100" />
            <el-table-column prop="username" label="账号" />
            <el-table-column prop="balance" label="可用−欠款" width="120" align="right" />
            <el-table-column prop="calculated" label="流水累计" width="110" align="right" />
            <el-table-column label="差额" width="100" align="right">
              <template #default="{ row }">
                <span class="danger">{{ row.balance - row.calculated }}</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </el-card>

    <!-- 系统配置 -->
    <el-card v-if="config" shadow="never" class="card span-2">
      <template #header><span class="card-title">虚拟货币参数配置</span></template>

      <el-form label-position="top">
        <div class="config-grid">
          <el-form-item label="新用户注册赠送（WB）">
            <el-input-number v-model="config.initGrant" :min="0" :max="1000" class="w-full" />
          </el-form-item>

          <el-form-item label="单人持有上限（WB）">
            <el-input-number v-model="config.maxHolding" :min="1000" class="w-full" />
          </el-form-item>

          <el-form-item label="单次发放上限（WB）">
            <el-input-number v-model="config.grantLimit" :min="1" :max="5000" class="w-full" />
          </el-form-item>

          <el-form-item label="超时自动确认（小时）">
            <el-input-number v-model="config.autoConfirmHours" :min="1" :max="168" class="w-full" />
          </el-form-item>

          <el-form-item label="接单超时自动关闭（小时）">
            <el-input-number v-model="config.claimTimeoutHours" :min="1" :max="168" class="w-full" />
            <div class="config-tip">
              任务过了截止时间、接单方仍未提交，再等多久就自动关闭并退回报酬
            </div>
          </el-form-item>

          <el-form-item label="违约金-额度外取消（WB）">
            <el-input-number v-model="config.penaltyCancel" :min="0" :max="100" class="w-full" />
          </el-form-item>

          <el-form-item label="违约金-爽约（WB）">
            <el-input-number v-model="config.penaltyNoShow" :min="0" :max="100" class="w-full" />
          </el-form-item>
        </div>

        <el-button type="primary" :loading="configLoading" @click="saveConfig">保存配置</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 16px;
}

.card {
  border-radius: 10px;
}

.span-2 {
  grid-column: 1 / -1;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0 20px;
}

.w-full {
  width: 100%;
}

.config-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}

.danger {
  color: #f56c6c;
  font-weight: 600;
}

.mb-16 {
  margin-bottom: 16px;
}

/* 最近流水选单：一行一条，点一下把流水 ID 填进输入框 */
.recent-tx {
  margin-bottom: 8px;
}

.recent-tx-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.recent-tx-empty {
  font-size: 13px;
  color: #c0c4cc;
  padding: 8px 0;
}

/* 筛选行：用户 / 类型 / 关键词 */
.tx-filter {
  display: grid;
  grid-template-columns: 1.2fr 0.9fr 1.2fr auto;
  gap: 6px;
  margin-bottom: 8px;
}

.tx-pager {
  margin-top: 8px;
  justify-content: center;
}

.recent-tx-row {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  font-size: 13px;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  color: #606266;
}

.recent-tx-row:hover {
  background: #f5f7fa;
}

.recent-tx-row.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.recent-tx-row .tx-id {
  color: #909399;
  min-width: 46px;
}

.recent-tx-row .tx-user {
  min-width: 56px;
  font-weight: 500;
}

.recent-tx-row .tx-type {
  min-width: 40px;
  color: #909399;
}

.recent-tx-row .tx-amount {
  min-width: 48px;
  font-weight: 600;
}

.recent-tx-row .tx-amount.plus {
  color: #67c23a;
}

.recent-tx-row .tx-amount.minus {
  color: #f56c6c;
}

.recent-tx-row .tx-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #a8abb2;
}
</style>
