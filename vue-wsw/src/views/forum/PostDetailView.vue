<script setup lang="ts">
/**
 * 帖子详情（FM-01 + FM-03 回帖 + FM-04 删帖 + FM-05 收藏）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { forumApi } from '@/api'
import type { Post, Reply } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import { fromNow } from '@/utils/format'

/** 回帖（扁平渲染，像贴吧那样：楼层按时间排，谁回复谁用「回复 @某人」标出来） */
type ReplyItem = Reply & { toName?: string }

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postId = computed(() => Number(route.params.id))
const post = ref<Post | null>(null)
const replies = ref<ReplyItem[]>([])
const loading = ref(false)
const draft = ref('')
const replyTo = ref<Reply | null>(null)
const sending = ref(false)

/* 举报弹窗 */
const reportVisible = ref(false)
const reportSending = ref(false)
const reportForm = reactive({ type: 'ILLEGAL_CONTENT', reason: '' })
const REPORT_TYPES = [
  { value: 'ILLEGAL_CONTENT', label: '违法违禁内容' },
  { value: 'HARASS', label: '辱骂 / 人身攻击' },
  { value: 'NO_SHOW', label: '疑似诈骗 / 带节奏' },
  { value: 'FAKE_DELIVERY', label: '虚假信息' },
  { value: 'OTHER', label: '其他' },
]

const isAuthor = computed(() => post.value?.authorId === userStore.user?.id)

/**
 * 后端返回扁平列表（带 parentId），这里只补一个「回复的是谁」，
 * **不做嵌套**：任何一条回复都能被回复，层级可能很深，
 * 层层缩进会越缩越窄；贴吧也是同样做法——平铺 + 「回复 @某人」。
 */
function decorate(flat: Reply[]): ReplyItem[] {
  const byId = new Map(flat.map((r) => [r.id, r]))
  return flat.map((r) => ({
    ...r,
    toName: r.parentId ? byId.get(r.parentId)?.author?.nickname : undefined,
  }))
}

async function load() {
  loading.value = true
  try {
    const [p, rs] = await Promise.all([
      forumApi.fetchPostDetail(postId.value),
      forumApi.fetchReplies(postId.value),
    ])
    post.value = p
    replies.value = decorate(rs)
  } catch {
    router.replace('/forum')
  } finally {
    loading.value = false
  }
}

/* ---------------- 点赞 / 收藏 ---------------- */
async function toggleLike() {
  if (!post.value) return
  if (!userStore.isLogin) return toLogin()
  const wasLiked = post.value.liked
  await forumApi.toggleLike(post.value.id, wasLiked)
  post.value.liked = !wasLiked
  post.value.likeCount += wasLiked ? -1 : 1
}

async function toggleFavorite() {
  if (!post.value) return
  if (!userStore.isLogin) return toLogin()
  if (post.value.favorited) {
    await forumApi.unfavoritePost(post.value.id)
    post.value.favorited = false
    ElMessage.success('已取消收藏')
  } else {
    await forumApi.favoritePost(post.value.id)
    post.value.favorited = true
    ElMessage.success('已收藏')
  }
}

/* ---------------- 回帖 ---------------- */
function setReplyTarget(r: Reply | null) {
  if (!userStore.isLogin) return toLogin()
  replyTo.value = r
}

async function handleReply() {
  const content = draft.value.trim()
  if (!content) {
    ElMessage.warning('请输入内容')
    return
  }
  if (!userStore.isLogin) return toLogin()

  sending.value = true
  try {
    await forumApi.createReply(postId.value, {
      content,
      parentId: replyTo.value?.id ?? null,
    })
    draft.value = ''
    replyTo.value = null
    ElMessage.success('回复成功')
    await load()
  } finally {
    sending.value = false
  }
}

/* ---------------- 删除 ---------------- */
async function handleDeletePost() {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确定删除这篇帖子吗？', '删除帖子', {
      type: 'warning',
    })
    await forumApi.deletePost(postId.value)
    ElMessage.success('已删除')
    router.replace('/forum')
  } catch {
    /* 用户取消 */
  }
}

