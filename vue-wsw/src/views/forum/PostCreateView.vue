<script setup lang="ts">
/**
 * 发帖（FM-02）
 *
 * 前端做基础校验，违禁词检测由后端完成（命中会返回原因）。
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

import { forumApi } from '@/api'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const tagOptions = ['学习交流', '生活求助', '经验分享', '失物招领', '闲聊']

const form = reactive({
  title: '',
  content: '',
  tags: [] as string[],
})

const rules: FormRules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 5, max: 50, message: '标题为 5-50 个字', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入正文', trigger: 'blur' },
    { min: 10, max: 5000, message: '正文为 10-5000 个字', trigger: 'blur' },
  ],
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const post = await forumApi.createPost({
      title: form.title,
      content: form.content,
      tags: form.tags,
    })
    ElMessage.success('发布成功')
    router.replace(`/forum/${post.id}`)
  } catch {
    // 拦截器会提示"内容包含违禁词"等错误
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page-container narrow">
    <el-page-header class="mb-16" @back="router.back()">
      <template #content><span class="header-title">发布帖子</span></template>
    </el-page-header>

    <el-card shadow="never" class="card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="一句话概括你想说的" />
        </el-form-item>

        <el-form-item label="板块">
          <el-select v-model="form.tags" multiple placeholder="选择 1-2 个板块（可选）" class="w-full">
            <el-option v-for="t in tagOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>

        <el-form-item label="正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            maxlength="5000"
            show-word-limit
            placeholder="详细描述你的问题或想分享的内容"
          />
        </el-form-item>

        <div class="actions">
          <el-button @click="router.back()">取消</el-button>
          <el-button type="primary" :loading="loading" @click="handleSubmit">发布</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 780px;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
}

.card {
  border-radius: 10px;
}

.w-full {
  width: 100%;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
