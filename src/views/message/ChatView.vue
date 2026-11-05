<script setup lang="ts">
/**
 * 消息会话（MS-01）
 *
 * 左侧会话列表 + 右侧聊天窗口。
 * 也可以通过 /messages?taskId=123 直接打开某个任务的会话（任务详情页跳过来）。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

import { messageApi } from '@/api'
import type { Conversation, Message } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { fromNow } from '@/utils/format'

const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

const conversations = ref<Conversation[]>([])
const activeId = ref<number | null>(null)
const messages = ref<Message[]>([])
const loadingList = ref(false)
const loadingMsgs = ref(false)
const sending = ref(false)
const draft = ref('')
const scrollRef = ref<HTMLElement>()

const active = computed(() => conversations.value.find((c) => c.id === activeId.value) ?? null)

/** 自己发的消息靠右显示 */
function isMine(msg: Message) {
  return msg.fromUserId === userStore.user?.id
}

async function loadConversations() {
  loadingList.value = true
  try {
    conversations.value = await messageApi.fetchConversations()
  } finally {
    loadingList.value = false
  }
}

/** 选中会话：加载消息 + 标记已读 */
async function openConversation(id: number) {
  activeId.value = id
  loadingMsgs.value = true
  try {
    const res = await messageApi.fetchMessages(id, undefined, 50)
    // 接口按时间倒序时前端反转；这里假定返回正序
    messages.value = res.list
    await messageApi.markConversationRead(id)
    const target = conversations.value.find((c) => c.id === id)
    if (target) target.unreadCount = 0
    await appStore.refreshUnread()
    await scrollToBottom()
  } finally {
    loadingMsgs.value = false
  }
}

/** 发送消息 */
async function handleSend() {
  const content = draft.value.trim()
  if (!content || !activeId.value) return

  sending.value = true
  try {
    const msg = await messageApi.sendMessage(activeId.value, { content })
    messages.value.push(msg)
    draft.value = ''

    const target = conversations.value.find((c) => c.id === activeId.value)
    if (target) {
      target.lastMessage = content
      target.lastMessageAt = msg.createdAt
    }
    await scrollToBottom()
  } catch {
    ElMessage.error('发送失败，请重试')
  } finally {
    sending.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  const el = scrollRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 入口：支持 ?taskId= 直接打开会话 */
async function initFromQuery() {
  const taskId = Number(route.query.taskId)
  if (!taskId) {
    if (conversations.value.length) await openConversation(conversations.value[0]!.id)
    return
  }
  try {
    const conv = await messageApi.openConversation(taskId)
    if (!conversations.value.some((c) => c.id === conv.id)) {
      conversations.value.unshift(conv)
    }
    await openConversation(conv.id)
  } catch {
    ElMessage.warning('无法打开该任务的会话')
  }
}

onMounted(async () => {
  await loadConversations()
  await initFromQuery()
})

watch(
  () => route.query.taskId,
  () => initFromQuery(),
)
</script>

<template>
  <div class="page-container chat-page">
    <div class="chat-shell">
      <!-- 左：会话列表 -->
      <aside class="sidebar">
        <div class="sidebar-head">
          <span>消息</span>
          <el-button text :icon="'Refresh'" size="small" @click="loadConversations" />
        </div>

        <div v-loading="loadingList" class="conv-list">
          <div
            v-for="c in conversations"
            :key="c.id"
            class="conv"
            :class="{ active: c.id === activeId }"
            @click="openConversation(c.id)"
          >
            <el-avatar :size="38" :src="c.peer?.avatar ?? undefined">
              {{ c.peer?.nickname?.slice(0, 1) ?? '?' }}
            </el-avatar>
            <div class="conv-main">
              <div class="conv-top">
                <span class="conv-name">{{ c.peer?.nickname }}</span>
                <span class="conv-time">{{ fromNow(c.lastMessageAt) }}</span>
              </div>
              <div class="conv-bottom">
                <span class="conv-last ellipsis">{{ c.lastMessage || '暂无消息' }}</span>
                <el-badge
                  v-if="c.unreadCount"
                  :value="c.unreadCount"
                  class="conv-badge"
                />
              </div>
              <div class="conv-task ellipsis">任务：{{ c.taskTitle }}</div>
            </div>
          </div>

          <EmptyState
            v-if="!loadingList && !conversations.length"
            text="还没有会话"
            description="接单或被接单后，会自动生成与对方的会话"
          />
        </div>
      </aside>

      <!-- 右：聊天窗口 -->
      <section class="pane">
        <template v-if="active">
          <header class="pane-head">
            <div class="peer">
              <el-avatar :size="32" :src="active.peer?.avatar ?? undefined">
                {{ active.peer?.nickname?.slice(0, 1) ?? '?' }}
              </el-avatar>
              <div>
                <div class="peer-name">{{ active.peer?.nickname }}</div>
                <div class="peer-sub">关于任务：{{ active.taskTitle }}</div>
              </div>
            </div>
          </header>

          <div ref="scrollRef" v-loading="loadingMsgs" class="messages">
            <div
              v-for="m in messages"
              :key="m.id"
              class="msg"
              :class="{ mine: isMine(m) }"
            >
              <el-avatar v-if="!isMine(m)" :size="30" :src="active.peer?.avatar ?? undefined">
                {{ active.peer?.nickname?.slice(0, 1) ?? '?' }}
              </el-avatar>
              <div class="bubble-wrap">
                <div class="bubble">{{ m.content }}</div>
                <div class="msg-time">{{ fromNow(m.createdAt) }}</div>
              </div>
            </div>

            <EmptyState v-if="!loadingMsgs && !messages.length" text="还没有聊天记录，打个招呼吧" />
          </div>

          <footer class="composer">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="2"
              resize="none"
              maxlength="500"
              placeholder="输入消息，Enter 发送，Shift + Enter 换行"
              @keydown.enter.exact.prevent="handleSend"
            />
            <el-button type="primary" :loading="sending" @click="handleSend">发送</el-button>
          </footer>
        </template>

        <EmptyState v-else text="选择一个会话开始聊天" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  padding-bottom: 24px;
}

.chat-shell {
  display: flex;
  height: calc(100vh - 200px);
  min-height: 480px;
  overflow: hidden;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.05);
}

