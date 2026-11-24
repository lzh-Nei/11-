<template>
  <!-- 审计日志（audit:view，仅管理员）：概览 + 多条件检索；detail 为 JSON 快照 -->
  <section class="panel">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="tip"
      title="审计日志用于追溯关键操作，只读不可修改；清空运行日志等敏感操作同样会在此留痕。"
    />

    <div v-if="stats" class="overview">
      <div class="metrics">
        <div class="metric">
          <b>{{ stats.total }}</b>
          <span>累计审计</span>
        </div>
        <div class="metric">
          <b>{{ stats.today }}</b>
          <span>今日新增</span>
        </div>
        <div class="metric">
          <b>{{ stats.last24h }}</b>
          <span>近24小时</span>
        </div>
      </div>
      <div class="modules">
        <span v-for="m in stats.byModule" :key="m.label" class="chip">
          {{ permModuleLabel(m.label) }} <em>{{ m.count }}</em>
        </span>
      </div>
      <div class="trend" aria-label="近7日审计趋势">
        <div v-for="t in stats.trend" :key="t.day" class="trend-col" :title="`${t.day}：${t.count} 条`">
          <div class="bar" :style="{ height: barHeight(t.count) }"></div>
          <span>{{ t.day.slice(5) }}</span>
        </div>
      </div>
    </div>

    <div class="toolbar">
      <el-select v-model="moduleFilter" placeholder="全部模块" style="width: 130px" @change="reload">
        <el-option label="全部模块" value="" />
        <el-option v-for="m in AUDIT_MODULES" :key="m" :label="permModuleLabel(m)" :value="m" />
      </el-select>
      <el-select
        v-model="actionFilter"
        class="search"
        placeholder="全部动作"
        clearable
        filterable
        allow-create
        default-first-option
        @change="reload"
      >
        <el-option label="全部动作" value="" />
        <el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value" />
      </el-select>
      <el-input
        v-model="operatorFilter"
        class="search"
        placeholder="操作人姓名/账号"
        clearable
        @keyup.enter="reload"
      />
      <el-input
        v-model="keywordFilter"
        class="search wide"
        placeholder="动作/对象/明细关键字"
        clearable
        @keyup.enter="reload"
      />
      <el-date-picker
        v-model="startFilter"
        type="datetime"
        format="YYYY-MM-DD HH:mm"
        value-format="YYYY-MM-DDTHH:mm:ss"
        placeholder="开始时间"
        style="width: 180px"
      />
      <span class="dim">至</span>
      <el-date-picker
        v-model="endFilter"
        type="datetime"
        format="YYYY-MM-DD HH:mm"
        value-format="YYYY-MM-DDTHH:mm:ss"
        placeholder="结束时间"
        style="width: 180px"
      />
      <el-button :loading="loading" @click="reload">查询</el-button>
      <el-button text @click="resetFilters">重置</el-button>
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
      scroll-max-height="52vh"
      @retry="load"
      @page-change="(p: number) => ((page = p), load())"
    >
      <el-table :data="rows" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <p class="detail-meta">
                操作对象：<span class="mono">{{ row.target || '—' }}</span>
              </p>
              <pre>{{ prettyDetail(row.detail) }}</pre>
            </div>
          </template>
        </el-table-column>
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
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="160">
          <template #default="{ row }">{{ targetLabel(row.target) }}</template>
        </el-table-column>
        <el-table-column label="IP" width="130">
          <template #default="{ row }">{{ row.ip ?? '—' }}</template>
        </el-table-column>
      </el-table>
    </DataTableShell>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi, type AdminAuditLog, type AdminAuditStats } from '@/api/admin'
import { AUDIT_MODULES, permModuleLabel, roleLabel } from '@/constants/admin'
import { formatDateTime } from '@/utils/format'
import DataTableShell from '@/components/ui/DataTableShell.vue'

const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<AdminAuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const stats = ref<AdminAuditStats | null>(null)
const moduleFilter = ref('')
const actionFilter = ref('')
const operatorFilter = ref('')
const keywordFilter = ref('')
const startFilter = ref('')
const endFilter = ref('')

