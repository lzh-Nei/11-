<template>
  <!-- 统计卡：标签 + 数值 + 可选趋势脚注（数据态规范：数值 mono 等宽） -->
  <div class="stat-card">
    <span class="sc-label">{{ label }}</span>
    <div class="sc-value">
      {{ display }}<small v-if="unit">{{ unit }}</small>
    </div>
    <span v-if="footnote" class="sc-foot">{{ footnote }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  value: number | string | null
  unit?: string
  footnote?: string
}>()
const display = computed(() => (props.value == null ? '—' : String(props.value)))
</script>

<style scoped>
.stat-card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: 18px var(--aies-gap-lg);
  transition:
    box-shadow var(--aies-t),
    transform var(--aies-t);
}
.stat-card:hover {
  box-shadow: var(--aies-shadow-hover);
  transform: var(--aies-lift);
}
.sc-label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--aies-ink3);
}
.sc-value {
  margin-top: 6px;
  font: 600 26px/1.15 var(--aies-mono, ui-monospace, Consolas, monospace);
  letter-spacing: -0.01em;
  color: var(--aies-ink);
}
.sc-value small {
  font-size: 12px;
  font-weight: 400;
  color: var(--aies-ink3);
  margin-left: 2px;
}
.sc-foot {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--aies-ink3);
}
</style>
