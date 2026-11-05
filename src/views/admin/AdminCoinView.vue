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
import type { SystemConfig } from '@/api/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/* ---------------- 发放 ---------------- */
const issueForm = reactive({ userId: undefined as number | undefined, amount: 50, reason: '' })
const issueLoading = ref(false)

async function handleIssue() {
  if (!issueForm.userId) {
    ElMessage.warning('请填写用户 ID')
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
  } finally {
    issueLoading.value = false
  }
}

/* ---------------- 冲正 ---------------- */
const reverseForm = reactive({ transactionId: undefined as number | undefined, reason: '' })
const reverseLoading = ref(false)

async function handleReverse() {
  if (!userStore.isSadmin) {
    ElMessage.warning('只有超级管理员可以执行资金冲正')
    return
  }
  if (!reverseForm.transactionId) {
    ElMessage.warning('请填写流水 ID')
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
      transactionId: reverseForm.transactionId,
      reason: reverseForm.reason,
    })
    ElMessage.success('冲正成功')
    reverseForm.transactionId = undefined
    reverseForm.reason = ''
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

async function loadConfig() {
  config.value = await adminApi.fetchSystemConfig()
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

      <el-form label-position="top">
        <el-form-item label="用户 ID">
          <el-input-number v-model="issueForm.userId" :min="1" :controls="false" class="w-full" placeholder="输入用户 ID" />
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

      <el-form label-position="top">
        <el-form-item label="原流水 ID">
          <el-input-number
            v-model="reverseForm.transactionId"
            :min="1"
            :controls="false"
            class="w-full"
            placeholder="输入要冲正的流水 ID"
            :disabled="!userStore.isSadmin"
          />
        </el-form-item>

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
        title="校验每个用户的「可用 + 冻结 − 欠款」是否等于其全部流水之和"
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
            <el-table-column prop="balance" label="账上余额" width="110" align="right" />
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

.danger {
  color: #f56c6c;
  font-weight: 600;
}

.mb-16 {
  margin-bottom: 16px;
}
</style>
