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
/**
 * 两份数据要分开：
 * - certInfo：服务端确认过的实名信息（只读展示用）
 * - certDraft：用户正在填/正在改的草稿（表单用）
 * 混用会出问题：提交失败时若把草稿当成「已认证信息」显示，
 * 用户会以为自己已经用别人的学号认证成功了。
 */
const certInfo = reactive({ realName: '', studentNo: '', certImage: '' })
const certDraft = reactive({ realName: '', studentNo: '', certImage: '' })
/** 已认证用户点了「申请变更」后，才展开表单 */
const certChanging = ref(false)
const certLoading = ref(false)
const uploading = ref(false)

async function loadAuth() {
  try {
    const res = await authApi.fetchAuthStatus()
    authStatus.value = res.authStatus
    rejectReason.value = res.rejectReason
    // 服务端确认值：只读展示用，必须如实反映后端（没有就清空）
    certInfo.studentNo = res.studentNo || ''
    certInfo.realName = res.realName || ''
    certInfo.certImage = res.certImage || ''
    // 草稿：默认与服务端一致；用户改动只影响草稿
    certDraft.studentNo = certInfo.studentNo
    certDraft.realName = certInfo.realName
    certDraft.certImage = certInfo.certImage
  } catch {
    authStatus.value = 'NONE'
  }
}

/** 上传学生证照片（写入草稿，不碰 certInfo） */
async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    const res = await uploadImage(options.file, 'cert')
    certDraft.certImage = res.url
    ElMessage.success('照片已上传')
  } finally {
    uploading.value = false
  }
}

async function submitCert() {
  if (!certDraft.realName.trim()) {
    ElMessage.warning('请填写真实姓名')
    return
  }
  if (!certDraft.studentNo.trim()) {
    ElMessage.warning('请填写学号')
    return
  }
  if (!certDraft.certImage) {
    ElMessage.warning('请上传学生证照片')
    return
  }
  certLoading.value = true
  try {
    await authApi.submitStudentCert({
      realName: certDraft.realName.trim(),
      studentNo: certDraft.studentNo.trim(),
      certImage: certDraft.certImage,
    })
    ElMessage.success('已提交，等待管理员审核')
    certChanging.value = false
    await loadAuth()
    await userStore.fetchMe()
  } catch {
    // 提交失败（学号被占用、无变化、网络错误等）：
    // 1) 保持表单打开，草稿原样保留，用户改一下就能重提；
    // 2) 绝不把草稿当成「已认证信息」展示——只读区读的是 certInfo。
    certChanging.value = true
    ElMessage.warning('提交未成功，请核对信息后重试')
  } finally {
    certLoading.value = false
  }
}

/* ---------------- 登录记录 ---------------- */
/** 放弃变更：重新拉一次服务端数据，丢掉表单里的临时改动 */
async function cancelCertChange() {
  certChanging.value = false
  await loadAuth()
}

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

      <!-- 已认证：先给本人看当前认证信息，想改再点「申请变更」 -->
      <template v-if="authStatus === 'APPROVED' && !certChanging">
        <el-alert
          type="success"
          :closable="false"
          show-icon
          title="已完成实名认证，可以正常发布与接取任务"
        />

        <el-descriptions :column="2" border size="small" class="cert-info">
          <el-descriptions-item label="真实姓名">
            {{ certInfo.realName || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="学号">
            {{ certInfo.studentNo || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="学生证照片" :span="2">
            <el-image
              v-if="certInfo.certImage"
              :src="certInfo.certImage"
              :preview-src-list="[certInfo.certImage]"
              preview-teleported
              fit="cover"
              class="cert-img"
            />
            <span v-else class="text-muted">未上传</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="mt-16"
          title="修改实名信息需要重新审核"
          description="提交后状态会变回「审核中」，审核通过前无法发布与接取任务。学号一旦被其他账号认证过，就不能再用来认证本账号。"
        />

        <el-button class="mt-16" @click="certChanging = true">申请变更实名信息</el-button>
      </template>

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

        <el-form label-position="top" class="form">
          <el-form-item label="真实姓名" required>
            <el-input v-model="certDraft.realName" placeholder="请输入本人真实姓名" maxlength="20" />
          </el-form-item>

          <el-form-item label="学号" required>
            <el-input v-model="certDraft.studentNo" placeholder="请输入本人学号" />
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
                  {{ certDraft.certImage ? '重新上传' : '上传照片' }}
                </el-button>
              </el-upload>

              <el-image
                v-if="certDraft.certImage"
                :src="certDraft.certImage"
                :preview-src-list="[certDraft.certImage]"
                class="preview"
                fit="cover"
              />
              <span class="hint">照片仅本人与管理员可见，用于核对学号与姓名是否一致</span>
            </div>
          </el-form-item>

          <el-button
            type="primary"
            :loading="certLoading"
            :disabled="authStatus === 'PENDING'"
            @click="submitCert"
          >
            {{ authStatus === 'APPROVED' ? '提交变更（需重新审核）' : '提交认证' }}
          </el-button>
          <el-button v-if="certChanging" class="ml-8" @click="cancelCertChange">
            取消变更
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

/* 已认证时展示的实名信息 */
.cert-info {
  margin-top: 16px;
}

.cert-img {
  width: 120px;
  height: 80px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  cursor: zoom-in;
}

.ml-8 {
  margin-left: 8px;
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
