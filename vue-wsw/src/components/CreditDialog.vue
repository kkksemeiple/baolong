<script setup lang="ts">
/**
 * 信誉分明细弹窗
 * - 展示：总分、等级、五项指标、统计窗口、最近参与计算的接单记录
 * - 目的：让「为什么是这个分」可见，否则用户只会觉得掉分是玄学
 */
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchCreditDetail } from '@/api/user'
import type { CreditDetail } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  userId: number | 'me'
  /** 展示用的昵称，标题里拼一下「某某的信誉分」 */
  nickname?: string
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const loading = ref(false)
const detail = ref<CreditDetail | null>(null)

const METRICS = [
  { key: 'finishRate', label: '完成率', weight: '30%' },
  { key: 'onTimeRate', label: '准时率', weight: '25%' },
  { key: 'reviewScore', label: '平均评价', weight: '25%' },
  { key: 'disputeRate', label: '争议率', weight: '10%', invert: true },
  { key: 'cancelRate', label: '取消率', weight: '10%', invert: true },
] as const

function percent(key: string, value: number) {
  if (key === 'reviewScore') {
    return Math.round((value / 5) * 100)
  }
  return Math.round(value * 100)
}

function levelTagType(level: string) {
  if (level === 'EXCELLENT') return 'success'
  if (level === 'GOOD') return 'primary'
  if (level === 'NORMAL') return 'info'
  if (level === 'LIMITED') return 'warning'
  return 'danger'
}

function formatTime(v: string | null) {
  if (!v) return '—'
  return v.slice(0, 16)
}

async function load() {
  loading.value = true
  try {
    detail.value = await fetchCreditDetail(props.userId)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载信誉分失败')
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) void load()
  },
)
</script>

<template>
  <el-dialog v-model="visible" :title="`${nickname ? nickname + '的' : ''}信誉分明细`" width="620px">
    <div v-loading="loading">
      <template v-if="detail">
        <div class="head">
          <div class="score">
            <span class="num">{{ detail.score }}</span>
            <span class="unit">分</span>
          </div>
          <div class="head-right">
            <el-tag :type="levelTagType(detail.level)" effect="light">{{ detail.levelLabel }}</el-tag>
            <div class="head-tip">
              统计最近 {{ detail.windowDays }} 天、{{ detail.totalClaims }} 条有效接单（已完成
              {{ detail.finishedClaims }} 条）
            </div>
            <div v-if="!detail.canAccept" class="head-warn">
              低于 80 分，暂时不能接单；把后续任务按时完成、不再放弃，会逐步回升。
            </div>
          </div>
        </div>

        <div class="metrics">
          <div v-for="m in METRICS" :key="m.key" class="metric">
            <div class="metric-top">
              <span class="metric-label">{{ m.label }}</span>
              <span class="metric-value">{{ percent(m.key, detail[m.key]) }}%</span>
            </div>
            <el-progress
              :percentage="percent(m.key, detail[m.key])"
              :show-text="false"
              :stroke-width="6"
              :color="'invert' in m && m.invert ? '#f59e0b' : '#409eff'"
            />
            <div class="metric-foot">权重 {{ m.weight }}</div>
          </div>
        </div>

        <div v-if="detail.totalClaims < 20" class="smooth-tip">
          <el-icon><InfoFilled /></el-icon>
          样本不足 20 单，以上比率已按新手保护值（完成率 90%、准时率 90%、评价 4.5、争议/取消 5%）平滑，
          所以会出现「完成率 72% 但完成单数 0」这种看起来矛盾的数字——单量够了就会自动切到真实值。
        </div>

        <el-alert type="info" :closable="false" class="formula">
          分数 = 120 × (0.30×完成率 + 0.25×准时率 + 0.25×平均评价/5 + 0.10×(1−争议率) + 0.10×(1−取消率))
          <div class="formula-sub">
            前 5 单按新手保护值计算（109.2 分），20 单后完全按真实数据；发布方自己取消的不计入你的分母。
          </div>
        </el-alert>

        <div class="section-title">参与计算的最近接单</div>
        <el-empty v-if="!detail.records.length" description="还没有接单记录，显示的分数来自新手保护值" :image-size="70" />
        <el-table v-else :data="detail.records" size="small" max-height="240">
          <el-table-column prop="taskTitle" label="任务" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="88">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'CANCELLED' ? 'danger' : row.status === 'FINISHED' ? 'success' : 'info'">
                {{ row.statusLabel }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="评价" width="70">
            <template #default="{ row }">{{ row.rating ? row.rating + ' 星' : '—' }}</template>
          </el-table-column>
          <el-table-column label="准时" width="66">
            <template #default="{ row }">
              <span :class="row.onTime ? 'ok' : 'no'">{{ row.onTime ? '是' : '否' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="接单时间" width="130">
            <template #default="{ row }">{{ formatTime(row.claimedAt) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else-if="!loading" description="暂无数据" :image-size="80" />
    </div>
  </el-dialog>
</template>

<style scoped>
.head {
  display: flex;
  gap: 18px;
  align-items: center;
  padding: 6px 0 14px;
  border-bottom: 1px solid #f0f2f5;
}

.score {
  display: flex;
  align-items: baseline;
  color: #409eff;
}

.score .num {
  font-size: 40px;
  font-weight: 700;
  line-height: 1;
}

.score .unit {
  margin-left: 4px;
  font-size: 14px;
  color: #909399;
}

.head-right {
  flex: 1;
}

.head-tip {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.head-warn {
  margin-top: 6px;
  font-size: 13px;
  color: #e6a23c;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px 24px;
  margin: 16px 0;
}

.metric-top {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 4px;
}

.metric-label {
  color: #606266;
}

.metric-value {
  color: #303133;
  font-weight: 600;
}

.metric-foot {
  margin-top: 3px;
  font-size: 12px;
  color: #c0c4cc;
}

.smooth-tip {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  margin: 0 0 14px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fdf6ec;
  color: #b88230;
  font-size: 12px;
  line-height: 1.6;
}

.formula {
  margin-bottom: 14px;
}

.formula-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.section-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.ok {
  color: #67c23a;
}

.no {
  color: #f56c6c;
}

@media (max-width: 640px) {
  .metrics {
    grid-template-columns: 1fr;
  }
}
</style>
