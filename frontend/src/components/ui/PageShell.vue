<template>
  <!-- 页面容器壳：统一 1080/860 两档内容宽 + 暖纸留白（六视图共用口径） -->
  <div class="page-shell" :style="{ maxWidth: `${width}px` }">
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ width?: number }>(), { width: 1080 })
</script>

<style scoped>
.page-shell {
  margin: 0 auto;
  padding: var(--aies-gap-lg) 16px 80px;
}
</style>
