<template>
  <!-- 加载态：骨架屏（四态铁律①，禁白屏转圈）——rows 行骨架 + 可选头部骨架 -->
  <div class="app-skeleton" aria-busy="true">
    <el-skeleton animated :rows="rows" :loading="true">
      <template #template>
        <el-skeleton-item v-if="withHeader" variant="text" style="width: 30%; height: 22px" />
        <div style="height: 12px" />
        <el-skeleton-item
          v-for="i in rows"
          :key="i"
          variant="rect"
          :style="{ width: '100%', height: rowHeight + 'px', marginTop: '10px', borderRadius: '8px' }"
        />
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ rows?: number; rowHeight?: number; withHeader?: boolean }>(), {
  rows: 4,
  rowHeight: 56,
  withHeader: true
})
</script>

<style scoped>
.app-skeleton {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
</style>
