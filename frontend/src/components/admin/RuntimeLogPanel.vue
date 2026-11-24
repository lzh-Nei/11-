<template>
  <!-- 运行日志采集（audit:view，仅管理员）：后端内存滚动缓冲，只读检索 -->
  <section class="panel">
    <div class="head">
      <div>
        <h3>运行日志</h3>
        <p class="hint">
          由后端 Logback 内存采集器实时收集，最多保留
          {{ stats?.capacity ?? 2000 }} 条，写满后自动淘汰最旧记录；不落盘、不外发。
        </p>
      </div>
      <div class="tools">
        <el-switch v-model="autoRefresh" size="small" active-text="自动刷新" />
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        <el-button size="small" type="danger" plain @click="clearLogs">清空缓冲</el-button>
      </div>
    </div>

    <div v-if="stats" class="stat-grid">
      <div class="stat">
        <b>{{ stats.total }}</b>
        <span>累计采集</span>
      </div>
      <div class="stat">
        <b>{{ stats.captured }}<em>/{{ stats.capacity }}</em></b>
        <span>缓冲占用</span>
      </div>
      <div class="stat err">
        <b>{{ stats.errorCount }}</b>
        <span>ERROR</span>
      </div>
      <div class="stat warn">
        <b>{{ stats.warnCount }}</b>
        <span>WARN</span>
      </div>
      <div class="stat">
        <b>{{ stats.capturing ? '采集中' : '已停止' }}</b>
        <span>采集状态</span>
      </div>
      <div class="stat">
        <b>{{ formatDateTime(stats.startedAt) }}</b>
        <span>启动时间</span>
      </div>
    </div>

    <div class="toolbar">
      <el-select v-model="levelFilter" placeholder="全部级别" style="width: 140px" @change="reload">
        <el-option label="全部级别" value="" />
        <el-option label="ERROR 及以上" value="ERROR" />
        <el-option label="WARN 及以上" value="WARN" />
        <el-option label="INFO 及以上" value="INFO" />
        <el-option label="DEBUG 及以上" value="DEBUG" />
      </el-select>
      <el-input
        v-model="loggerFilter"
        class="filter"
        placeholder="Logger 名称"
        clearable
        @keyup.enter="reload"
      />
      <el-input
        v-model="keywordFilter"
        class="filter wide"
        placeholder="消息 / 异常关键字"
        clearable
        @keyup.enter="reload"
      />
      <el-button :loading="loading" @click="reload">查询</el-button>
      <span class="dim">匹配 {{ total }} 条</span>
    </div>

    <DataTableShell
      :loading="loading"
      :error="error"
      :rows="rows"
      :total="total"
      :page="page"
      :page-size="size"
      empty-text="采集缓冲为空或没有匹配的日志"
      scroll-max-height="58vh"
      @retry="load"
      @page-change="(p: number) => ((page = p), load())"
    >
      <el-table :data="rows" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <p class="detail-meta mono">{{ row.logger }} · {{ row.thread || '—' }} · #{{ row.seq }}</p>
              <pre>{{ row.detail || '无异常堆栈' }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.time) }}</template>
        </el-table-column>
        <el-table-column label="级别" width="92">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="levelTagType(row.level)">
              {{ row.level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Logger" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.logger }}</span></template>
        </el-table-column>
        <el-table-column label="线程" width="130" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.thread || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="消息" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">{{ row.message || '—' }}</template>
        </el-table-column>
      </el-table>
    </DataTableShell>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type AdminRuntimeLog, type AdminRuntimeLogStats } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import DataTableShell from '@/components/ui/DataTableShell.vue'

const REFRESH_MS = 5000

const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<AdminRuntimeLog[]>([])
const stats = ref<AdminRuntimeLogStats | null>(null)
const total = ref(0)
const page = ref(1)
const size = 20
const levelFilter = ref('')
const loggerFilter = ref('')
const keywordFilter = ref('')
const autoRefresh = ref(false)
let timer: number | null = null

function levelTagType(level: string): 'danger' | 'warning' | 'success' | 'info' {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  if (level === 'INFO') return 'success'
  return 'info'
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const params: Parameters<typeof adminApi.runtimeLogs>[0] = { page: page.value, size }
    if (levelFilter.value) params.level = levelFilter.value
    if (loggerFilter.value.trim()) params.logger = loggerFilter.value.trim()
    if (keywordFilter.value.trim()) params.keyword = keywordFilter.value.trim()
    const result = await adminApi.runtimeLogs(params)
    rows.value = result.records
    total.value = result.total
    stats.value = result.stats
  } catch (e) {
    error.value = (e as { message?: string })?.message || '运行日志加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

async function clearLogs() {
  try {
    await ElMessageBox.confirm(
      '确认清空当前运行日志缓冲？此操作会写入审计记录，清空后不可恢复。',
      '清空运行日志',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await adminApi.clearRuntimeLogs()
    ElMessage.success('运行日志缓冲已清空')
    reload()
  } catch (e) {
    ElMessage.error((e as { message?: string })?.message || '清空失败，请稍后重试')
  }
}

function stopTimer() {
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
}

watch(autoRefresh, (on) => {
  stopTimer()
  if (on) {
    timer = window.setInterval(load, REFRESH_MS)
  }
})

onMounted(load)
onUnmounted(stopTimer)
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: var(--aies-gap);
}
.head h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--aies-ink);
}
.hint {
  margin: 0;
  max-width: 720px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--aies-ink3);
}
.tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: var(--aies-gap);
}
.stat {
  padding: 12px 14px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  background: var(--aies-surface);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat b {
  font: 600 18px var(--aies-mono, monospace);
  color: var(--aies-ink);
  line-height: 1.2;
}
.stat b em {
  font-style: normal;
  font-size: 12px;
  color: var(--aies-ink3);
}
.stat span {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.stat.err b {
  color: #b91c1c;
}
.stat.warn b {
  color: #b45309;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: var(--aies-gap);
}
.filter {
  width: 180px;
}
.filter.wide {
  width: 240px;
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12px;
}
.detail {
  padding: 10px 18px;
  background: var(--aies-paper);
}
.detail-meta {
  margin: 0 0 8px;
  color: var(--aies-ink3);
}
.detail pre {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--aies-border);
  border-radius: 8px;
  background: var(--aies-surface);
  color: var(--aies-ink2);
  font: 12px/1.6 var(--aies-mono, monospace);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}
@media (max-width: 1180px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 860px) {
  .head {
    flex-wrap: wrap;
  }
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
