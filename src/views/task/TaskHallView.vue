<script setup lang="ts">
/**
 * 任务大厅（TK-01 + TK-04 搜索 + TK-05 筛选排序 + TK-06 推荐 + TK-07 收藏 + TK-08 接取）
 *
 * 交互说明：
 * - 顶部「为你推荐」按历史接单类型推荐，可关闭
 * - 筛选栏支持类型、报酬区间、排序
 * - 列表使用游标分页，下拉「加载更多」
 * - 接单前二次确认；接单成功后刷新列表（任务会从大厅消失）
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import { taskApi } from '@/api'
import type { Task, TaskQuery, TaskType } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import TaskCard from '@/components/TaskCard.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { TASK_SORT_OPTIONS, TASK_TYPE_META } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

/* ---------------- 列表状态 ---------------- */
const list = ref<Task[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)

/* ---------------- 推荐 ---------------- */
const recommend = ref<Task[]>([])
const showRecommend = ref(true)

/* ---------------- 筛选条件 ---------------- */
const filters = reactive<{
  type: TaskType | ''
  minReward: number | undefined
  maxReward: number | undefined
  sortBy: NonNullable<TaskQuery['sortBy']>
}>({
  type: '',
  minReward: undefined,
  maxReward: undefined,
  sortBy: 'DEFAULT',
})

const keyword = computed(() => (route.query.keyword as string) || '')

/** 类型下拉选项 */
const typeOptions = Object.entries(TASK_TYPE_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

/** 是否有任何筛选条件被激活 */
const filterActive = computed(
  () => Boolean(filters.type) || Boolean(filters.minReward) || Boolean(filters.maxReward),
)

/** 拉取第一页 */
async function loadList() {
  loading.value = true
  try {
    const res = await taskApi.fetchTaskList({
      keyword: keyword.value || undefined,
      type: (filters.type || undefined) as TaskType | undefined,
      minReward: filters.minReward,
      maxReward: filters.maxReward,
      sortBy: filters.sortBy,
      size: 12,
    })
    list.value = res.list
    nextCursor.value = res.nextCursor
    hasMore.value = res.hasMore
  } finally {
    loading.value = false
  }
}

/** 加载更多 */
async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  try {
    const res = await taskApi.fetchTaskList({
      keyword: keyword.value || undefined,
      type: (filters.type || undefined) as TaskType | undefined,
      minReward: filters.minReward,
      maxReward: filters.maxReward,
      sortBy: filters.sortBy,
      cursor: nextCursor.value ?? undefined,
      size: 12,
    })
    list.value = [...list.value, ...res.list]
    nextCursor.value = res.nextCursor
    hasMore.value = res.hasMore
  } finally {
    loadingMore.value = false
  }
}

/** 推荐列表（登录后才请求，失败静默） */
async function loadRecommend() {
  if (!userStore.isLogin) return
  try {
    recommend.value = await taskApi.fetchRecommendedTasks(3)
  } catch {
    recommend.value = []
  }
}

/** 重置筛选 */
function resetFilters() {
  filters.type = ''
  filters.minReward = undefined
  filters.maxReward = undefined
  filters.sortBy = 'DEFAULT'
  loadList()
}

/* ---------------- 卡片事件 ---------------- */

function goDetail(task: Task) {
  router.push(`/tasks/${task.id}`)
}

/** 收藏 / 取消收藏 */
async function handleFavorite(task: Task) {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    if (task.favorited) {
      await taskApi.unfavoriteTask(task.id)
      task.favorited = false
      ElMessage.success('已取消收藏')
    } else {
      await taskApi.favoriteTask(task.id)
      task.favorited = true
      ElMessage.success('已收藏')
    }
  } catch {
    // 拦截器已提示
  }
}

