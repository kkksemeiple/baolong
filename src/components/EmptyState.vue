<script setup lang="ts">
/**
 * 空状态占位
 * 列表没数据时统一用它，避免每个页面各写一套。
 */
withDefaults(
  defineProps<{
    text?: string
    /** 补充说明 */
    description?: string
    /** 按钮文案，不传则不显示按钮 */
    actionText?: string
  }>(),
  { text: '暂时没有内容', description: '', actionText: '' },
)

defineEmits<{ action: [] }>()
</script>

<template>
  <div class="empty-state">
    <el-icon :size="48" class="icon"><DocumentDelete /></el-icon>
    <p class="text">{{ text }}</p>
    <p v-if="description" class="description">{{ description }}</p>
    <el-button v-if="actionText" type="primary" plain class="action" @click="$emit('action')">
      {{ actionText }}
    </el-button>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #909399;
}

.icon {
  color: #dcdfe6;
}

.text {
  margin: 16px 0 0;
  font-size: 15px;
  color: #606266;
}

.description {
  margin: 6px 0 0;
  font-size: 13px;
}

.action {
  margin-top: 16px;
}
</style>
