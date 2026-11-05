<script setup lang="ts">
/**
 * 内容审核与仲裁（AD-02 + AD-03）
 *
 * 三个页签：
 * 1. 待审内容 —— 命中违禁词的自动进审，可通过 / 下架
 * 2. 举报处理 —— 用户举报，判定成立或不成立
 * 3. 争议仲裁 —— 任务纠纷，四种裁决结果
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { adminApi } from '@/api'
import type { AuditItem, Dispute, JudgeResult, Report } from '@/api/types'
import StatusTag from '@/components/StatusTag.vue'
import { formatDateTime } from '@/utils/format'
import { JUDGE_RESULT_META, REPORT_TYPE_META } from '@/utils/constants'

const tab = ref<'audit' | 'report' | 'dispute'>('audit')

const audits = ref<AuditItem[]>([])
const reports = ref<Report[]>([])
const disputes = ref<Dispute[]>([])
const loading = ref(false)

/* ---------------- 数据加载 ---------------- */
async function loadAudits() {
  const res = await adminApi.fetchAudits({ status: 'PENDING', page: 1, size: 50 })
  audits.value = res.list
}

async function loadReports() {
  const res = await adminApi.fetchReports({ status: 'PENDING', page: 1, size: 50 })
  reports.value = res.list
}

async function loadDisputes() {
  const res = await adminApi.fetchDisputes({ page: 1, size: 50 })
  disputes.value = res.list
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadAudits(), loadReports(), loadDisputes()])
  } finally {
    loading.value = false
  }
}

/* ---------------- 内容审核 ---------------- */
async function approve(item: AuditItem) {
  await adminApi.approveAudit(item.id)
  ElMessage.success('已通过')
  await loadAudits()
}

async function reject(item: AuditItem) {
  try {
    const { value } = await ElMessageBox.prompt('请填写下架原因（会通知作者）', '下架内容', {
      inputType: 'textarea',
      inputValidator: (v) => (v && v.trim().length >= 2 ? true : '请填写原因'),
    })
    await adminApi.rejectAudit(item.id, value)
    ElMessage.success('已下架')
    await loadAudits()
  } catch {
    /* 用户取消 */
  }
}

/* ---------------- 举报处理 ---------------- */
async function handleReport(item: Report, pass: boolean) {
  try {
    const { value } = await ElMessageBox.prompt(
      pass ? '请填写处理结果（如：已下架并扣除信用分）' : '请填写不受理的理由',
      pass ? '举报成立' : '举报不成立',
      {
        inputType: 'textarea',
        inputValidator: (v) => (v && v.trim().length >= 2 ? true : '请填写说明'),
      },
    )
    await adminApi.handleReport(item.id, {
      pass,
      result: value,
      penalty: pass ? 10 : 0,
    })
    ElMessage.success('处理完成')
    await loadReports()
  } catch {
    /* 用户取消 */
  }
}

/* ---------------- 争议仲裁 ---------------- */
const judgeVisible = ref(false)
const judgeTarget = ref<Dispute | null>(null)
const judgeForm = reactive<{ result: JudgeResult; ratio: number; remark: string }>({
  result: 'PUBLISHER',
  ratio: 50,
  remark: '',
})

function openJudge(d: Dispute) {
  judgeTarget.value = d
  judgeForm.result = 'PUBLISHER'
  judgeForm.ratio = 50
  judgeForm.remark = ''
  judgeVisible.value = true
}

async function submitJudge() {
  if (!judgeTarget.value) return
  if (judgeForm.remark.trim().length < 20) {
    ElMessage.warning('裁决理由至少 20 字')
    return
  }
  await adminApi.judgeDispute(judgeTarget.value.id, {
    result: judgeForm.result,
    ratio: judgeForm.result === 'PARTIAL' ? judgeForm.ratio : undefined,
    remark: judgeForm.remark,
  })
  judgeVisible.value = false
  ElMessage.success('裁决已完成')
  await loadDisputes()
}

