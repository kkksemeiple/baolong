<script setup lang="ts">
/**
 * 发布任务（TK-02）
 *
 * 关键业务规则：
 * - 报酬为必填，发布时后端会冻结等额屋币；余额不足会返回错误
 * - 截止时间必须晚于当前时间，且不超过发布后 30 天
 * - 提交前后端会做违禁词检测（代考、代写等会被拒绝）
 */
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { taskApi, userApi } from '@/api'
import type { TaskType, WalletAccount } from '@/api/types'
import { formatCoin } from '@/utils/format'
import { TASK_TYPE_META } from '@/utils/constants'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)

/** 钱包余额，用于提示"余额不足" */
const wallet = ref<WalletAccount | null>(null)

const form = reactive({
  title: '',
  description: '',
  type: 'PICKUP' as TaskType,
  reward: 10,
  deadline: new Date(Date.now() + 24 * 3600_000).toISOString(),
  location: '',
  peopleNeeded: 1,
})

const rules: FormRules = {
  title: [
    { required: true, message: '请填写任务标题', trigger: 'blur' },
    { min: 5, max: 50, message: '标题为 5-50 个字', trigger: 'blur' },
  ],
  description: [
    { required: true, message: '请填写任务描述', trigger: 'blur' },
    { min: 10, max: 2000, message: '描述为 10-2000 个字', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  reward: [{ required: true, message: '请填写报酬', trigger: 'blur' }],
  location: [{ required: true, message: '请填写交付地点', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择截止时间', trigger: 'change' }],
}

const typeOptions = Object.entries(TASK_TYPE_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

/** 余额是否够支付本次报酬 */
const enoughBalance = computed(() => !wallet.value || wallet.value.available >= form.reward)

/** 可选截止时间范围：从明天到 30 天后 */
const deadlineShortcuts = [
  {
    text: '今天结束前',
    value: () => {
      const d = new Date()
      d.setHours(23, 59, 59, 0)
      return d
    },
  },
  {
    text: '3 天后',
    value: () => new Date(Date.now() + 3 * 86400_000),
  },
  {
    text: '一周后',
    value: () => new Date(Date.now() + 7 * 86400_000),
  },
]

/** 禁用过去的日期 */
function disabledDate(date: Date) {
  const now = Date.now()
  const max = now + 30 * 86400_000
  return date.getTime() < now - 86400_000 || date.getTime() > max
}

async function loadWallet() {
  try {
    wallet.value = await userApi.fetchWallet()
  } catch {
    wallet.value = null
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!enoughBalance.value) {
    ElMessage.warning('屋币余额不足，请先完成几单赚取，或等待管理员发放')
    return
  }

  loading.value = true
  try {
    const created = await taskApi.createTask({
      title: form.title,
      description: form.description,
      type: form.type,
      reward: form.reward,
      deadline: new Date(form.deadline).toISOString(),
      location: form.location,
      peopleNeeded: form.peopleNeeded,
    })
    ElMessage.success(`发布成功，已冻结 ${form.reward} WB`)
    router.replace(`/tasks/${created.id}`)
  } catch {
    // 错误提示由拦截器统一处理（如余额不足、命中违禁词）
  } finally {
    loading.value = false
  }
}

loadWallet()
</script>

<template>
  <div class="page-container narrow">
    <el-page-header class="mb-16" @back="router.back()">
      <template #content>
        <span class="header-title">发布任务</span>
      </template>
    </el-page-header>

    <el-card shadow="never" class="form-card">
      <!-- 余额提示 -->
      <el-alert
        v-if="wallet"
        :type="enoughBalance ? 'info' : 'error'"
        :closable="false"
        show-icon
        class="mb-16"
      >
        <template #title>
          当前可用屋币 <b>{{ formatCoin(wallet.available) }}</b>
          <span v-if="!enoughBalance">，不足以支付本次报酬 {{ formatCoin(form.reward) }}</span>
          <span v-else>，发布后将冻结 {{ formatCoin(form.reward) }}</span>
        </template>
      </el-alert>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="任务标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="一句话说清要做什么，例如「帮取南门快递送到 7 号楼」"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="任务类型" prop="type">
              <el-select v-model="form.type" class="w-full">
                <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="人数需求">
              <el-input-number v-model="form.peopleNeeded" :min="1" :max="10" class="w-full" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item label="报酬（屋币）" prop="reward">
              <el-input-number v-model="form.reward" :min="1" :max="500" class="w-full" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="截止时间" prop="deadline">
              <el-date-picker
                v-model="form.deadline"
                type="datetime"
                placeholder="选择截止时间"
                :shortcuts="deadlineShortcuts"
                :disabled-date="disabledDate"
                class="w-full"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="交付地点" prop="location">
          <el-input v-model="form.location" placeholder="例如：南门菜鸟驿站 → 7 号楼 502" />
        </el-form-item>

        <el-form-item label="任务描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="补充细节：东西多大、有没有取件码、需要注意什么……描述越清楚，越容易被接单"
          />
        </el-form-item>

        <el-alert type="warning" :closable="false" show-icon class="mb-16">
          <template #title>
            禁止发布代考、代课、代写论文等学术作弊类委托，系统会自动拦截。
          </template>
        </el-alert>

        <div class="form-actions">
          <el-button @click="router.back()">取消</el-button>
          <el-button type="primary" :loading="loading" @click="handleSubmit">
            发布并冻结 {{ form.reward }} WB
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 760px;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
}

.form-card {
  border-radius: 10px;
}

.w-full {
  width: 100%;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 8px;
}
</style>