/* 左侧 */
.sidebar {
  display: flex;
  flex-direction: column;
  width: 300px;
  border-right: 1px solid #ebeef5;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid #ebeef5;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
}

.conv {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f7fa;
  transition: background 0.2s;
}

.conv:hover {
  background: #fafcff;
}

.conv.active {
  background: #ecf5ff;
}

.conv-main {
  flex: 1;
  min-width: 0;
}

.conv-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.conv-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.conv-time {
  font-size: 11px;
  color: #c0c4cc;
  white-space: nowrap;
}

.conv-bottom {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.conv-last {
  flex: 1;
  font-size: 12px;
  color: #909399;
}

.conv-task {
  margin-top: 4px;
  font-size: 11px;
  color: #b1b3b8;
}

/* 右侧 */
.pane {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.pane-head {
  padding: 12px 18px;
  border-bottom: 1px solid #ebeef5;
}

.peer {
  display: flex;
  align-items: center;
  gap: 10px;
}

.peer-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.peer-sub {
  font-size: 12px;
  color: #909399;
}

.messages {
  flex: 1;
  padding: 18px;
  overflow-y: auto;
  background: #fafbfc;
}

.msg {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.msg.mine {
  flex-direction: row-reverse;
}

.bubble-wrap {
  max-width: 70%;
}

.bubble {
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  word-break: break-word;
  white-space: pre-wrap;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.msg.mine .bubble {
  color: #fff;
  background: #409eff;
}

.msg-time {
  margin-top: 4px;
  font-size: 11px;
  color: #c0c4cc;
}

.msg.mine .msg-time {
  text-align: right;
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
}

@media (max-width: 820px) {
  .sidebar {
    width: 110px;
  }

  .conv-task,
  .conv-time {
    display: none;
  }
}
</style>
