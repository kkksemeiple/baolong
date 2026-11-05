<script setup lang="ts">
/**
 * 帖子详情（FM-01 + FM-03 回帖 + FM-04 删帖 + FM-05 收藏）
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { forumApi } from '@/api'
import type { Post, Reply } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import { fromNow } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postId = computed(() => Number(route.params.id))
const post = ref<Post | null>(null)
const replies = ref<Reply[]>([])
const loading = ref(false)
const draft = ref('')
const replyTo = ref<Reply | null>(null)
const sending = ref(false)

const isAuthor = computed(() => post.value?.authorId === userStore.user?.id)

async function load() {
  loading.value = true
  try {
    const [p, rs] = await Promise.all([
      forumApi.fetchPostDetail(postId.value),
      forumApi.fetchReplies(postId.value),
    ])
    post.value = p
    replies.value = rs
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
        </div>
      </el-card>

      <!-- 回复区 -->
      <el-card shadow="never" class="card mt-16">
        <h3 class="section-title">全部回复（{{ replies.length }}）</h3>

        <div v-if="replies.length" class="replies">
          <div v-for="r in replies" :key="r.id" class="reply">
            <el-avatar :size="32" :src="r.author?.avatar ?? undefined">
              {{ r.author?.nickname?.slice(0, 1) ?? '?' }}
            </el-avatar>
            <div class="reply-main">
              <div class="reply-head">
                <span class="author">{{ r.author?.nickname }}</span>
                <span class="time">{{ fromNow(r.createdAt) }}</span>
              </div>
              <p class="reply-content">{{ r.content }}</p>
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

              <!-- 二级回复 -->
              <div v-if="r.children?.length" class="children">
                <div v-for="c in r.children" :key="c.id" class="child">
                  <div class="child-head">
                    <span class="author">{{ c.author?.nickname }}</span>
                    <span class="time">{{ fromNow(c.createdAt) }}</span>
                  </div>
                  <p class="reply-content">{{ c.content }}</p>
                  <el-button
                    v-if="c.authorId === userStore.user?.id"
                    text
                    size="small"
                    type="danger"
                    @click="handleDeleteReply(c)"
                  >
                    删除
                  </el-button>
                </div>
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

.children {
  padding-left: 12px;
  margin-top: 8px;
  border-left: 2px solid #ebeef5;
}

.child {
  padding: 8px 0;
}

.child-head {
  display: flex;
  align-items: center;
  gap: 8px;
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
