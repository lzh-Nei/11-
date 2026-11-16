<template>
  <!-- 页头模式（布局基因）：标题 + 副题 + 操作区——全站每页统一 -->
  <header class="page-header">
    <div class="ph-text">
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.default" class="ph-actions"><slot /></div>
  </header>
</template>

<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--aies-gap);
  margin-bottom: var(--aies-gap-lg);
}
.ph-text h1 {
  font-size: 22px;
  font-weight: 600;
  color: var(--aies-ink);
  margin: 0;
  letter-spacing: -0.01em;
}
.ph-text p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--aies-ink3);
}
.ph-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
