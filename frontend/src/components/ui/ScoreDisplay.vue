<template>
  <!-- 分数渲染：分数环 + 等级色（禁裸数字）——size 可选 mini/normal -->
  <div class="score-display" :class="size">
    <svg :width="diameter" :height="diameter" viewBox="0 0 72 72">
      <circle cx="36" cy="36" :r="radius" fill="none" stroke="#edebe6" stroke-width="8" />
      <circle
        cx="36"
        cy="36"
        :r="radius"
        fill="none"
        :stroke="levelColor"
        stroke-width="8"
        stroke-linecap="round"
        :stroke-dasharray="dash"
        transform="rotate(-90 36 36)"
        class="ring"
      />
      <text x="36" y="40" text-anchor="middle" class="score-text">{{ display }}</text>
    </svg>
    <span v-if="showLevel" class="level" :style="{ color: levelColor }">{{ levelLabel }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { scoreLevel, SCORE_LEVEL_LABEL } from '@/utils/format'

const props = withDefaults(defineProps<{ score: number | null; size?: 'mini' | 'normal'; showLevel?: boolean }>(), {
  size: 'normal',
  showLevel: true
})

const radius = 30
const circumference = 2 * Math.PI * radius
const valid = computed(() => props.score != null && !isNaN(Number(props.score)))
const display = computed(() => (valid.value ? String(Math.round(Number(props.score) * 10) / 10) : '—'))
const level = computed(() => scoreLevel(props.score))
const levelLabel = computed(() => (level.value ? SCORE_LEVEL_LABEL[level.value] : ''))
const levelColor = computed(() => {
  switch (level.value) {
    case 'excellent':
      return '#15803d'
    case 'good':
      return '#0f766e'
    case 'pass':
      return '#b45309'
    case 'low':
      return '#b91c1c'
    default:
      return '#a8a29e'
  }
})
const dash = computed(() => {
  if (!valid.value) return '0 ' + circumference
  const pct = Math.min(Math.max(Number(props.score) / 100, 0), 1)
  return `${(pct * circumference).toFixed(1)} ${circumference.toFixed(1)}`
})
const diameter = computed(() => (props.size === 'mini' ? 44 : 72))
</script>

<style scoped>
.score-display {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.score-text {
  font: 600 18px var(--aies-mono, ui-monospace, Consolas, monospace);
  fill: var(--aies-ink);
}
.mini .score-text {
  font-size: 13px;
}
.level {
  font-size: 12.5px;
  font-weight: 600;
}
.ring {
  transition: stroke-dasharray var(--aies-t);
}
</style>
