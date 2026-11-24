<template>
  <router-view />
</template>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { ElNotification } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useNotificationStore } from '@/stores/notification'
import { statusMeta } from '@/constants/status'
import { onRealtimeEvent, startRealtime, stopRealtime } from '@/utils/realtime'

interface EvaluationUpdate {
  evaluationId?: number
  submissionId?: number
  fileName?: string
  status?: string
  totalScore?: number | null
}

interface ChatEvent {
  id?: number
  senderId?: number
  receiverId?: number
  content?: string
}

const auth = useAuthStore()
const chatStore = useChatStore()
const notificationStore = useNotificationStore()

const unsubscribe = onRealtimeEvent((event) => {
  if (event.type === 'evaluation.updated') {
    const data = (event.data ?? {}) as EvaluationUpdate
    const label = data.status ? statusMeta(data.status).label : '状态更新'
    const score = data.totalScore == null ? '' : ` · 总分 ${data.totalScore}`
    ElNotification({
      title: '评价状态实时更新',
      message: `${data.fileName || `提交 #${data.submissionId ?? ''}`} · ${label}${score}`,
      type: data.status === 'FAILED' ? 'error' : 'success',
      duration: 5000
    })
    return
  }
  if (event.type === 'notification.created') {
    const data = (event.data ?? {}) as {
      type?: string
      title?: string
      content?: string
      unread?: number
    }
    notificationStore.setUnread(Number(data.unread ?? 0))
    // 聊天消息已有独立 toast，通知中心仅更新角标避免重复打扰
    if (data.type !== 'CHAT') {
      ElNotification({
        title: data.title || '新通知',
        message: data.content || '',
        type: 'info',
        duration: 5000
      })
    }
    return
  }
  if (event.type !== 'chat.message') return
  // 全局新消息提醒：聊天页只负责当前会话已读，未读计数与 toast 在此统一处理
  const message = (event.data ?? {}) as ChatEvent
  const myId = auth.me?.id
  if (!message.id || !myId || message.receiverId !== myId) return
  const peerId = message.senderId ?? 0
  if (peerId === chatStore.activePeerId) return
  chatStore.addUnread(peerId)
  const text = (message.content ?? '').slice(0, 60)
  ElNotification({
    title: '收到新消息',
    message: text,
    type: 'info',
    duration: 5000
  })
})

watch(
  () => auth.accessToken,
  (token) => {
    if (token) {
      startRealtime()
      void notificationStore.refresh()
    } else {
      stopRealtime()
      chatStore.reset()
      notificationStore.reset()
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  unsubscribe()
  stopRealtime()
})
</script>

