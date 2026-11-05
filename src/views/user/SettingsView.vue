<script setup lang="ts">
/**
 * 账号设置（UA-03 退出 / UA-06 改密 / UA-07 实名认证 / UA-08 登录日志）
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'

import { authApi } from '@/api'
import { uploadImage } from '@/api/task'
import type { AuthStatus } from '@/api/types'
import type { LoginLogItem } from '@/api/auth'
import StatusTag from '@/components/StatusTag.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/* ---------------- 修改密码 ---------------- */
const pwdRef = ref<FormInstance>()
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_r, v: string, cb) => {
        if (!v || v.length < 8 || v.length > 20) return cb(new Error('密码长度为 8-20 位'))
        if (!/[a-zA-Z]/.test(v) || !/\d/.test(v)) return cb(new Error('需同时包含字母和数字'))
        cb()
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, v: string, cb) =>
        cb(v === pwdForm.newPassword ? undefined : new Error('两次输入的密码不一致')),
      trigger: 'blur',
    },
  ],
}

async function handleChangePwd() {
  if (!pwdRef.value) return
  const valid = await pwdRef.value.validate().catch(() => false)
  if (!valid) return

  pwdLoading.value = true
  try {
    await authApi.changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success('密码已修改，请使用新密码重新登录')
    // 改密后所有凭证失效，需要重新登录
    await userStore.logout()
    router.replace('/login')
  } finally {
    pwdLoading.value = false
  }
}

/* ---------------- 实名认证 ---------------- */
const authStatus = ref<AuthStatus>('NONE')
const rejectReason = ref<string | null>(null)
const certForm = reactive({ studentNo: '', certImage: '' })
const certLoading = ref(false)
const uploading = ref(false)

async function loadAuth() {
  try {
    const res = await authApi.fetchAuthStatus()
    authStatus.value = res.authStatus
    rejectReason.value = res.rejectReason
    if (res.studentNo) certForm.studentNo = res.studentNo
  } catch {
    authStatus.value = 'NONE'
  }
}

/** 上传学生证照片 */
async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const res = await uploadImage(options.file, 'cert')
    certForm.certImage = res.url
    ElMessage.success('照片已上传')
  } finally {
    uploading.value = false
  }
}

async function submitCert() {
  if (!certForm.studentNo.trim()) {
    ElMessage.warning('请填写学号')
    return
  }
  if (!certForm.certImage) {
    ElMessage.warning('请上传学生证照片')
    return
  }
  certLoading.value = true
  try {
    await authApi.submitStudentCert({
      studentNo: certForm.studentNo,
      certImage: certForm.certImage,
    })
    ElMessage.success('已提交，等待管理员审核')
    await loadAuth()
    await userStore.fetchMe()
  } finally {
    certLoading.value = false
  }
}

/* ---------------- 登录记录 ---------------- */
const logs = ref<LoginLogItem[]>([])
const logsLoading = ref(false)

async function loadLogs() {
  logsLoading.value = true
  try {
    logs.value = await authApi.fetchLoginLogs()
  } catch {
    logs.value = []
  } finally {
    logsLoading.value = false
  }
}

