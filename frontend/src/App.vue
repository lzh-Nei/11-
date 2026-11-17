<template>
  <router-view />
</template>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { ElNotification } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { statusMeta } from '@/constants/status'
import { onRealtimeEvent, startRealtime, stopRealtime } from '@/utils/realtime'

interface EvaluationUpdate {
  evaluationId?: number
  submissionId?: number
  fileName?: string
  status?: string
  totalScore?: number | null
}

const auth = useAuthStore()

const unsubscribe = onRealtimeEvent((event) => {
  if (event.type !== 'evaluation.updated') return
  const data = (event.data ?? {}) as EvaluationUpdate
  const label = data.status ? statusMeta(data.status).label : '状态更新'
  const score = data.totalScore == null ? '' : ` · 总分 ${data.totalScore}`
  ElNotification({
    title: '评价状态实时更新',
    message: `${data.fileName || `提交 #${data.submissionId ?? ''}`} · ${label}${score}`,
    type: data.status === 'FAILED' ? 'error' : 'success',
    duration: 5000
  })
})

watch(
  () => auth.accessToken,
  (token) => {
    if (token) startRealtime()
    else stopRealtime()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  unsubscribe()
  stopRealtime()
})
</script>

