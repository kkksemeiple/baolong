<script setup lang="ts">
/**
 * 注册页（UA-01）
 *
 * 对应需求：
 * - 填写账号、密码、确认密码、手机号提交注册
 * - 前端校验：账号非空且格式合法、密码长度与强度合规、两次密码一致、手机号格式合法
 * - 账号已存在时提示重复（由后端返回）
 * - 注册成功后自动登录，并跳转到任务大厅
 * - 后端会在同一事务内创建屋币账户并赠送 100 WB
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const agreed = ref(false)

const form = reactive({
  username: '',
  nickname: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

/** 密码强度：必须同时包含字母和数字 */
function passwordStrength(value: string): number {
  let score = 0
  if (value.length >= 8) score++
  if (/[a-zA-Z]/.test(value)) score++
  if (/\d/.test(value)) score++
  if (/[^a-zA-Z0-9]/.test(value)) score++
  return Math.min(score, 4)
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9]{3,20}$/,
      message: '账号为 3-20 位字母或数字',
      trigger: 'blur',
    },
  ],
  nickname: [{ max: 20, message: '昵称不超过 20 个字符', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value || value.length < 8 || value.length > 20) {
          callback(new Error('密码长度为 8-20 位'))
          return
        }
        if (!/[a-zA-Z]/.test(value) || !/\d/.test(value)) {
          callback(new Error('密码需同时包含字母和数字'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handleRegister() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (!agreed.value) {
    ElMessage.warning('请先阅读并同意用户协议')
    return
  }

  loading.value = true
  try {
    await userStore.register({
      username: form.username,
      password: form.password,
      confirmPassword: form.confirmPassword,
      phone: form.phone,
      nickname: form.nickname || undefined,
    })
    await appStore.refreshUnread()

    ElMessage.success('注册成功，已赠送 100 屋币')
    router.replace('/tasks')
  } catch {
    // 失败提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <aside class="brand-panel">
      <div class="brand-mark">暴</div>
      <h1 class="brand-title">加入暴龙万事屋</h1>
      <p class="brand-desc">注册即送 100 屋币，够接十几单快递</p>
      <ul class="brand-points">
        <li>用学号认证后即可发布与接取任务</li>
        <li>信用分从 100 起，完成得好会越来越高</li>
        <li>屋币只在平台内流通，安全可控</li>
      </ul>
    </aside>

    <main class="form-panel">
      <div class="form-box">
        <h2 class="form-title">创建账号</h2>
        <p class="form-sub">已有账号？<router-link to="/login" class="link">直接登录</router-link></p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent
        >
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" placeholder="3-20 位字母或数字" clearable />
          </el-form-item>

          <el-form-item label="昵称（可选）" prop="nickname">
            <el-input v-model="form.nickname" placeholder="不填则默认使用账号" clearable />
          </el-form-item>

          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" placeholder="用于接收登录与安全提醒" clearable />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="8-20 位，含字母和数字" show-password />
            <div v-if="form.password" class="strength">
              <span
                v-for="i in 4"
                :key="i"
                class="bar"
                :class="{ on: passwordStrength(form.password) >= i }"
              />
              <span class="strength-text">
                {{ ['很弱', '较弱', '一般', '较强'][passwordStrength(form.password) - 1] ?? '很弱' }}
              </span>
            </div>
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              show-password
              @keyup.enter="handleRegister"
            />
          </el-form-item>

          <el-checkbox v-model="agreed" class="agree">
            我已阅读并同意《用户协议》与《隐私政策》
          </el-checkbox>

          <el-button
            type="primary"
            class="submit"
            :loading="loading"
            @click="handleRegister"
          >
            注 册
          </el-button>
        </el-form>
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  min-height: 100vh;
}

.brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  width: 44%;
  padding: 60px 8%;
  color: #fff;
  background: linear-gradient(135deg, #1f4fff 0%, #2f7ce0 45%, #1a3fa8 100%);
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 24px;
  font-size: 26px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 14px;
}

.brand-title {
  margin: 0 0 8px;
  font-size: 30px;
}

.brand-desc {
  margin: 0 0 36px;
  font-size: 16px;
  opacity: 0.85;
}

.brand-points {
  padding: 0;
  margin: 0;
  font-size: 14px;
  line-height: 2.2;
  list-style: none;
  opacity: 0.9;
}

.brand-points li::before {
  margin-right: 8px;
  content: '·';
}

.form-panel {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background: #fff;
}

.form-box {
  width: 100%;
  max-width: 380px;
}

.form-title {
  margin: 0 0 6px;
  font-size: 26px;
  color: #303133;
}

.form-sub {
  margin: 0 0 24px;
  font-size: 13px;
  color: #909399;
}

.link {
  color: #409eff;
}

.strength {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  margin-top: 6px;
}

.bar {
  flex: 1;
  height: 4px;
  background: #ebeef5;
  border-radius: 2px;
}

.bar.on {
  background: #67c23a;
}

.strength-text {
  font-size: 12px;
  color: #909399;
}

.agree {
  margin-bottom: 18px;
  font-size: 13px;
}

.submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
}

@media (max-width: 860px) {
  .brand-panel {
    display: none;
  }
}
</style>