async function handleDeleteReply(r: Reply) {
  try {
    await ElMessageBox.confirm('确定删除这条回复吗？', '删除回复', { type: 'warning' })
    await forumApi.deleteReply(r.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    /* 用户取消 */
  }
}

function toLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

/* ---------------- 举报 ---------------- */
function openReport() {
  if (!userStore.isLogin) return toLogin()
  reportForm.type = 'ILLEGAL_CONTENT'
  reportForm.reason = ''
  reportVisible.value = true
}

async function submitReport() {
  if (!post.value) return
  if (reportForm.reason.trim().length < 5) {
    ElMessage.warning('请填写至少 5 个字的举报说明')
    return
  }
  reportSending.value = true
  try {
    await forumApi.reportPost(post.value.id, {
      type: reportForm.type,
      reason: reportForm.reason.trim(),
    })
    reportVisible.value = false
    ElMessage.success('举报已提交，管理员会尽快处理')
  } finally {
    reportSending.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page-container narrow">
    <el-page-header class="mb-16" @back="router.push('/forum')">
      <template #content><span class="header-title">帖子详情</span></template>
    </el-page-header>

    <template v-if="post">
      <el-card shadow="never" class="card">
        <h1 class="title">{{ post.title }}</h1>

        <div class="meta">
          <el-avatar :size="32" :src="post.author?.avatar ?? undefined">
            {{ post.author?.nickname?.slice(0, 1) ?? '?' }}
          </el-avatar>
          <span class="author">{{ post.author?.nickname }}</span>
          <span class="time">{{ fromNow(post.createdAt) }}</span>
          <el-tag v-for="t in post.tags" :key="t" size="small" type="info" effect="plain">
            {{ t }}
          </el-tag>

          <el-button
            v-if="isAuthor"
            class="right"
            text
            type="danger"
            size="small"
            @click="handleDeletePost"
          >
            删除
          </el-button>
        </div>

        <div class="content">{{ post.content }}</div>

        <div class="actions">
          <el-button
            :type="post.liked ? 'primary' : 'default'"
            size="small"
            @click="toggleLike"
          >
            <el-icon class="mr-4"><Star /></el-icon>
            {{ post.liked ? '已赞' : '点赞' }} {{ post.likeCount }}
          </el-button>

          <el-button size="small" @click="toggleFavorite">
            <el-icon class="mr-4"><CollectionTag /></el-icon>
            {{ post.favorited ? '已收藏' : '收藏' }}
          </el-button>

          <el-button size="small" type="danger" plain @click="openReport">
            <el-icon class="mr-4"><Warning /></el-icon>
            举报
          </el-button>
        </div>
      </el-card>

      <!-- 回复区 -->
      <el-card shadow="never" class="card mt-16">
        <h3 class="section-title">全部回复（{{ replies.length }}）</h3>

        <div v-if="replies.length" class="replies">
          <div v-for="r in replies" :key="r.id" class="reply" :class="{ 'is-child': r.parentId }">
            <el-avatar :size="32" :src="r.author?.avatar ?? undefined">
              {{ r.author?.nickname?.slice(0, 1) ?? '?' }}
            </el-avatar>
            <div class="reply-main">
              <div class="reply-head">
                <span class="author">{{ r.author?.nickname }}</span>
                <span v-if="r.toName" class="to-name">回复 @{{ r.toName }}</span>
                <span class="time">{{ fromNow(r.createdAt) }}</span>
              </div>
              <p class="reply-content">{{ r.content }}</p>
              <!-- 每一层都能继续回复：贴吧的楼中楼也是这样无限接下去的 -->
              <div class="reply-actions">
                <el-button text size="small" @click="setReplyTarget(r)">回复</el-button>
                <el-button
                  v-if="r.authorId === userStore.user?.id"
                  text
                  size="small"
                  type="danger"
                  @click="handleDeleteReply(r)"
                >
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <EmptyState v-else text="还没有回复" description="来说点什么吧" />

        <!-- 回复输入 -->
        <div class="composer">
          <div v-if="replyTo" class="reply-hint">
            回复 <b>{{ replyTo.author?.nickname }}</b>
            <el-button text size="small" @click="replyTo = null">取消</el-button>
          </div>
          <el-input
            v-model="draft"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            :placeholder="userStore.isLogin ? '说点什么……' : '登录后即可回复'"
            :disabled="!userStore.isLogin"
          />
          <div class="composer-actions">
            <el-button v-if="!userStore.isLogin" type="primary" plain @click="toLogin">
              去登录
            </el-button>
            <el-button v-else type="primary" :loading="sending" @click="handleReply">
              发表回复
            </el-button>
          </div>
        </div>
      </el-card>
    </template>

    <!-- 举报弹窗 -->
    <el-dialog v-model="reportVisible" title="举报帖子" width="420px">
      <el-form label-position="top">
        <el-form-item label="举报类型">
          <el-select v-model="reportForm.type" class="w-full">
            <el-option
              v-for="t in REPORT_TYPES"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="举报说明" required>
          <el-input
            v-model="reportForm.reason"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            placeholder="请说明具体问题，管理员会据此核实（至少 5 个字）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="reportSending" @click="submitReport">
          提交举报
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 840px;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
}

.card {
  border-radius: 10px;
}

.title {
  margin: 0 0 14px;
  font-size: 22px;
  color: #303133;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.author {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.time {
  font-size: 12px;
  color: #c0c4cc;
}

.right {
  margin-left: auto;
}

.content {
  margin-top: 18px;
  font-size: 14px;
  line-height: 1.9;
  color: #606266;
  white-space: pre-wrap;
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 22px;
}

.section-title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.replies {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.reply {
  display: flex;
  gap: 10px;
}

.reply-main {
  flex: 1;
  min-width: 0;
}

.reply-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reply-content {
  margin: 6px 0;
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}

.reply-actions {
  display: flex;
  gap: 4px;
}

/* 楼中楼：用左侧竖线 + 浅底表现「这是回复别人的」，
   不按层级缩进——层级可能无限深，缩进会越来越窄 */
.reply.is-child {
  margin-left: 44px;
  padding-left: 12px;
  border-left: 2px solid #ebeef5;
}

/* 「回复 @某人」标记：二级回复必须一眼看出在回谁 */
.to-name {
  font-size: 12px;
  color: #409eff;
  background: #ecf5ff;
  padding: 1px 6px;
  border-radius: 4px;
}

.w-full {
  width: 100%;
}

.composer {
  padding-top: 20px;
  margin-top: 24px;
  border-top: 1px solid #f2f6fc;
}

.reply-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #909399;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.mt-16 {
  margin-top: 16px;
}

.mr-4 {
  margin-right: 4px;
}
</style>
