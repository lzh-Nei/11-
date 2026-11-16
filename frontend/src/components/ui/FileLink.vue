<template>
  <!-- 文件链接：文件名 + 格式化大小 + 可选下载动作（S-04 签名URL 跳转由调用方处理） -->
  <span class="file-link" :title="name">
    <b class="fl-name">{{ name }}</b>
    <span class="fl-size">{{ formatBytes(size) }}</span>
    <el-button v-if="downloadable" link type="primary" size="small" @click="emit('download')">下载</el-button>
  </span>
</template>

<script setup lang="ts">
import { formatBytes } from '@/utils/format'

withDefaults(defineProps<{ name: string; size?: number; downloadable?: boolean }>(), { downloadable: true })
const emit = defineEmits<{ download: [] }>()
</script>

<style scoped>
.file-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.fl-name {
  font-size: 13px;
  color: var(--aies-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fl-size {
  font: 12px var(--aies-mono, monospace);
  color: var(--aies-ink3);
  flex: none;
}
</style>
