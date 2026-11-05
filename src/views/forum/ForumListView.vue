<script setup lang="ts">
/**
 * 社区论坛列表（FM-01）
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { forumApi } from '@/api'
import type { Post } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import { fromNow } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const list = ref<Post[]>([])
const loading = ref(false)
const sortBy = ref<'LATEST' | 'HOT'>('LATEST')
const keyword = ref('')

/** 常用板块，点一下即筛选 */
const tags = ['全部', '学习交流', '生活求助', '经验分享', '失物招领']
const activeTag = ref('全部')

async function load() {
  loading.value = true
  try {
    const res = await forumApi.fetchPosts({
      sortBy: sortBy.value,
      keyword: keyword.value || undefined,
      tag: activeTag.value === '全部' ? undefined : activeTag.value,
      size: 20,
    })
    list.value = res.list
  } finally {
    loading.value = false
  }
}

function selectTag(tag: string) {
  activeTag.value = tag
  load()
}

function goCreate() {
  if (!userStore.isLogin) {
    router.push({ name: 'login', query: { redirect: '/forum/create' } })
    return
  }
  router.push('/forum/create')
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h1 class="page-title">社区论坛</h1>
        <p class="page-sub">分享日常、提问求助、交换经验</p>
      </div>
      <el-button type="primary" @click="goCreate">
        <el-icon class="mr-4"><EditPen /></el-icon>
        发帖
      </el-button>
    </div>

    <el-card shadow="never" class="card">
      <div class="toolbar">
        <div class="tags">
          <el-check-tag
            v-for="t in tags"
            :key="t"
            :checked="activeTag === t"
            class="tag"
            @change="selectTag(t)"
          >
            {{ t }}
          </el-check-tag>
        </div>

        <div class="tools">
          <el-input
            v-model="keyword"
            placeholder="搜索帖子"
            clearable
            class="w-200"
            @keyup.enter="load"
          />
          <el-radio-group v-model="sortBy" size="small" @change="load">
            <el-radio-button value="LATEST">最新</el-radio-button>
            <el-radio-button value="HOT">最热</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div v-loading="loading">
        <div v-if="list.length" class="posts">
          <article
            v-for="p in list"
            :key="p.id"
            class="post"
            @click="router.push(`/forum/${p.id}`)"
          >
            <div class="post-head">
              <el-avatar :size="36" :src="p.author?.avatar ?? undefined">
                {{ p.author?.nickname?.slice(0, 1) ?? '?' }}
              </el-avatar>
              <div class="post-meta">
                <div class="author">{{ p.author?.nickname }}</div>
                <div class="time">{{ fromNow(p.createdAt) }}</div>
              </div>
              <div class="post-tags">
                <el-tag v-for="t in p.tags" :key="t" size="small" type="info" effect="plain">
                  {{ t }}
                </el-tag>
              </div>
            </div>

            <h2 class="post-title">{{ p.title }}</h2>
            <p class="post-content ellipsis-2">{{ p.content }}</p>

            <div class="post-footer">
              <span class="stat">
                <el-icon><ChatDotRound /></el-icon>
                {{ p.replyCount }}
              </span>
              <span class="stat">
                <el-icon><Star /></el-icon>
                {{ p.likeCount }}
              </span>
            </div>
          </article>
        </div>

        <EmptyState
          v-else-if="!loading"
          text="还没有帖子"
          description="来发第一条吧"
          action-text="发帖"
          @action="goCreate"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 22px;
  color: #303133;
}

.page-sub {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.card {
  border-radius: 10px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  margin-bottom: 6px;
  border-bottom: 1px solid #f2f6fc;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  font-size: 13px;
}

.tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.w-200 {
  width: 200px;
}

.posts {
  display: flex;
  flex-direction: column;
}

.post {
  padding: 16px 8px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.2s;
}

.post:hover {
  background: #fafcff;
}

.post-head {
  display: flex;
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

.post-tags {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.post-title {
  margin: 12px 0 8px;
  font-size: 17px;
  color: #303133;
}

.post-content {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}

.post-footer {
  display: flex;
  gap: 18px;
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
}

.stat {
  display: flex;
  align-items: center;
  gap: 4px;
}

.mr-4 {
  margin-right: 4px;
}
</style>
