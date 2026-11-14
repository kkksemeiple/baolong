<script setup lang="ts">
/**
 * 找回密码页（UA-06，迭代三新增）
 *
 * 三步流程：
 * 1. 校验手机号 → 发送一次性验证码（15 分钟有效）
 * 2. 验证码 + 新密码 → 重置
 * 3. 重置成功 → 回到登录页
 *
 * 安全要点：
 * - 无论手机号是否注册，第 1 步都返回相同提示（防账号枚举）
 * - 验证码一次性使用，重置成功后该用户所有已登录会话失效（后端处理）
 */
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { authApi } from '@/api'

const router = useRouter()

/** 当前步骤：0 输入手机号 / 1 设置新密码 / 2 完成 */
const step = ref(0)
const loading = ref(false)
/** 发送验证码后的倒计时 */
const countdown = ref(0)
let timer: number | undefined

const phoneFormRef = ref<FormInstance>()
const resetFormRef = ref<FormInstance>()

const phoneForm = reactive({ phone: '' })
const resetForm = reactive({ code: '', newPassword: '', confirmPassword: '' })

const isMock = import.meta.env.VITE_USE_MOCK === 'true'

const phoneRules: FormRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}

const resetRules: FormRules = {
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位数字', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请设置新密码', trigger: 'blur' },
    {
      validator: (_r, value: string, cb) => {
        if (!value || value.length < 8 || value.length > 20) {
          cb(new Error('密码长度为 8-20 位'))
          return
        }
        if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) {
          cb(new Error('密码需同时包含字母和数字'))
          return
        }
        cb()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, value: string, cb) => {
        cb(value === resetForm.newPassword ? undefined : new Error('两次输入的密码不一致'))
      },
      trigger: 'blur',
    },
  ],
}

const sendButtonText = computed(() =>
  countdown.value > 0 ? `${countdown.value} 秒后重发` : '发送验证码',
)

/** 发送验证码 */
async function handleSendCode() {
  if (!phoneFormRef.value) return
  const valid = await phoneFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.sendResetCode(phoneForm.phone)
    ElMessage.success('若该手机号已注册，验证码已发送')

    // 进入倒计时，防止频繁请求
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0 && timer) {
        window.clearInterval(timer)
        timer = undefined
      }
    }, 1000)

    step.value = 1
  } finally {
    loading.value = false
  }
}

/** 提交重置 */
async function handleReset() {
  if (!resetFormRef.value) return
  const valid = await resetFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.resetPassword({
      phone: phoneForm.phone,
      code: resetForm.code,
      newPassword: resetForm.newPassword,
    })
    step.value = 2
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <main class="form-panel">
      <div class="form-box">
        <h2 class="form-title">找回密码</h2>
        <p class="form-sub">通过注册手机号重置登录密码</p>

        <el-steps :active="step" align-center finish-status="success" class="steps">
          <el-step title="验证手机号" />
          <el-step title="设置新密码" />
          <el-step title="完成" />
        </el-steps>

        <!-- 第一步：手机号 -->
        <el-form
          v-if="step === 0"
          ref="phoneFormRef"
          :model="phoneForm"
          :rules="phoneRules"
          label-position="top"
          size="large"
        >
          <el-form-item label="注册手机号" prop="phone">
            <el-input v-model="phoneForm.phone" placeholder="请输入注册时填写的手机号" clearable />
          </el-form-item>
          <el-button type="primary" class="submit" :loading="loading" @click="handleSendCode">
            下一步
          </el-button>
        </el-form>

        <!-- 第二步：验证码 + 新密码 -->
        <el-form
          v-else-if="step === 1"
          ref="resetFormRef"
          :model="resetForm"
          :rules="resetRules"
          label-position="top"
          size="large"
        >
          <el-form-item label="验证码" prop="code">
            <div class="code-row">
              <el-input v-model="resetForm.code" placeholder="6 位验证码" maxlength="6" />
              <el-button :disabled="countdown > 0 || loading" @click="handleSendCode">
                {{ sendButtonText }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="resetForm.newPassword"
              type="password"
              placeholder="8-20 位，含字母和数字"
              show-password
            />
          </el-form-item>

          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="resetForm.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              show-password
              @keyup.enter="handleReset"
            />
          </el-form-item>

          <el-alert
            v-if="isMock"
            type="info"
            :closable="false"
            show-icon
            title="演示模式：验证码固定为 123456"
            class="mb-16"
          />

          <el-button type="primary" class="submit" :loading="loading" @click="handleReset">
            重置密码
          </el-button>
        </el-form>

        <!-- 第三步：完成 -->
        <el-result
          v-else
          icon="success"
          title="密码已重置"
          sub-title="为保证账号安全，其他设备上的登录状态已失效，请使用新密码重新登录。"
        >
          <template #extra>
            <el-button type="primary" @click="router.replace('/login')">去登录</el-button>
          </template>
        </el-result>

        <p v-if="step !== 2" class="back">
          <router-link to="/login" class="link">返回登录</router-link>
        </p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.form-panel {
  width: 100%;
  max-width: 460px;
  padding: 40px;
  margin: 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
}

.form-title {
  margin: 0 0 6px;
  font-size: 24px;
  color: #303133;
}

.form-sub {
  margin: 0 0 24px;
  font-size: 13px;
  color: #909399;
}

.steps {
  margin-bottom: 28px;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
}

.back {
  margin-top: 20px;
  font-size: 13px;
  color: #909399;
  text-align: center;
}

.link {
  color: #409eff;
}

.mb-16 {
  margin-bottom: 16px;
}
</style>
