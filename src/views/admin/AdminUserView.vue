<script setup lang="ts">
/**
 * 用户管理（AD-01 + UA-07 认证审核）
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { adminApi } from '@/api'
import type { AdminUserRow, UserStatus } from '@/api/types'
import StatusTag from '@/components/StatusTag.vue'
import { formatDate } from '@/utils/format'
import { USER_STATUS_META } from '@/utils/constants'

const list = ref<AdminUserRow[]>([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<UserStatus | ''>('')

const statusOptions = Object.entries(USER_STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

async function load() {
  loading.value = true
  try {
    const res = await adminApi.fetchAdminUsers({
      keyword: keyword.value || undefined,
      status: (statusFilter.value || undefined) as UserStatus | undefined,
      page: 1,
      size: 20,
    })
    list.value = res.list
  } finally {
    loading.value = false
  }
}

/** 封禁：必须填写原因 */
async function handleBan(row: AdminUserRow) {
  try {
    const { value } = await ElMessageBox.prompt(
      `封禁「${row.nickname}」后该用户将无法登录，请填写原因`,
      '封禁用户',
      {
        inputType: 'textarea',
        inputValidator: (v) => (v && v.trim().length >= 2 ? true : '请填写封禁原因'),
      },
    )
    await adminApi.banUser(row.id, { reason: value })
    ElMessage.success('已封禁')
    await load()
  } catch {
    /* 用户取消 */
  }
}

async function handleUnban(row: AdminUserRow) {
  try {
    await ElMessageBox.confirm(`确定解封「${row.nickname}」吗？`, '解封用户', { type: 'warning' })
    await adminApi.unbanUser(row.id)
    ElMessage.success('已解封')
    await load()
  } catch {
    /* 用户取消 */
  }
}

async function handleDelete(row: AdminUserRow) {
  try {
    const { value } = await ElMessageBox.prompt(
      `删除「${row.nickname}」的账号不可恢复，请填写原因`,
      '删除账号',
      {
        inputType: 'textarea',
        inputValidator: (v) => (v && v.trim().length >= 2 ? true : '请填写删除原因'),
      },
    )
    await adminApi.deleteUser(row.id, value)
    ElMessage.success('已删除')
    await load()
  } catch {
    /* 用户取消 */
  }
}

/** 审核学号实名认证 */
async function handleCert(row: AdminUserRow, pass: boolean) {
  try {
    if (pass) {
      await ElMessageBox.confirm(`确认「${row.nickname}」的实名认证通过？`, '认证审核')
      await adminApi.reviewStudentCert(row.id, { pass: true })
    } else {
      const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回认证', {
        inputType: 'textarea',
        inputValidator: (v) => (v && v.trim().length >= 2 ? true : '请填写驳回原因'),
      })
      await adminApi.reviewStudentCert(row.id, { pass: false, reason: value })
    }
    ElMessage.success('操作成功')
    await load()
  } catch {
    /* 用户取消 */
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never" class="card">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索账号或昵称"
        clearable
        class="w-220"
        @keyup.enter="load"
      />
      <el-select v-model="statusFilter" placeholder="全部状态" clearable class="w-140" @change="load">
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" plain @click="load">查询</el-button>
      <el-button text :icon="'Refresh'" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="32" :src="row.avatar ?? undefined">
              {{ row.nickname?.slice(0, 1) }}
            </el-avatar>
            <div>
              <div class="name">{{ row.nickname }}</div>
              <div class="username">@{{ row.username }}</div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="creditScore" label="信用分" width="90" align="center" />

      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <StatusTag :value="row.status" kind="user" size="small" />
        </template>
      </el-table-column>

      <el-table-column label="实名认证" width="100" align="center">
        <template #default="{ row }">
          <StatusTag :value="row.authStatus" kind="auth" size="small" />
        </template>
      </el-table-column>

      <el-table-column label="发布 / 接单" width="110" align="center">
        <template #default="{ row }">{{ row.publishedCount }} / {{ row.claimedCount }}</template>
      </el-table-column>

      <el-table-column label="被举报" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.reportedCount" type="danger" size="small">{{ row.reportedCount }}</el-tag>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="注册时间" width="120">
        <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.authStatus === 'PENDING'"
            size="small"
            type="primary"
            link
            @click="handleCert(row, true)"
          >
            通过认证
          </el-button>
          <el-button
            v-if="row.authStatus === 'PENDING'"
            size="small"
            type="warning"
            link
            @click="handleCert(row, false)"
          >
            驳回
          </el-button>

          <el-button
            v-if="row.status !== 'BANNED'"
            size="small"
            type="danger"
            link
            @click="handleBan(row)"
          >
            封禁
          </el-button>
          <el-button v-else size="small" type="success" link @click="handleUnban(row)">
            解封
          </el-button>

          <el-button size="small" type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.card {
  border-radius: 10px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.w-220 {
  width: 220px;
}

.w-140 {
  width: 140px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name {
  font-size: 14px;
  color: #303133;
}

.username {
  font-size: 12px;
  color: #909399;
}
</style>
