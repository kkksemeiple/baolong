<script setup lang="ts">
/**
 * 数据看板（AD-05）
 *
 * 关键恒等式：流通量 + 冻结量 + 欠款 = 累计发行 − 累计回收
 * 趋势图用纯 CSS 柱状图实现，避免引入图表库依赖。
 */
import { computed, onMounted, ref } from 'vue'

import { adminApi } from '@/api'
import type { AdminOverview, TrendPoint } from '@/api/types'
import { formatNumber } from '@/utils/format'

const overview = ref<AdminOverview | null>(null)
const trend = ref<TrendPoint[]>([])
const loading = ref(false)

/** 恒等式是否成立：流通 + 冻结 + 欠款 == 发行 − 回收 */
const balanced = computed(() => {
  const o = overview.value
  if (!o) return true
  return o.coinCirculating + o.coinFrozen + o.coinDebt === o.coinIssued - o.coinRecycled
})

/** 趋势图最大值，用于计算柱高百分比 */
const maxValue = computed(() => {
  if (!trend.value.length) return 1
  return Math.max(...trend.value.map((t) => Math.max(t.published, t.claimed))) || 1
})

async function load() {
  loading.value = true
  try {
    const [o, t] = await Promise.all([adminApi.fetchOverview(), adminApi.fetchTrend(30)])
    overview.value = o
    trend.value = t
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <!-- 概览卡片 -->
    <div v-if="overview" class="cards">
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">用户数</div>
        <div class="stat-value">{{ formatNumber(overview.userCount) }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">任务数</div>
        <div class="stat-value">{{ formatNumber(overview.taskCount) }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">帖子数</div>
        <div class="stat-value">{{ formatNumber(overview.postCount) }}</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">完成率</div>
        <div class="stat-value">{{ overview.finishRate }}%</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-label">纠纷率</div>
        <div class="stat-value" :class="{ danger: overview.disputeRate > 5 }">
          {{ overview.disputeRate }}%
        </div>
      </el-card>
    </div>

    <!-- 资金概览 -->
    <el-card v-if="overview" shadow="never" class="block">
      <template #header>
        <div class="card-head">
          <span class="card-title">屋币资金概览</span>
          <el-tag :type="balanced ? 'success' : 'danger'" size="small">
            {{ balanced ? '对账通过' : '账目异常' }}
          </el-tag>
        </div>
      </template>

      <div class="coin-grid">
        <div class="coin-item">
          <div class="coin-label">累计发行</div>
          <div class="coin-value">{{ formatNumber(overview.coinIssued) }}</div>
        </div>
        <div class="coin-item">
          <div class="coin-label">当前流通</div>
          <div class="coin-value">{{ formatNumber(overview.coinCirculating) }}</div>
        </div>
        <div class="coin-item">
          <div class="coin-label">冻结中</div>
          <div class="coin-value">{{ formatNumber(overview.coinFrozen) }}</div>
        </div>
        <div class="coin-item">
          <div class="coin-label">已回收</div>
          <div class="coin-value">{{ formatNumber(overview.coinRecycled) }}</div>
        </div>
        <div class="coin-item">
          <div class="coin-label">欠款</div>
          <div class="coin-value">{{ formatNumber(overview.coinDebt) }}</div>
        </div>
      </div>

      <el-alert
        :type="balanced ? 'success' : 'error'"
        :closable="false"
        show-icon
        class="mt-16"
      >
        <template #title>
          恒等式校验：流通 {{ overview.coinCirculating }} + 冻结 {{ overview.coinFrozen }} +
          欠款 {{ overview.coinDebt }} =
          {{ overview.coinCirculating + overview.coinFrozen + overview.coinDebt }}，
          发行 {{ overview.coinIssued }} − 回收 {{ overview.coinRecycled }} =
          {{ overview.coinIssued - overview.coinRecycled }}
        </template>
      </el-alert>
    </el-card>

    <!-- 趋势图 -->
    <el-card shadow="never" class="block">
      <template #header>
        <div class="card-head">
          <span class="card-title">近 30 天趋势</span>
          <div class="legend">
            <span class="legend-item"><i class="dot published" />发布量</span>
            <span class="legend-item"><i class="dot claimed" />接单量</span>
          </div>
        </div>
      </template>

      <div class="chart">
        <div v-for="point in trend" :key="point.date" class="bar-group" :title="point.date">
          <div class="bar published" :style="{ height: `${(point.published / maxValue) * 100}%` }" />
          <div class="bar claimed" :style="{ height: `${(point.claimed / maxValue) * 100}%` }" />
        </div>
      </div>

      <div class="axis">
        <span>{{ trend[0]?.date }}</span>
        <span>{{ trend[trend.length - 1]?.date }}</span>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 14px;
}

.stat-card {
  border-radius: 10px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}

.stat-value.danger {
  color: #f56c6c;
}

.block {
  margin-top: 16px;
  border-radius: 10px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.coin-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
}

.coin-label {
  font-size: 12px;
  color: #909399;
}

.coin-value {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 600;
  color: #409eff;
}

.legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.dot.published {
  background: #409eff;
}

.dot.claimed {
  background: #67c23a;
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: 3px;
  height: 180px;
  padding-top: 10px;
}

.bar-group {
  display: flex;
  flex: 1;
  align-items: flex-end;
  gap: 1px;
  height: 100%;
}

.bar {
  flex: 1;
  min-height: 2px;
  border-radius: 1px 1px 0 0;
  transition: height 0.3s;
}

.bar.published {
  background: #409eff;
}

.bar.claimed {
  background: #67c23a;
}

.axis {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: #c0c4cc;
}

.mt-16 {
  margin-top: 16px;
}
</style>
