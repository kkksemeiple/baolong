<script setup lang="ts">
/**
 * 个人中心（UC-01）
 * 展示个人信息与信用数据，并支持修改昵称、头像、简介、擅长标签。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { userApi } from '@/api'
import StatusTag from '@/components/StatusTag.vue'
import { useUserStore } from '@/stores/user'
import { formatDate } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const editing = ref(false)
const saving = ref(false)

const form = reactive({
  nickname: '',
  intro: '',
  tags: [] as string[],
})

const tagOptions = ['代取快递', '代买', '打印复印', '搬运', '家教', '编程', '设计', '其他']

/** 进入编辑态时把当前值填进表单 */
function startEdit() {
  const u = userStore.user
  if (!u) return
  form.nickname = u.nickname
  form.intro = u.intro ?? ''
  form.tags = [...u.tags]
  editing.value = true
}

async function handleSave() {
  if (!form.nickname.trim()) {
    ElMessage.warning('昵称不能为空')
    return
  }
  saving.value = true
  try {
    await userApi.updateMyProfile({
      nickname: form.nickname,
      intro: form.intro,
      tags: form.tags,
    })
    await userStore.fetchMe()
    editing.value = false
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

// 页面加载时刷新一次，保证数据最新
onMounted(() => {
  userStore.fetchMe().catch(() => undefined)
})
</script>

<template>
  <div class="page-container narrow">
    <h1 class="page-title">个人中心</h1>

    <el-card v-if="userStore.user" shadow="never" class="card">
      <!-- 头部 -->
      <div class="head">
        <el-avatar :size="72" :src="userStore.user.avatar ?? undefined">
          {{ userStore.user.nickname.slice(0, 1) }}
        </el-avatar>

        <div class="head-main">
          <div class="name-row">
            <h2 class="name">{{ userStore.user.nickname }}</h2>
            <StatusTag :value="userStore.user.authStatus" kind="auth" size="small" />
            <StatusTag :value="userStore.user.status" kind="user" size="small" />
          </div>
          <p class="username">@{{ userStore.user.username }}</p>
          <p class="intro">{{ userStore.user.intro || '这个人很懒，什么都没写' }}</p>
          <div class="tags">
            <el-tag v-for="t in userStore.user.tags" :key="t" size="small" effect="plain">
              {{ t }}
            </el-tag>
            <span v-if="!userStore.user.tags.length" class="text-muted">还没有添加擅长标签</span>
          </div>
        </div>

        <el-button class="edit-btn" @click="startEdit">编辑资料</el-button>
      </div>

      <el-divider />

      <!-- 数据 -->
      <div class="stats">
        <div class="stat">
          <div class="stat-value">{{ userStore.user.creditScore }}</div>
          <div class="stat-label">信用分</div>
        </div>
        <div class="stat">
          <div class="stat-value">{{ userStore.user.finishedCount }}</div>
          <div class="stat-label">完成单数</div>
        </div>
        <div class="stat">
          <div class="stat-value">{{ userStore.user.goodRate }}%</div>
          <div class="stat-label">好评率</div>
        </div>
        <div class="stat">
          <div class="stat-value">{{ formatDate(userStore.user.createdAt) }}</div>
          <div class="stat-label">注册时间</div>
        </div>
      </div>

      <el-alert
        v-if="userStore.creditScore < 60"
        class="mt-16"
        type="warning"
        :closable="false"
        show-icon
        title="信用分低于 60，暂时无法发布或接取任务"
      />

      <!-- 快捷入口 -->
      <div class="shortcuts">
        <el-button @click="router.push('/my-tasks')">我的任务</el-button>
        <el-button @click="router.push('/wallet')">我的钱包</el-button>
        <el-button @click="router.push('/favorites')">我的收藏</el-button>
        <el-button @click="router.push('/history')">浏览历史</el-button>
        <el-button @click="router.push('/settings')">账号设置</el-button>
      </div>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editing" title="编辑资料" width="520px">
      <el-form label-position="top">
        <el-form-item label="昵称" required>
          <el-input v-model="form.nickname" maxlength="20" show-word-limit />
        </el-form-item>

        <el-form-item label="个人简介">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="3"
            maxlength="120"
            show-word-limit
            placeholder="简单介绍一下自己，接单时更容易被选中"
          />
        </el-form-item>

        <el-form-item label="擅长标签">
          <el-select v-model="form.tags" multiple class="w-full" placeholder="选择你擅长的类型">
            <el-option v-for="t in tagOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>

        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            账号、信用分、实名认证状态不可自行修改。
          </template>
        </el-alert>
      </el-form>

      <template #footer>
        <el-button @click="editing = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 860px;
}

.page-title {
  margin: 0 0 20px;
  font-size: 22px;
  color: #303133;
}

.card {
  border-radius: 10px;
}

.head {
  display: flex;
  gap: 20px;
}

.head-main {
  flex: 1;
  min-width: 0;
}

.name-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.name {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.username {
  margin: 4px 0 8px;
  font-size: 13px;
  color: #909399;
}

.intro {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.edit-btn {
  align-self: flex-start;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat {
  text-align: center;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #409eff;
}

.stat-label {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

.w-full {
  width: 100%;
}

.mt-16 {
  margin-top: 16px;
}

@media (max-width: 640px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
