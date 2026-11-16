<template>
  <!-- 上传区图形交互：upload×5 端点（init/chunk/complete/batch/corrupt-chunk）
       收敛为一个组件——拖拽/点击选文件 → 进度/速度 → 失败重试（upload 铁律） -->
  <div
    class="upload-zone"
    :class="{ dragging }"
    @dragover.prevent="dragging = true"
    @dragleave.prevent="dragging = false"
    @drop.prevent="onDrop"
  >
    <input ref="fileInput" type="file" class="hidden-input" :accept="accept" @change="onPick" />
    <template v-if="!uploading">
      <div class="uz-idle" @click="fileInput?.click()">
        <div class="uz-icon">↑</div>
        <b>{{ idleTitle }}</b>
        <p>拖拽文件到此处，或点击选择 · 支持 {{ accept }}</p>
      </div>
    </template>
    <template v-else>
      <div class="uz-progress">
        <div class="uz-row">
          <b class="uz-name">{{ fileName }}</b>
          <span class="uz-pct">{{ percent }}%</span>
        </div>
        <el-progress :percentage="percent" :show-text="false" :stroke-width="10" />
        <div class="uz-meta">
          <span>{{ sentLabel }} / {{ totalLabel }} · {{ speed }}</span>
          <el-button size="small" text type="danger" @click="emit('cancel')">取消</el-button>
        </div>
        <el-button
          v-if="failed"
          size="small"
          type="primary"
          plain
          class="uz-retry"
          @click="emit('retryUpload')"
        >
          失败重试
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { formatBytes } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    accept?: string
    idleTitle?: string
    uploading?: boolean
    failed?: boolean
    fileName?: string
    percent?: number
    sentBytes?: number
    totalBytes?: number
    bytesPerSecond?: number
  }>(),
  {
    accept: '.doc,.docx,.pdf,.zip',
    idleTitle: '上传作业文件',
    uploading: false,
    failed: false,
    fileName: '',
    percent: 0,
    sentBytes: 0,
    totalBytes: 0,
    bytesPerSecond: 0
  }
)

const emit = defineEmits<{ pick: [file: File]; cancel: []; retryUpload: [] }>()
const fileInput = ref<HTMLInputElement | null>(null)
const dragging = ref(false)

const sentLabel = computed(() => formatBytes(props.sentBytes))
const totalLabel = computed(() => formatBytes(props.totalBytes))
const speed = computed(() =>
  props.bytesPerSecond > 0 ? `${formatBytes(props.bytesPerSecond)}/s` : '—'
)

function onPick(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (f) emit('pick', f)
  ;(e.target as HTMLInputElement).value = ''
}
function onDrop(e: DragEvent) {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) emit('pick', f)
}
</script>

<style scoped>
.upload-zone {
  border: 1.5px dashed var(--aies-border);
  border-radius: var(--aies-radius-lg);
  background: var(--aies-paper);
  transition: border-color var(--aies-t), background var(--aies-t);
}
.upload-zone.dragging {
  border-color: var(--aies-brand);
  background: #f7ede6;
}
.hidden-input {
  display: none;
}
.uz-idle {
  padding: 28px;
  text-align: center;
  cursor: pointer;
}
.uz-icon {
  width: 40px;
  height: 40px;
  margin: 0 auto 8px;
  border-radius: 999px;
  background: #f7ede6;
  color: var(--aies-brand);
  font: 700 20px/40px sans-serif;
}
.uz-idle b {
  font-size: 14px;
  color: var(--aies-ink);
}
.uz-idle p {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--aies-ink3);
}
.uz-progress {
  padding: 18px var(--aies-gap-lg);
}
.uz-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}
.uz-name {
  font-size: 13.5px;
  color: var(--aies-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.uz-pct {
  font: 600 13px var(--aies-mono, monospace);
  color: var(--aies-brand);
}
.uz-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  font-size: 12px;
  color: var(--aies-ink3);
  font-family: var(--aies-mono, monospace);
}
.uz-retry {
  margin-top: 10px;
}
</style>
