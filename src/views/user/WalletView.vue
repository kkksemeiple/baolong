<script setup lang="ts">
/**
 * 我的钱包（UC-05 / PF-01）
 *
 * 展示可用余额、冻结余额、欠款、总资产，
 * 以及冻结明细和收支流水（不可变账本）。
 * 注意：本页不提供充值、提现、转账入口——屋币只在平台内流转。
 */
import { onMounted, ref } from 'vue'

import { userApi } from '@/api'
import type { FrozenItem, TransType, WalletAccount, WalletTransaction } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { BIZ_TYPE_META, TRANS_TYPE_META } from '@/utils/constants'
import { formatCoin, formatDateTime } from '@/utils/format'

const wallet = ref<WalletAccount | null>(null)
const transactions = ref<WalletTransaction[]>([])
const frozenItems = ref<FrozenItem[]>([])
const loading = ref(false)
const typeFilter = ref<TransType | ''>('')

const typeOptions = Object.entries(TRANS_TYPE_META).map(([value, meta]) => ({
  value,
  label: meta.label,
}))

async function loadWallet() {
  wallet.value = await userApi.fetchWallet()
}

async function loadTransactions() {
  const res = await userApi.fetchTransactions({
    type: (typeFilter.value || undefined) as TransType | undefined,
    size: 30,
  })
  transactions.value = res.list
}

async function loadFrozen() {
  frozenItems.value = await userApi.fetchFrozenItems()
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadWallet(), loadTransactions(), loadFrozen()])
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div v-loading="loading" class="page-container narrow">
    <h1 class="page-title">我的钱包</h1>

    <!-- 余额卡片 -->
    <el-card v-if="wallet" shadow="never" class="balance-card">
      <div class="balance-main">
        <div class="label">可用余额</div>
        <div class="value">{{ wallet.available }} <span class="unit">WB</span></div>
        <div class="hint">屋币仅限平台内结算，不可充值、提现或转让</div>
      </div>

      <div class="balance-side">
        <div class="side-item">
          <span class="side-label">冻结中</span>
          <span class="side-value">{{ wallet.frozen }} WB</span>
        </div>
        <div class="side-item">
          <span class="side-label">欠款</span>
          <span class="side-value" :class="{ danger: wallet.debt > 0 }">{{ wallet.debt }} WB</span>
        </div>
        <div class="side-item">
          <span class="side-label">总资产</span>
          <span class="side-value">{{ wallet.total }} WB</span>
        </div>
      </div>
    </el-card>

    <!-- 冻结明细 -->
    <el-card shadow="never" class="card mt-16">
      <template #header>
        <div class="card-head">
          <span class="card-title">冻结明细</span>
          <span class="text-muted">共 {{ frozenItems.length }} 笔</span>
        </div>
      </template>

      <div v-if="frozenItems.length" class="frozen-list">
        <div v-for="f in frozenItems" :key="f.taskId" class="frozen-row">
          <span class="ellipsis">{{ f.taskTitle }}</span>
          <span class="frozen-amount">{{ formatCoin(f.amount) }}</span>
          <span class="text-muted">{{ formatDateTime(f.frozenAt) }}</span>
        </div>
      </div>
      <EmptyState v-else text="没有冻结中的屋币" />
    </el-card>

    <!-- 收支流水 -->
    <el-card shadow="never" class="card mt-16">
      <template #header>
        <div class="card-head">
          <span class="card-title">收支流水</span>
          <el-select
            v-model="typeFilter"
            placeholder="全部类型"
            clearable
            size="small"
            class="w-140"
            @change="loadTransactions"
          >
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </div>
      </template>

      <div v-if="transactions.length" class="tx-list">
        <div v-for="tx in transactions" :key="tx.id" class="tx-row">
          <div class="tx-left">
            <div class="tx-type">
              {{ TRANS_TYPE_META[tx.type]?.label ?? tx.type }}
              <el-tag size="small" type="info" effect="plain">
                {{ BIZ_TYPE_META[tx.bizType] ?? tx.bizType }}
              </el-tag>
            </div>
            <div class="tx-sub">
              {{ tx.bizTitle || tx.remark }}
              <span class="dot">·</span>
              {{ formatDateTime(tx.createdAt) }}
            </div>
          </div>

          <div class="tx-right">
            <div class="tx-amount" :class="tx.amount > 0 ? 'income' : 'expense'">
              {{ formatCoin(tx.amount, true) }}
            </div>
            <div class="tx-balance">余额 {{ tx.balanceAfter }}</div>
          </div>
        </div>
      </div>

      <EmptyState v-else text="还没有收支记录" />
    </el-card>
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

.balance-card {
  border-radius: 10px;
  background: linear-gradient(135deg, #ecf5ff, #fff);
}

.balance-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.label {
  font-size: 13px;
  color: #909399;
}

.value {
  margin: 6px 0;
  font-size: 36px;
  font-weight: 700;
  color: #409eff;
}

.unit {
  font-size: 16px;
  font-weight: 500;
}

.hint {
  font-size: 12px;
  color: #c0c4cc;
}

.balance-side {
  display: flex;
  gap: 28px;
}

.side-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.side-label {
  font-size: 12px;
  color: #909399;
}

.side-value {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.side-value.danger {
  color: #f56c6c;
}

.card {
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

.w-140 {
  width: 140px;
}

.frozen-list {
  display: flex;
  flex-direction: column;
}

.frozen-row {
  display: grid;
  grid-template-columns: 1fr auto 160px;
  gap: 16px;
  align-items: center;
  padding: 10px 0;
  font-size: 13px;
  border-bottom: 1px solid #f5f7fa;
}

.frozen-amount {
  font-weight: 600;
  color: #e6a23c;
}

.tx-list {
  display: flex;
  flex-direction: column;
}

.tx-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #f5f7fa;
}

.tx-type {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.tx-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.dot {
  margin: 0 4px;
  color: #dcdfe6;
}

.tx-right {
  text-align: right;
}

.tx-amount {
  font-size: 15px;
  font-weight: 700;
}

.tx-amount.income {
  color: #67c23a;
}

.tx-amount.expense {
  color: #f56c6c;
}

.tx-balance {
  margin-top: 2px;
  font-size: 12px;
  color: #c0c4cc;
}

.mt-16 {
  margin-top: 16px;
}

@media (max-width: 640px) {
  .balance-card :deep(.el-card__body) {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
