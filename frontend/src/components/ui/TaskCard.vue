<template>
  <!-- 任务卡：标题+状态标签+指标权重 chips+截止徽标+主操作（列表铁律：整卡可点进详情） -->
  <div class="task-card" @click="emit('open')">
    <div class="tc-head">
      <b class="tc-title">{{ task.title }}</b>
      <StatusTag :status="task.status" />
    </div>
    <p v-if="task.description" class="tc-desc">{{ task.description }}</p>
    <div class="tc-metrics">
      <span v-for="m in task.metrics" :key="m.id" class="metric-chip">
        {{ m.metricName }} <b>{{ m.weight }}%</b>
      </span>
    </div>
    <div class="tc-foot">
      <DeadlineBadge :deadline="task.deadline" :status="task.status" />
      <span class="tc-id mono">ID {{ task.id }}</span>
    </div>
    <div v-if="$slots.default" class="tc-action" @click.stop>
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { TaskInfo } from '@/api/task'
import StatusTag from './StatusTag.vue'
import DeadlineBadge from './DeadlineBadge.vue'

defineProps<{ task: TaskInfo }>()
const emit = defineEmits<{ open: [] }>()
</script>

<style scoped>
.task-card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
  cursor: pointer;
  transition: box-shadow var(--aies-t), transform var(--aies-t);
}
.task-card:hover {
  box-shadow: var(--aies-shadow-hover);
  transform: var(--aies-lift);
}
.tc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.tc-title {
  font-size: 15px;
  color: var(--aies-ink);
}
.tc-desc {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--aies-ink2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.tc-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.metric-chip {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: var(--aies-radius-pill);
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  color: var(--aies-ink2);
}
.metric-chip b {
  color: var(--aies-brand);
  font-family: var(--aies-mono, monospace);
}
.tc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.tc-id {
  font-size: 12px;
  color: var(--aies-ink3);
}
.tc-action {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--aies-border);
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
