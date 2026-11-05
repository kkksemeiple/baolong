<script setup lang="ts">
/**
 * 登录页（UA-02）
 *
 * 对应需求：
 * - 输入账号密码登录
 * - 校验失败给出提示（不区分"账号不存在"和"密码错误"，防账号枚举）
 * - 成功后签发凭证、跳转到原本要去的页面
 * - 连续 5 次失败锁定 10 分钟（由后端判定，这里负责把错误信息展示出来）
 * - 支持退出登录（在其他页面）
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为 8-20 位', trigger: 'blur' },
  ],
}

/** 是否处于 Mock 模式：是的话提示可用的演示账号 */
const isMock = import.meta.env.VITE_USE_MOCK === 'true'

async function handleLogin() {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    await appStore.refreshUnread()

    ElMessage.success('登录成功')

    // 登录后回到原本要访问的页面；没有则进任务大厅
    const redirect = (route.query.redirect as string) || '/tasks'
    router.replace(redirect)
  } catch {
    // 错误提示已由 axios 拦截器统一弹出，这里无需重复提示
  } finally {
    loading.value = false
  }
}

/** 一键填充演示账号，方便答辩演示 */
function fillDemo() {
  form.username = 'linxiaoman'
  form.password = 'abc12345'
}
</script>

<template>
  <div class="auth-page">
    <!-- 左侧品牌区 -->
    <aside class="brand-panel">
      <div class="brand-mark">暴</div>
      <h1 class="brand-title">暴龙万事屋</h1>
      <p class="brand-desc">校园委托互助平台</p>
      <ul class="brand-points">
        <li>发一条委托，找同校的人帮忙</li>
        <li>任务报酬用屋币结算，发布即冻结，完成才划转</li>
        <li>学号实名认证，交易更放心</li>
      </ul>
    </aside>

    <!-- 右侧表单区 -->
    <main class="form-panel">
      <div class="form-box">
        <h2 class="form-title">登录</h2>
        <p class="form-sub">还没有账号？<router-link to="/register" class="link">立即注册</router-link></p>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          size="large"
          @submit.prevent
        >
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入账号"
              :prefix-icon="'User'"
              clearable
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="'Lock'"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div class="form-extra">
            <router-link to="/forgot-password" class="link">忘记密码？</router-link>
          </div>

          <el-button
            type="primary"
            class="submit"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form>

        <el-alert
          v-if="isMock"
          class="mock-tip"
          type="info"
          :closable="false"
          show-icon
          title="当前为演示模式（未连接后端）"
        >
          <template #default>
            演示账号：<b>linxiaoman</b> / <b>abc12345</b>
            <el-button link type="primary" @click="fillDemo">一键填入</el-button>
          </template>
        </el-alert>

        <p class="agreement">
          登录即表示同意《用户协议》与《隐私政策》。平台内屋币不可充值、提现或转让。
        </p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  min-height: 100vh;
}

/* 左侧品牌区 */
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
  font-size: 34px;
  letter-spacing: 2px;
}

.brand-desc {
  margin: 0 0 36px;
  font-size: 16px;
  opacity: 0.85;
}

.brand-points {
  padding: 0;
  margin: 0;
  list-style: none;
  font-size: 14px;
  line-height: 2.2;
  opacity: 0.9;
}

.brand-points li::before {
  margin-right: 8px;
  content: '·';
}

/* 右侧表单区 */
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
  margin: 0 0 28px;
  font-size: 13px;
  color: #909399;
}

.link {
  color: #409eff;
}

.form-extra {
  display: flex;
  justify-content: flex-end;
  margin: -6px 0 18px;
  font-size: 13px;
}

.submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
}

.mock-tip {
  margin-top: 20px;
}

.agreement {
  margin-top: 24px;
  font-size: 12px;
  line-height: 1.7;
  color: #c0c4cc;
  text-align: center;
}

@media (max-width: 860px) {
  .brand-panel {
    display: none;
  }
}
</style>
