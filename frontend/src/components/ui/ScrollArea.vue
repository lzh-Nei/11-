<template>
  <!-- 统一滚动区（WP8-C3）：长列表/矩阵只在组件内部滚动，滚动链不传给外层页面。
       maxHeight：限高（默认 62vh）；fill：作为 flex 子项占满剩余高度（父级需 flex 列布局）；
       passthrough：嵌入到已有滚动容器时不建立新的滚动上下文（避免吞掉滚轮事件）。 -->
  <div class="scroll-area" :class="{ fill, passthrough }" :style="areaStyle">
    <slot />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    maxHeight?: string
    fill?: boolean
    padded?: boolean
    passthrough?: boolean
  }>(),
  { maxHeight: '62vh', fill: false, padded: false, passthrough: false }
)

const areaStyle = computed<Record<string, string>>(() => {
  const style: Record<string, string> = {}
  if (!props.fill && !props.passthrough) style.maxHeight = props.maxHeight
  if (props.padded && !props.passthrough) style.paddingRight = '6px'
  return style
})
</script>

<style scoped>
.scroll-area {
  overflow: auto;
  overscroll-behavior: auto;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: var(--aies-border) transparent;
  -webkit-overflow-scrolling: touch;
}
.scroll-area.fill {
  flex: 1;
  min-height: 0;
}
.scroll-area.passthrough {
  overflow: visible;
  overscroll-behavior: auto;
  scrollbar-gutter: auto;
}
.scroll-area::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
.scroll-area::-webkit-scrollbar-track {
  background: transparent;
}
.scroll-area::-webkit-scrollbar-thumb {
  background: var(--aies-border);
  border-radius: 999px;
}
.scroll-area::-webkit-scrollbar-thumb:hover {
  background: var(--aies-ink3);
}
</style>
