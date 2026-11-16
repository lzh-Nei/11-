<template>
  <!-- 评语结构化：AI 评语按条渲染（支持 1./一、/• 行拆分），无结构则整段；
       长评语在组件内滚动（WP8-C3），不把整页顶长 -->
  <div class="suggestion-list">
    <ScrollArea :max-height="maxHeight" padded>
      <div v-if="items.length" class="items">
        <div v-for="(item, i) in items" :key="i" class="item">
          <span class="idx">{{ i + 1 }}</span>
          <span class="txt">{{ item }}</span>
        </div>
      </div>
      <p v-else class="whole">{{ comment }}</p>
    </ScrollArea>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ScrollArea from './ScrollArea.vue'

const props = withDefaults(defineProps<{ comment: string | null | undefined; maxHeight?: string }>(), {
  maxHeight: '360px'
})

const items = computed(() => {
  const c = (props.comment ?? '').trim()
  if (!c) return []
  // 按 换行 / 序号前缀（1. 2． 一、 ② • -）拆条
  const parts = c
    .split(/\n+|(?=(?:^|\s)(?:[1-9][.、．]|[一二三四五六七八九十]+[、.])|(?<=\s)[•-]\s)/g)
    .map((s) => s.trim().replace(/^[•-]\s*/, ''))
    .filter((s) => s.length > 1)
  return parts.length > 1 ? parts : []
})
</script>

<style scoped>
.suggestion-list {
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  padding: 14px 16px;
}
.items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--aies-ink);
}
.idx {
  flex: none;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: #f7ede6;
  color: var(--aies-brand);
  font: 600 11px/20px var(--aies-mono, monospace);
  text-align: center;
}
.whole {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--aies-ink);
  white-space: pre-wrap;
}
</style>
