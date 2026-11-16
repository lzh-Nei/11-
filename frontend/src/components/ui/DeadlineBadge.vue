<template>
  <!-- 截止时间徽标：格式化日期 + 状态语义（已截止红 / 临近琥珀 / 剩余时间灰） -->
  <span class="deadline-badge" :class="cls">
    {{ formatDateTime(deadline) }}
    <i v-if="state === 'overdue'" class="badge-flag">已截止</i>
    <i v-else-if="state === 'urgent'" class="badge-flag">临近</i>
    <i v-else-if="state === 'soon'" class="badge-flag muted">{{ remainText }}</i>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ deadline: string; status?: string }>()

const state = computed<'overdue' | 'urgent' | 'soon' | 'normal'>(() => {
  if (props.status === 'CLOSED') return 'overdue'
  const t = new Date(props.deadline).getTime()
  if (isNaN(t) || t < Date.now()) return 'overdue'
  const diff = t - Date.now()
  if (diff < 24 * 3600 * 1000) return 'urgent'
  if (diff < 3 * 24 * 3600 * 1000) return 'soon'
  return 'normal'
})

const cls = computed(() => `d-${state.value}`)

const remainText = computed(() => {
  if (state.value !== 'soon') return ''
  const h = Math.floor((new Date(props.deadline).getTime() - Date.now()) / 3600000)
  return h < 48 ? `剩 ${h} 小时` : `剩 ${Math.floor(h / 24)} 天`
})
</script>

<style scoped>
.deadline-badge {
  font-family: var(--aies-mono, monospace);
  font-size: 13px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.d-overdue {
  color: #b91c1c;
}
.d-urgent {
  color: #b45309;
}
.d-normal,
.d-soon {
  color: var(--aies-ink2);
}
.badge-flag {
  font-style: normal;
  font-size: 11.5px;
  font-weight: 600;
  padding: 1px 8px;
  border-radius: var(--aies-radius-pill);
}
.d-overdue .badge-flag {
  background: #fee2e2;
  color: #7f1d1d;
}
.d-urgent .badge-flag {
  background: #fef3c7;
  color: #78350f;
}
.badge-flag.muted {
  background: var(--aies-paper);
  color: var(--aies-ink3);
  border: 1px solid var(--aies-border);
}
</style>
