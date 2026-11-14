<script setup lang="ts">
/**
 * 帖子管理
 * 管理员可查看全部帖子、下架违规帖（下架后前台不可见，作者会收到通知），
 * 也可以把误判下架的帖子恢复回来。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { adminApi } from '@/api'
import type { AdminPostRow } from '@/api/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

const list = ref<AdminPostRow[]>([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const page = ref(1)
const size = 10
const total = ref(0)

const statusOptions = [
  { value: 'NORMAL', label: '正常' },
  { value: 'OFFLINE', label: '已下架' },
  { value: 'DELETED', label: '作者已删除' },
]

function statusLabel(status: string) {
  return statusOptions.find((o) => o.value === status)?.label ?? status ?? '—'
}

function statusTagType(status: string) {
  if (status === 'NORMAL') return 'success'
  if (status === 'OFFLINE') return 'warning'
  return 'info'
}

async function load() {
  loading.value = true
  try {
    const res = await adminApi.fetchAdminPosts({
      keyword: keyword.value || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      size,
    })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function changePage(p: number) {
  page.value = p
  load()
}

async function openDetail(row: AdminPostRow) {
  router.push(`/forum/${row.id}`)
}

async function handleOffline(row: AdminPostRow) {
  try {
    const { value } = await ElMessageBox.prompt(
      `下架「${row.title}」后前台将看不到这条帖子，作者会收到通知。请填写原因`,
      '下架帖子',
      {
        inputType: 'textarea',
        inputValidator: (v) => (v && v.trim().length >= 2 ? true : '请填写下架原因'),
      },
    )
    await adminApi.offlinePost(row.id, value)
    ElMessage.success('已下架')
    await load()
  } catch {
    /* 用户取消 */
  }
}

async function handleRestore(row: AdminPostRow) {
  try {
    const { value } = await ElMessageBox.prompt(
      `恢复「${row.title}」后前台重新可见，作者会收到通知。请填写说明`,
      '恢复帖子',
      {
        inputType: 'textarea',
        inputPlaceholder: '例如：复核后确认内容合规',
      },
    )
    await adminApi.restorePost(row.id, value || '')
    ElMessage.success('已恢复')
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
        placeholder="搜索帖子标题"
        clearable
        class="w-240"
        @keyup.enter="search"
      />
      <el-select
        v-model="statusFilter"
        placeholder="全部状态"
        clearable
        class="w-140"
        @change="search"
      >
        <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="load">刷新</el-button>
      <span class="total">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="标题" min-width="240">
        <template #default="{ row }">
          <span class="title link" @click="openDetail(row)">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column label="作者" width="140">
        <template #default="{ row }">
          {{ row.author?.nickname || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="标签" width="160">
        <template #default="{ row }">
          <el-tag v-for="t in row.tags" :key="t" size="small" class="tag">{{ t }}</el-tag>
          <span v-if="!row.tags?.length">—</span>
        </template>
      </el-table-column>
      <el-table-column label="回复 / 赞" width="100">
        <template #default="{ row }">{{ row.replyCount }} / {{ row.likeCount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="举报" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.pendingReports > 0" type="danger" size="small" effect="dark">
            {{ row.pendingReports }} 条待处理
          </el-tag>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          <el-button
            v-if="row.status === 'NORMAL'"
            link
            type="danger"
            @click="handleOffline(row)"
          >
            下架
          </el-button>
          <el-button
            v-if="row.status === 'OFFLINE'"
            link
            type="success"
            @click="handleRestore(row)"
          >
            恢复
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        @current-change="changePage"
      />
    </div>
  </el-card>
</template>

<style scoped>
.card {
  border-radius: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.w-240 {
  width: 240px;
}

.w-140 {
  width: 140px;
}

.total {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
}

.title {
  font-weight: 500;
}

.link {
  color: #409eff;
  cursor: pointer;
}

.link:hover {
  text-decoration: underline;
}

.tag {
  margin-right: 4px;
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>
