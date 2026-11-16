<template>
  <!-- 页面容器壳：统一 1080/860 两档内容宽 + 暖纸留白；
       bare=嵌入到二级/三级模块内容区时去除页壳留白与宽度限制 -->
  <div class="page-shell" :class="{ bare }" :style="bare ? undefined : { maxWidth: `${width}px` }">
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ width?: number; bare?: boolean }>(), { width: 1080, bare: false })
</script>

<style scoped>
.page-shell {
  margin: 0 auto;
  padding: var(--aies-gap-lg) 16px 80px;
}
.page-shell.bare {
  margin: 0;
  padding: 0;
}
</style>
