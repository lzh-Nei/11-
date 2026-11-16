<template>
  <!-- 页面容器壳：统一 1080/860 两档内容宽 + 暖纸留白；
       bare=嵌入到二级/三级模块内容区时去除页壳留白与宽度限制；
       fill=撑满可用视口高度（模块浏览器用，避免页面底部大块留白） -->
  <div class="page-shell" :class="{ bare, fill }" :style="bare ? undefined : { maxWidth: `${width}px` }">
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ width?: number; bare?: boolean; fill?: boolean }>(), {
  width: 1080,
  bare: false,
  fill: false
})
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
.page-shell.fill {
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  padding-bottom: 24px;
  overflow: hidden;
}
</style>