const judgeOptions = Object.entries(JUDGE_RESULT_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

onMounted(loadAll)
</script>

<template>
  <el-card shadow="never" class="card">
    <el-tabs v-model="tab">
      <!-- 待审内容 -->
      <el-tab-pane :label="`待审内容 (${audits.length})`" name="audit">
        <div v-loading="loading">
          <div v-for="item in audits" :key="item.id" class="item">
            <div class="item-head">
              <el-tag size="small" type="warning">{{ item.targetType }}</el-tag>
              <span class="item-title">{{ item.title }}</span>
              <el-tag size="small" type="danger" effect="plain">{{ item.hitReason }}</el-tag>
              <span class="item-time">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <p class="item-content">{{ item.content }}</p>
            <div class="item-foot">
              <span class="text-muted">作者：{{ item.author?.nickname }}</span>
              <div>
                <el-button size="small" type="success" plain @click="approve(item)">通过</el-button>
                <el-button size="small" type="danger" plain @click="reject(item)">下架</el-button>
              </div>
            </div>
          </div>

          <el-empty v-if="!loading && !audits.length" description="没有待审核内容" />
        </div>
      </el-tab-pane>

      <!-- 举报处理 -->
      <el-tab-pane :label="`举报 (${reports.length})`" name="report">
        <div v-loading="loading">
          <div v-for="item in reports" :key="item.id" class="item">
            <div class="item-head">
              <el-tag size="small" type="danger">
                {{ REPORT_TYPE_META[item.type] ?? item.type }}
              </el-tag>
              <span class="item-title">{{ item.targetTitle }}</span>
              <span class="item-time">{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <p class="item-content">{{ item.reason }}</p>
            <div class="item-foot">
              <span class="text-muted">举报人：{{ item.reporter?.nickname }}</span>
              <div>
                <el-button size="small" type="danger" plain @click="handleReport(item, true)">
                  成立
                </el-button>
                <el-button size="small" plain @click="handleReport(item, false)">不成立</el-button>
              </div>
            </div>
          </div>

          <el-empty v-if="!loading && !reports.length" description="没有待处理举报" />
        </div>
      </el-tab-pane>

      <!-- 争议仲裁 -->
      <el-tab-pane :label="`争议 (${disputes.length})`" name="dispute">
        <div v-loading="loading">
          <div v-for="d in disputes" :key="d.id" class="item">
            <div class="item-head">
              <span class="item-title">{{ d.taskTitle }}</span>
              <el-tag size="small" :type="d.status === 'PENDING' ? 'warning' : 'success'">
                {{ d.status === 'PENDING' ? '待裁决' : '已裁决' }}
              </el-tag>
              <StatusTag v-if="d.result" :value="d.result" kind="judge" size="small" />
              <span class="item-time">{{ formatDateTime(d.createdAt) }}</span>
            </div>

            <div class="dispute-body">
              <div class="party">
                <div class="party-label">委托方</div>
                <div class="party-name">{{ d.publisher?.nickname }}</div>
              </div>
              <div class="party">
                <div class="party-label">接单方</div>
                <div class="party-name">{{ d.taker?.nickname }}</div>
              </div>
              <div class="party">
                <div class="party-label">争议金额</div>
                <div class="party-name accent">{{ d.frozenAmount }} WB</div>
              </div>
            </div>

            <p class="item-content">理由：{{ d.reason }}</p>
            <p v-if="d.remark" class="item-content remark">裁决：{{ d.remark }}</p>

            <div class="item-foot">
              <span class="text-muted">发起人 ID：{{ d.initiatorId }}</span>
              <el-button
                v-if="d.status === 'PENDING'"
                size="small"
                type="primary"
                @click="openJudge(d)"
              >
                裁决
              </el-button>
            </div>
          </div>

          <el-empty v-if="!loading && !disputes.length" description="没有争议" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 裁决弹窗 -->
    <el-dialog v-model="judgeVisible" title="争议裁决" width="560px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="裁决为终局，不可撤销，请谨慎操作"
        class="mb-16"
      />

      <el-form label-position="top">
        <el-form-item label="裁决结果">
          <el-select v-model="judgeForm.result" class="w-full">
            <el-option v-for="o in judgeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="judgeForm.result === 'PARTIAL'" label="给接单方的比例（%）">
          <el-slider v-model="judgeForm.ratio" :min="0" :max="100" show-input />
        </el-form-item>

        <el-form-item label="裁决理由（至少 20 字）" required>
          <el-input
            v-model="judgeForm.remark"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="说明依据与结论，双方都会看到"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="judgeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitJudge">确认裁决</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.card {
  border-radius: 10px;
}

.item {
  padding: 16px;
  margin-bottom: 12px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.item-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.item-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.item-time {
  margin-left: auto;
  font-size: 12px;
  color: #c0c4cc;
}

.item-content {
  margin: 10px 0;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}

.item-content.remark {
  color: #67c23a;
}

.item-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px dashed #ebeef5;
}

.dispute-body {
  display: flex;
  gap: 28px;
  margin-top: 12px;
}

.party-label {
  font-size: 12px;
  color: #909399;
}

.party-name {
  margin-top: 4px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.party-name.accent {
  color: #e6a23c;
}

.w-full {
  width: 100%;
}

.mb-16 {
  margin-bottom: 16px;
}
</style>
