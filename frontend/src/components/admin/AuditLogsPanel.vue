<template>
  <!-- 审计日志（audit:view，UC-40/S-09）：只读检索；detail 为 JSON 快照 -->
  <section class="panel">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="tip"
      title="审计日志仅追加不可篡改（S-09）：本页只读。记录登录、复核、重评与管理端写操作。"
    />

    <div class="toolbar">
      <el-select v-model="moduleFilter" style="width: 140px" @change="reload">
        <el-option label="全部模块" value="" />
        <el-option v-for="m in AUDIT_MODULES" :key="m" :label="permModuleLabel(m)" :value="m" />
      </el-select>
      <el-input
        v-model="actionFilter"
        class="search"
        placeholder="动作关键字（如 REVIEW / UPDATE）"
        clearable
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button :loading="loading" @click="reload">查询</el-button>
      <span class="dim">共 {{ total }} 条</span>
    </div>

    <DataTableShell
      :loading="loading"
      :error="error"
      :rows="rows"
      :total="total"
      :page="page"
      :page-size="size"
      empty-text="没有匹配的审计记录"
      @retry="load"
      @page-change="(p: number) => ((page = p), load())"
    >
      <el-table :data="rows" stripe>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">{{ row.operatorName ?? `#${row.operatorId}` }}</template>
        </el-table-column>
        <el-table-column label="模块" width="100">
          <template #default="{ row }">{{ permModuleLabel(row.module) }}</template>
        </el-table-column>
        <el-table-column label="动作" width="190">
          <template #default="{ row }"
            ><code class="mono">{{ row.action }}</code></template
          >
        </el-table-column>
        <el-table-column label="对象" min-width="160">
          <template #default="{ row }">{{ row.target ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="IP" width="130">
          <template #default="{ row }">{{ row.ip ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="明细" width="100">
          <template #default="{ row }">
            <el-popover v-if="row.detail" placement="left" :width="420" trigger="click">
              <template #reference>
                <el-button size="small" text type="primary">查看</el-button>
              </template>
              <pre class="detail">{{ pretty(row.detail) }}</pre>
            </el-popover>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi, type AdminAuditLog } from '@/api/admin'
import { AUDIT_MODULES, permModuleLabel } from '@/constants/admin'
import { formatDateTime } from '@/utils/format'
import DataTableShell from '@/components/ui/DataTableShell.vue'

const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<AdminAuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const moduleFilter = ref('')
const actionFilter = ref('')

function pretty(detail: string): string {
  try {
    return JSON.stringify(JSON.parse(detail), null, 2)
  } catch {
    return detail
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const params: Parameters<typeof adminApi.auditLogs>[0] = { page: page.value, size }
    if (moduleFilter.value) params.module = moduleFilter.value
    if (actionFilter.value.trim()) params.action = actionFilter.value.trim()
    const result = await adminApi.auditLogs(params)
    rows.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '审计日志加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin-bottom: var(--aies-gap);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: var(--aies-gap);
}
.search {
  max-width: 240px;
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
  margin: 0;
  max-height: 320px;
  overflow: auto;
  font: 11.5px/1.7 var(--aies-mono, monospace);
  color: var(--aies-ink2);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
