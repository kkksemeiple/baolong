<script setup lang="ts">
/**
 * 我的帖子（个人中心）
 * 和「我的任务」同一思路：只列自己发布的，
 * 但这里**包含已被下架的**，并把状态原样显示出来，
 * 否则作者只会看到帖子「凭空消失」，不知道是被平台下架了。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { forumApi } from '@/api'
import type { Post } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { fromNow } from '@/utils/format'

const router = useRouter()

const list = ref<Post[]>([])
const loading = ref(false)
const hasMore = ref(false)
const cursor = ref<string | null>(null)

const STATUS_TEXT: Record<string, { label: string; type: 'success' | 'warning' | 'danger' }> = {
  NORMAL: { label: '正常', type: 'success' },
  OFFLINE: { label: '已下架', type: 'warning' },
  DELETED: { label: '已删除', type: 'danger' },
}
const DEFAULT_STATUS = { label: '正常', type: 'success' } as const

function statusOf(status?: string) {
  return (status && STATUS_TEXT[status]) || DEFAULT_STATUS
}

async function load(reset = false) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await forumApi.fetchMyPosts({
      cursor: reset ? undefined : cursor.value,
      size: 10,
    })
    list.value = reset ? res.list : [...list.value, ...res.list]
    cursor.value = res.nextCursor
    hasMore.value = res.hasMore
  } finally {
    loading.value = false
  }
}

function openPost(p: Post) {
  router.push(`/forum/${p.id}`)
}

onMounted(() => load(true))
</script>

<template>
  <div class="page">
    <h1 class="page-title">我的帖子</h1>

    <el-card shadow="never" class="card">
      <el-skeleton v-if="loading && !list.length" :rows="4" animated />

      <template v-else>
        <EmptyState
          v-if="!list.length"
          text="还没有发布过帖子"
          description="去社区论坛发一条，分享你的日常或求助吧"
        >
          <el-button type="primary" @click="router.push('/forum')">去社区论坛</el-button>
        </EmptyState>

        <div v-for="p in list" :key="p.id" class="post-row">
          <div class="post-main">
            <div class="post-head">
              <span class="post-title" @click="openPost(p)">{{ p.title }}</span>
              <el-tag :type="statusOf(p.status).type" size="small" effect="plain">
                {{ statusOf(p.status).label }}
              </el-tag>
            </div>
            <p class="post-excerpt">{{ p.content }}</p>
            <div class="post-meta">
              <span>{{ fromNow(p.createdAt) }}</span>
              <span>回复 {{ p.replyCount }}</span>
              <span>点赞 {{ p.likeCount }}</span>
              <span v-if="p.tags?.length" class="tags">
                <el-tag v-for="t in p.tags" :key="t" size="small" effect="plain">{{ t }}</el-tag>
              </span>
            </div>
          </div>
          <div class="post-actions">
            <el-button text type="primary" @click="openPost(p)">查看</el-button>
          </div>
        </div>

        <div v-if="hasMore" class="more">
          <el-button :loading="loading" @click="load(false)">加载更多</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 900px;
  margin: 0 auto;
}

.page-title {
  margin: 0 0 16px;
  font-size: 22px;
}

.post-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #f0f2f5;
}

.post-row:last-of-type {
  border-bottom: none;
}

.post-main {
  flex: 1;
  min-width: 0;
}

.post-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.post-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  cursor: pointer;
}

.post-title:hover {
  color: #409eff;
}

.post-excerpt {
  margin: 6px 0;
  color: #606266;
  font-size: 13px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.tags {
  display: flex;
  gap: 4px;
}

.more {
  text-align: center;
  padding-top: 12px;
}
</style>
