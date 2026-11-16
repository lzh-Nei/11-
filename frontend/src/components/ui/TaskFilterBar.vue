<template>
  <!-- 任务状态筛选条：chips 单选（全站状态字典出中文名） -->
  <div class="task-filter-bar">
    <button
      v-for="opt in options"
      :key="opt.value"
      class="filter-chip"
      :class="{ on: modelValue === opt.value }"
      type="button"
      @click="emit('update:modelValue', opt.value)"
    >
      {{ opt.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { statusMeta } from '@/constants/status'

withDefaults(
  defineProps<{ modelValue: string; options?: { value: string; label: string }[] }>(),
  {
    options: () => [
      { value: '', label: '全部' },
      { value: 'PUBLISHED', label: statusMeta('PUBLISHED').label },
      { value: 'DRAFT', label: statusMeta('DRAFT').label },
      { value: 'CLOSED', label: statusMeta('CLOSED').label }
    ]
  }
)
const emit = defineEmits<{ 'update:modelValue': [v: string] }>()
</script>

<style scoped>
.task-filter-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.filter-chip {
  padding: 5px 14px;
  border-radius: var(--aies-radius-pill);
  border: 1px solid var(--aies-border);
  background: var(--aies-surface);
  color: var(--aies-ink2);
  font-size: 13px;
  cursor: pointer;
  transition: all var(--aies-t);
}
.filter-chip:hover {
  border-color: var(--aies-ink4, #a8a29e);
  color: var(--aies-ink);
}
.filter-chip.on {
  background: var(--aies-brand);
  border-color: var(--aies-brand);
  color: #fff;
  font-weight: 600;
}
</style>
