<script setup lang="ts">
/** 404 页面 */
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="code">404</div>
    <p class="text">页面不存在或已被删除</p>
    <div class="actions">
      <el-button type="primary" @click="router.push('/tasks')">回到任务大厅</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.code {
  font-size: 96px;
  font-weight: 700;
  color: #dcdfe6;
  line-height: 1;
}

.text {
  margin: 16px 0 24px;
  font-size: 15px;
  color: #909399;
}

.actions {
  display: flex;
  gap: 12px;
}
</style>