/* ---------------- 退出登录 ---------------- */
async function handleLogout() {
  await userStore.logout()
  appStore.reset()
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(() => {
  loadAuth()
  loadLogs()
})
</script>

<template>
  <div class="page-container narrow">
    <h1 class="page-title">账号设置</h1>

    <!-- 修改密码 -->
    <el-card shadow="never" class="card">
      <template #header><span class="card-title">修改密码</span></template>

      <el-form ref="pwdRef" :model="pwdForm" :rules="pwdRules" label-position="top" class="form">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="8-20 位，含字母和数字" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>

        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="修改密码后，所有设备上的登录状态都会失效"
          class="mb-16"
        />

        <el-button type="primary" :loading="pwdLoading" @click="handleChangePwd">
          保存新密码
        </el-button>
      </el-form>
    </el-card>

    <!-- 实名认证 -->
    <el-card shadow="never" class="card mt-16">
      <template #header>
        <div class="card-head">
          <span class="card-title">学号实名认证</span>
          <StatusTag :value="authStatus" kind="auth" size="small" />
        </div>
      </template>

      <el-alert
        v-if="authStatus === 'APPROVED'"
        type="success"
        :closable="false"
        show-icon
        title="已完成实名认证，可以正常发布与接取任务"
      />

      <template v-else>
        <el-alert
          v-if="authStatus === 'PENDING'"
          type="warning"
          :closable="false"
          show-icon
          title="认证资料已提交，正在等待管理员审核"
          class="mb-16"
        />

        <el-alert
          v-if="authStatus === 'REJECTED'"
          type="error"
          :closable="false"
          show-icon
          :title="`认证被驳回：${rejectReason || '资料不符合要求，请重新提交'}`"
          class="mb-16"
        />

        <el-alert
          v-if="authStatus === 'NONE'"
          type="info"
          :closable="false"
          show-icon
          title="未认证用户无法发布任务，请先完成认证"
          class="mb-16"
        />

        <el-form label-position="top" class="form">
          <el-form-item label="学号" required>
            <el-input v-model="certForm.studentNo" placeholder="请输入本人学号" />
          </el-form-item>

          <el-form-item label="学生证照片" required>
            <div class="upload-row">
              <el-upload
                :show-file-list="false"
                :http-request="handleUpload"
                accept="image/*"
                :disabled="uploading"
              >
                <el-button :loading="uploading">
                  {{ certForm.certImage ? '重新上传' : '上传照片' }}
                </el-button>
              </el-upload>

              <el-image
                v-if="certForm.certImage"
                :src="certForm.certImage"
                :preview-src-list="[certForm.certImage]"
                class="preview"
                fit="cover"
              />
              <span class="hint">照片仅本人与管理员可见</span>
            </div>
          </el-form-item>

          <el-button
            type="primary"
            :loading="certLoading"
            :disabled="authStatus === 'PENDING'"
            @click="submitCert"
          >
            提交认证
          </el-button>
        </el-form>
      </template>
    </el-card>

    <!-- 登录记录 -->
    <el-card shadow="never" class="card mt-16">
      <template #header>
        <div class="card-head">
          <span class="card-title">最近登录记录</span>
          <el-button text size="small" :icon="'Refresh'" @click="loadLogs">刷新</el-button>
        </div>
      </template>

      <div v-loading="logsLoading">
        <div v-if="logs.length" class="log-list">
          <div v-for="log in logs" :key="log.id" class="log-row">
            <el-tag :type="log.success ? 'success' : 'danger'" size="small">
              {{ log.success ? '成功' : '失败' }}
            </el-tag>
            <span class="log-ip">{{ log.ip }}</span>
            <span class="log-location">{{ log.location }}</span>
            <span class="log-agent ellipsis">{{ log.userAgent }}</span>
            <span class="log-time">{{ formatDateTime(log.loginAt) }}</span>
          </div>
        </div>
        <p v-else class="text-muted">暂无登录记录</p>
      </div>
    </el-card>

    <!-- 退出 -->
    <el-card shadow="never" class="card mt-16">
      <div class="card-head">
        <div>
          <div class="card-title">退出登录</div>
          <div class="hint">退出后当前设备上的凭证会立即失效</div>
        </div>
        <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 760px;
}

.page-title {
  margin: 0 0 20px;
  font-size: 22px;
  color: #303133;
}

.card {
  border-radius: 10px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.form {
  max-width: 420px;
}

.hint {
  font-size: 12px;
  color: #909399;
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}

.log-list {
  display: flex;
  flex-direction: column;
}

.log-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  font-size: 13px;
  border-bottom: 1px solid #f5f7fa;
}

.log-ip {
  width: 120px;
  color: #606266;
}

.log-location {
  width: 120px;
  color: #909399;
}

.log-agent {
  flex: 1;
  min-width: 0;
  color: #909399;
}

.log-time {
  color: #c0c4cc;
  white-space: nowrap;
}

.mb-16 {
  margin-bottom: 16px;
}

.mt-16 {
  margin-top: 16px;
}
</style>