/** 接单 */
async function handleClaim(task: Task) {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认接取「${task.title}」吗？接单后请按时完成，无故放弃会影响信用分。`,
      '接取任务',
      { type: 'warning', confirmButtonText: '确认接单' },
    )
  } catch {
    return
  }

  try {
    await taskApi.claimTask(task.id)
    ElMessage.success('接单成功，可在「我的任务」中查看')
    await loadList()
    await loadRecommend()
  } catch {
    // 任务被别人抢先接走时，拦截器会提示"任务已被接取"
    await loadList()
  }
}

/* ---------------- 生命周期 ---------------- */

onMounted(async () => {
  await Promise.all([appStore.loadCategories(), loadList(), loadRecommend()])
})

// 顶部搜索框改变关键词时重新查询
watch(
  () => route.query.keyword,
  () => loadList(),
)
</script>

<template>
  <div class="page-container">
    <!-- 页面标题 -->
    <div class="page-head">
      <div>
        <h1 class="page-title">任务大厅</h1>
        <p class="page-sub">
          共 {{ list.length }} 条待接取委托
          <span v-if="keyword" class="keyword">· 搜索「{{ keyword }}」</span>
        </p>
      </div>
      <el-button type="primary" @click="router.push('/publish')">
        <el-icon class="mr-4"><Plus /></el-icon>
        发布任务
      </el-button>
    </div>

    <!-- 推荐 -->
    <section v-if="showRecommend && recommend.length" class="recommend">
      <div class="recommend-head">
        <span class="recommend-title">
          <el-icon><MagicStick /></el-icon>
          为你推荐
        </span>
        <el-button text size="small" @click="showRecommend = false">收起</el-button>
      </div>
      <div class="grid">
        <TaskCard
          v-for="t in recommend"
          :key="`rec-${t.id}`"
          :task="t"
          @click="goDetail"
          @favorite="handleFavorite"
          @claim="handleClaim"
        />
      </div>
    </section>

    <!-- 筛选栏 -->
    <section class="filters">
      <el-select v-model="filters.type" placeholder="任务类型" clearable class="w-140">
        <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>

      <div class="reward-range">
        <el-input-number
          v-model="filters.minReward"
          :min="0"
          :max="500"
          :controls="false"
          placeholder="最低"
          class="w-90"
        />
        <span class="sep">—</span>
        <el-input-number
          v-model="filters.maxReward"
          :min="0"
          :max="500"
          :controls="false"
          placeholder="最高"
          class="w-90"
        />
        <span class="unit">WB</span>
      </div>

      <el-select v-model="filters.sortBy" class="w-150">
        <el-option
          v-for="o in TASK_SORT_OPTIONS"
          :key="o.value"
          :label="o.label"
          :value="o.value"
        />
      </el-select>

      <el-button type="primary" plain @click="loadList">筛选</el-button>
      <el-button v-if="filterActive" text @click="resetFilters">重置</el-button>
    </section>

    <!-- 列表 -->
    <section v-loading="loading">
      <div v-if="list.length" class="grid">
        <TaskCard
          v-for="t in list"
          :key="t.id"
          :task="t"
          show-actions
          @click="goDetail"
          @favorite="handleFavorite"
          @claim="handleClaim"
        />
      </div>

      <EmptyState
        v-else-if="!loading"
        :text="keyword ? `没有找到包含「${keyword}」的任务` : '暂时没有待接取的委托'"
        description="换个关键词或筛选条件试试，也可以自己发一条"
        action-text="去发布任务"
        @action="router.push('/publish')"
      />

      <div v-if="hasMore" class="load-more">
        <el-button :loading="loadingMore" @click="loadMore">加载更多</el-button>
      </div>
    </section>
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

.keyword {
  color: #409eff;
}

/* 推荐区 */
.recommend {
  padding: 16px;
  margin-bottom: 20px;
  background: linear-gradient(135deg, #f0f7ff, #f7fbff);
  border: 1px solid #d9ecff;
  border-radius: 10px;
}

.recommend-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.recommend-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #409eff;
}

/* 筛选栏 */
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  margin-bottom: 18px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.reward-range {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sep {
  color: #c0c4cc;
}

.unit {
  font-size: 12px;
  color: #909399;
}

.w-90 {
  width: 90px;
}

.w-140 {
  width: 140px;
}

.w-150 {
  width: 150px;
}

/* 网格 */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
}

.load-more {
  margin-top: 24px;
  text-align: center;
}

.mr-4 {
  margin-right: 4px;
}
</style>
