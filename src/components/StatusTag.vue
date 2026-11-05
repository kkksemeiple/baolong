<script setup lang="ts">
/**
 * 状态标签
 * 统一把各种枚举渲染成带颜色的 el-tag，避免每个页面重复写映射。
 */
import { computed } from 'vue'
import type { TagType } from '@/utils/constants'
import {
  AUTH_STATUS_META,
  JUDGE_RESULT_META,
  NOTIF_TYPE_META,
  TASK_STATUS_META,
  USER_STATUS_META,
} from '@/utils/constants'

const props = withDefaults(
  defineProps<{
    /** 状态值 */
    value: string
    /** 用哪一套映射 */
    kind?: 'task' | 'user' | 'auth' | 'notif' | 'judge'
    size?: 'large' | 'default' | 'small'
  }>(),
  { kind: 'task', size: 'default' },
)

const meta = computed<{ label: string; type: TagType }>(() => {
  const maps: Record<string, Record<string, { label: string; type: TagType }>> = {
    task: TASK_STATUS_META as unknown as Record<string, { label: string; type: TagType }>,
    user: USER_STATUS_META as unknown as Record<string, { label: string; type: TagType }>,
    auth: AUTH_STATUS_META as unknown as Record<string, { label: string; type: TagType }>,
    notif: NOTIF_TYPE_META as unknown as Record<string, { label: string; type: TagType }>,
    judge: JUDGE_RESULT_META as unknown as Record<string, { label: string; type: TagType }>,
  }
  return maps[props.kind]?.[props.value] ?? { label: props.value, type: 'info' }
})
</script>

<template>
  <el-tag :type="meta.type" :size="size" effect="light">{{ meta.label }}</el-tag>
</template>
