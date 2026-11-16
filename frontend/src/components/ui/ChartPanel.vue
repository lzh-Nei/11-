<template>
  <!-- 图表容器：标题 + 三态（骨架/空/错误）+ 内容插槽——图表类页面统一挂载点 -->
  <section class="chart-panel">
    <header class="cp-head">
      <h3>{{ title }}</h3>
      <span v-if="hint" class="cp-hint">{{ hint }}</span>
    </header>
    <AppSkeleton v-if="loading" :rows="3" :row-height="72" :with-header="false" />
    <ErrorState v-else-if="error" :message="error" @retry="emit('retry')" />
    <EmptyState v-else-if="empty" :description="emptyText" />
    <div v-else class="cp-body"><slot /></div>
  </section>
</template>

<script setup lang="ts">
import AppSkeleton from './AppSkeleton.vue'
import ErrorState from './ErrorState.vue'
import EmptyState from './EmptyState.vue'

withDefaults(
  defineProps<{ title: string; hint?: string; loading?: boolean; error?: string | null; empty?: boolean; emptyText?: string }>(),
  { loading: false, error: null, empty: false, emptyText: '暂无数据' }
)
const emit = defineEmits<{ retry: [] }>()
</script>

<style scoped>
.chart-panel {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
.cp-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: var(--aies-gap);
}
.cp-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--aies-ink);
}
.cp-hint {
  font-size: 12px;
  color: var(--aies-ink3);
}
.cp-body {
  min-height: 200px;
}
</style>
