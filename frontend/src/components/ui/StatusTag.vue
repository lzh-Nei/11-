<template>
  <!-- 状态标签：全站状态色统一映射表出口（constants/status.ts），禁裸枚举 -->
  <span class="status-tag" :class="`tone-${meta.tone}`">{{ meta.label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { statusMeta } from '@/constants/status'

const props = defineProps<{ status: string | null | undefined }>()
const meta = computed(() => statusMeta(props.status))
</script>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: var(--aies-radius-pill);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.tone-ok {
  background: #dcfce7;
  color: #14532d;
}
.tone-warn {
  background: #fef3c7;
  color: #78350f;
}
.tone-err {
  background: #fee2e2;
  color: #7f1d1d;
}
.tone-teal {
  background: #ccfbf1;
  color: #134e4a;
}
.tone-muted {
  background: #f1efe9;
  color: #57534e;
}
</style>
