<script setup lang="ts">
/**
 * 找回密码页（UA-06，迭代三新增）
 *
 * 三步流程：
 * 1. 校验「登录名 + 注册手机号」是否匹配
 * 2. 设置新密码
 * 3. 完成，回登录页
 *
 * 后端实现说明（与页面必须一致）：
 * - 本项目没有接短信服务，所以没有真正的验证码，第一步只是校验两项信息是否属于同一账号；
 * - 后端 /auth/password/forgot 要求 username + phone 两个字段，只传手机号会被参数校验拦下；
 * - 重置时后端会再次校验，防止有人跳过第一步直接调接口。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { authApi } from '@/api'

const router = useRouter()

/** 当前步骤：0 校验账号 / 1 设置新密码 / 2 完成 */
const step = ref(0)
const loading = ref(false)

const accountFormRef = ref<FormInstance>()
const resetFormRef = ref<FormInstance>()

const accountForm = reactive({ username: '', phone: '' })
const resetForm = reactive({ newPassword: '', confirmPassword: '' })

const accountRules: FormRules = {
  username: [{ required: true, message: '请输入登录名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}

const resetRules: FormRules = {
  newPassword: [
    { required: true, message: '请设置新密码', trigger: 'blur' },
    {
      validator: (_r, value: string, cb) => {
        if (!value || value.length < 8 || value.length > 32) {
          cb(new Error('密码长度为 8-32 位'))
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

/** 第一步：校验登录名与手机号是否匹配 */
async function handleVerify() {
  if (!accountFormRef.value) return
  const valid = await accountFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.verifyResetAccount(accountForm.username.trim(), accountForm.phone)
    ElMessage.success('账号校验通过，请设置新密码')
    step.value = 1
  } finally {
    loading.value = false
  }
}

/** 第二步：提交重置 */
async function handleReset() {
  if (!resetFormRef.value) return
  const valid = await resetFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.resetPassword({
      username: accountForm.username.trim(),
      phone: accountForm.phone,
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
        <p class="form-sub">用注册时填的登录名和手机号重置密码</p>

        <el-steps :active="step" align-center finish-status="success" class="steps">
          <el-step title="校验账号" />
          <el-step title="设置新密码" />
          <el-step title="完成" />
        </el-steps>

        <!-- 第一步：登录名 + 手机号 -->
        <el-form
          v-if="step === 0"
          ref="accountFormRef"
          :model="accountForm"
          :rules="accountRules"
          label-position="top"
          size="large"
        >
          <el-form-item label="登录名" prop="username">
            <el-input v-model="accountForm.username" placeholder="注册时填的登录名" clearable />
          </el-form-item>

          <el-form-item label="注册手机号" prop="phone">
            <el-input
              v-model="accountForm.phone"
              placeholder="注册时填写的手机号"
              maxlength="11"
              clearable
              @keyup.enter="handleVerify"
            />
          </el-form-item>

          <el-button type="primary" class="submit" :loading="loading" @click="handleVerify">
            下一步
          </el-button>
        </el-form>

        <!-- 第二步：新密码 -->
        <el-form
          v-else-if="step === 1"
          ref="resetFormRef"
          :model="resetForm"
          :rules="resetRules"
          label-position="top"
          size="large"
        >
          <el-alert
            :title="`正在重置账号 ${accountForm.username}，请设置新密码`"
            type="info"
            :closable="false"
            show-icon
            class="mb-16"
          />

          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="resetForm.newPassword"
              type="password"
              placeholder="8-32 位，含字母和数字"
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
