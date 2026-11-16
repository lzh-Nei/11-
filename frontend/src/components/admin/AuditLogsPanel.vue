<template>
  <!-- 审计日志（audit:view，UC-40/S-09）：只读检索；detail 为 JSON 快照 -->
  <section class="panel">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="tip"
      title="审计日志用于追溯关键操作，只读不可修改。"
    />

    <div class="toolbar">
      <el-select v-model="moduleFilter" placeholder="全部模块" style="width: 140px" @change="reload">
        <el-option label="全部模块" value="" />
        <el-option v-for="m in AUDIT_MODULES" :key="m" :label="permModuleLabel(m)" :value="m" />
      </el-select>
      <el-select
        v-model="actionFilter"
        class="search"
        placeholder="全部动作"
        clearable
        @change="reload"
      >
        <el-option label="全部动作" value="" />
        <el-option v-for="a in actionOptions" :key="a.value" :label="a.label" :value="a.value" />
      </el-select>
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
import { onMounted, ref } from 'vue'
import { adminApi, type AdminAuditLog } from '@/api/admin'
import { AUDIT_MODULES, permModuleLabel, roleLabel } from '@/constants/admin'
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

const ACTION_LABELS: Record<string, string> = {
  CREATE_USER: '新建用户',
  UPDATE_USER: '编辑用户',
  DELETE_USER: '删除用户',
  ASSIGN_ROLES: '分配角色',
  ENABLE_USER: '启用账号',
  DISABLE_USER: '禁用账号',
  UPDATE_PROFILE: '修改个人资料',
  UPDATE_ROLE_PERMISSIONS: '修改角色权限',
  CREATE_CLASS: '新建班级',
  UPDATE_CLASS: '编辑班级',
  DELETE_CLASS: '删除班级',
  UPDATE_LLM_CONFIG: '修改模型参数',
  CREATE_PROMPT: '新建评分提示词',
  ENABLE_PROMPT: '启用评分提示词',
  ENABLE_GOLDEN: '启用基准样本',
  DISABLE_GOLDEN: '停用基准样本',
  DELETE_FILE: '删除文件',
  HIDE_FILE: '从文件中心移除',
  RE_EVALUATE: '重新评价'
}

const actionOptions = Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? '其他操作'
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
  return '其他对象'
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
</style>
