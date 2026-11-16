<template>
  <!-- 文件中心（WP8-B6）：分片上传产物清单 + 流式直下（Range 断点续传）
       数据真身：GET /api/files（FileController） → GET /api/download/{id}（DownloadController） -->
  <PageShell>
    <PageHeader title="文件中心" subtitle="查看已上传文件并下载">
      <el-button size="small" :loading="loading" @click="load">刷新清单</el-button>
    </PageHeader>

    <section class="stat-row">
      <div class="stat-chip">
        <span class="stat-label">文件数</span>
        <b class="stat-value">{{ rows.length }}</b>
      </div>
      <div class="stat-chip">
        <span class="stat-label">总大小</span>
        <b class="stat-value">{{ formatBytes(totalSize) }}</b>
      </div>
      <div class="stat-chip">
        <span class="stat-label">最近上传</span>
        <b class="stat-value">{{ latestUpload ? formatRelative(latestUpload) : '—' }}</b>
      </div>
    </section>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="source-tip"
      title="这里列出的是上传到文件存储中的文件。作业原件请到「提交档案 → 详情」下载。"
    />

    <div class="toolbar">
      <el-input v-model="keyword" class="search" placeholder="按文件名筛选" clearable />
      <el-select v-model="sortKey" class="sort" style="width: 150px">
        <el-option label="按上传时间" value="time" />
        <el-option label="按文件名" value="name" />
        <el-option label="按大小" value="size" />
      </el-select>
    </div>

    <DataTableShell
      :loading="loading"
      :error="error"
      :rows="pageRows"
      :total="filtered.length"
      :page="page"
      :page-size="size"
      empty-text="还没有上传记录"
      empty-action-text="去任务列表提交作业"
      @retry="load"
      @page-change="(p: number) => (page = p)"
      @empty-action="router.push('/tasks')"
    >
      <table class="file-table">
        <thead>
          <tr>
            <th>文件名</th>
            <th style="width: 110px">大小</th>
            <th style="width: 160px">上传时间</th>
            <th style="width: 120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="f in pageRows" :key="f.id">
            <td>
              <span class="file-name" :title="f.name">{{ f.name }}</span>
            </td>
            <td class="mono dim">{{ formatBytes(f.size) }}</td>
            <td>
              <el-tooltip :content="formatDateTime(f.uploadTime)" placement="top">
                <span>{{ formatRelative(f.uploadTime) }}</span>
              </el-tooltip>
            </td>
            <td>
              <el-button
                size="small"
                type="primary"
                plain
                :loading="downloadingId === f.id"
                @click="download(f)"
              >
                下载
              </el-button>
            </td>
          </tr>
        </tbody>
      </table>
    </DataTableShell>

    <p class="foot-note">
      文件下载支持断点续传；网络中断后可继续下载。
    </p>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fileApi, type FileMeta } from '@/api/file'
import { downloadApi } from '@/api/download'
import { formatBytes, formatDateTime, formatRelative } from '@/utils/format'
import { saveBlob } from '@/utils/file'
import { toast } from '@/utils/toast'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'

const router = useRouter()

const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<FileMeta[]>([])
const keyword = ref('')
const sortKey = ref<'time' | 'name' | 'size'>('time')
const page = ref(1)
const size = 10
const downloadingId = ref<string | null>(null)

const totalSize = computed(() => rows.value.reduce((a, f) => a + (Number(f.size) || 0), 0))
const latestUpload = computed(() => rows.value[0]?.uploadTime ?? null)

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const list = kw ? rows.value.filter((f) => (f.name ?? '').toLowerCase().includes(kw)) : [...rows.value]
  if (sortKey.value === 'name') list.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  if (sortKey.value === 'size') list.sort((a, b) => b.size - a.size)
  if (sortKey.value === 'time') {
    list.sort((a, b) => String(b.uploadTime).localeCompare(String(a.uploadTime)))
  }
  return list
})
const pageRows = computed(() => filtered.value.slice((page.value - 1) * size, page.value * size))

// 筛选/排序变化后回到第一页，避免停在越界页看到空表
watch([keyword, sortKey], () => {
  page.value = 1
})

async function load() {
  loading.value = true
  error.value = null
  try {
    rows.value = await fileApi.list()
    page.value = 1
  } catch (e) {
    error.value = (e as { message?: string })?.message || '文件清单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** D-02 直下：Blob 落地（同源流式通道，不经 axios 拦截器） */
async function download(f: FileMeta) {
  downloadingId.value = f.id
  try {
    const blob = await downloadApi.direct(f.id)
    saveBlob(blob, f.name || `${f.id}.bin`)
    toast.success(`已下载：${f.name}`)
  } catch (e) {
    toast.error((e as { message?: string })?.message || '下载失败', '请稍后重试')
  } finally {
    downloadingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.stat-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: var(--aies-gap);
}
.stat-chip {
  flex: 1;
  min-width: 150px;
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat-label {
  font-size: 12px;
  color: var(--aies-ink3);
}
.stat-value {
  font: 600 18px var(--aies-mono, monospace);
  color: var(--aies-ink);
}
.source-tip {
  margin-bottom: var(--aies-gap);
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: var(--aies-gap);
}
.search {
  max-width: 320px;
}
.file-table {
  width: 100%;
  border-collapse: collapse;
}
.file-table th {
  position: sticky;
  top: 0;
  z-index: 2;
  text-align: left;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--aies-ink3);
  padding: 10px 14px;
  background: var(--aies-paper);
  border-bottom: 1px solid var(--aies-border);
}
.file-table td {
  padding: 11px 14px;
  border-bottom: 1px solid var(--aies-border);
  font-size: 13.5px;
  color: var(--aies-ink2);
}
.file-table tbody tr:last-child td {
  border-bottom: none;
}
.file-table tbody tr:hover td {
  background: var(--aies-paper);
}
.file-name {
  display: inline-block;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--aies-ink);
  font-weight: 500;
}
.mono {
  font-family: var(--aies-mono, monospace);
}
.dim {
  color: var(--aies-ink3);
}
.foot-note {
  margin-top: var(--aies-gap);
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
.foot-note code {
  font-family: var(--aies-mono, monospace);
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  border-radius: 4px;
  padding: 1px 5px;
}
</style>