const ACTION_LABELS: Record<string, string> = {
  CREATE_USER: '新建用户',
  UPDATE_USER: '编辑用户',
  DELETE_USER: '删除用户',
  FORCE_DELETE_USER: '强制删除用户',
  RESET_USER_PASSWORD: '重置用户密码',
  ASSIGN_ROLES: '分配角色',
  ENABLE_USER: '启用账号',
  DISABLE_USER: '禁用账号',
  UPDATE_PROFILE: '修改个人资料',
  UPDATE_ROLE_PERMISSIONS: '修改角色权限',
  CREATE_CLASS: '新建班级',
  UPDATE_CLASS: '编辑班级',
  DELETE_CLASS: '删除班级',
  UPDATE_SYSTEM_CONFIG: '修改系统参数',
  CLEAR_RUNTIME_LOG: '清空运行日志',
  UPDATE_LLM_CONFIG: '修改模型参数',
  CREATE_PROMPT: '新建评分提示词',
  UPDATE_PROMPT: '编辑评分提示词',
  ENABLE_PROMPT: '启用评分提示词',
  DISABLE_PROMPT: '停用评分提示词',
  DELETE_PROMPT: '删除评分提示词',
  CREATE_GOLDEN: '新建基准样本',
  DELETE_GOLDEN: '删除基准样本',
  ENABLE_GOLDEN: '启用基准样本',
  DISABLE_GOLDEN: '停用基准样本',
  DELETE_FILE: '删除文件',
  HIDE_FILE: '从文件中心移除',
  RE_EVALUATE: '重新评价',
  REPLAY_PARSE: '重放解析',
  REPLAY_EVALUATION: '重放评价',
  REPLAY_PARSE_BATCH: '批量重放解析',
  REPLAY_EVALUATION_BATCH: '批量重放评价',
  REPLAY_DLQ: '死信队列重放'
}

const actionOptions = Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))
const trendMax = computed(() => Math.max(1, ...(stats.value?.trend ?? []).map((t) => t.count)))

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action
}

function targetLabel(target: string | null): string {
  if (!target) return '—'
  const [kind, value] = target.split(':')
  if (kind === 'user') return `用户 #${value}`
  if (kind === 'role') return `角色 ${roleLabel(value)}`
  if (kind === 'class') return `班级 #${value}`
  if (kind === 'llm_config') return '模型配置'
  if (kind === 'golden') return `样本 #${value}`
  if (kind === 'prompt') return `提示词 ${value}`
  if (kind === 'evaluation') return `评价 #${value}`
  if (kind === 'file') return '文件'
  if (kind === 'runtime_log') return '运行日志缓冲'
  if (kind === 'sys_config') return '系统参数'
  if (kind === 'submission') {
    return value === 'batch' ? '提交批处理' : `提交 #${value}`
  }
  if (kind === 'dlq') return `${value === 'parse' ? '解析' : '评价'}死信队列`
  return target
}

function prettyDetail(detail: string | null): string {
  if (!detail) return '无明细快照'
  try {
    return JSON.stringify(JSON.parse(detail), null, 2)
  } catch {
    return detail
  }
}

function barHeight(count: number): string {
  return `${Math.max(4, Math.round((count / trendMax.value) * 48))}px`
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const params: Parameters<typeof adminApi.auditLogs>[0] = { page: page.value, size }
    if (moduleFilter.value) params.module = moduleFilter.value
    if (actionFilter.value.trim()) params.action = actionFilter.value.trim()
    if (operatorFilter.value.trim()) params.operator = operatorFilter.value.trim()
    if (keywordFilter.value.trim()) params.keyword = keywordFilter.value.trim()
    if (startFilter.value) params.start = startFilter.value
    if (endFilter.value) params.end = endFilter.value
    const result = await adminApi.auditLogs(params)
    rows.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '审计日志加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await adminApi.auditStats()
  } catch {
    // 概览失败不覆盖列表主错误
  }
}

function reload() {
  page.value = 1
  load()
}

function resetFilters() {
  moduleFilter.value = ''
  actionFilter.value = ''
  operatorFilter.value = ''
  keywordFilter.value = ''
  startFilter.value = ''
  endFilter.value = ''
  reload()
}

onMounted(() => {
  loadStats()
  load()
})
</script>

<style scoped>
.tip {
  margin-bottom: var(--aies-gap);
}
.overview {
  display: flex;
  align-items: center;
  gap: 22px;
  flex-wrap: wrap;
  padding: 14px 16px;
  margin-bottom: var(--aies-gap);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  background: var(--aies-paper);
}
.metrics {
  display: flex;
  gap: 24px;
}
.metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.metric b {
  font: 600 20px var(--aies-mono, monospace);
  color: var(--aies-ink);
  line-height: 1.1;
}
.metric span {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.modules {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  flex: 1;
  min-width: 220px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  font-size: 11.5px;
  color: var(--aies-ink2);
}
.chip em {
  font-style: normal;
  font-family: var(--aies-mono, monospace);
  color: var(--aies-brand);
}
.trend {
  display: flex;
  align-items: flex-end;
  gap: 5px;
  height: 66px;
  flex: none;
}
.trend-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  width: 26px;
}
.trend-col .bar {
  width: 14px;
  border-radius: 3px 3px 0 0;
  background: linear-gradient(180deg, var(--aies-brand), #93c5fd);
  min-height: 4px;
}
.trend-col span {
  font-size: 10px;
  color: var(--aies-ink3);
  white-space: nowrap;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: var(--aies-gap);
}
.search {
  width: 170px;
}
.search.wide {
  width: 220px;
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
  font-size: 12px;
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
  max-height: 280px;
  overflow: auto;
}
</style>
