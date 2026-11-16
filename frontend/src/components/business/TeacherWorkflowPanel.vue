<template>
  <section class="panel">
    <div class="head">
      <div>
        <h3>评价与复核</h3>
        <p class="hint">
          提交队列可按班级、学生筛选；先发起 AI 评价，AI 完成后进入待复核，教师可逐条复评或一键通过，归档后进入复核记录。
        </p>
      </div>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-tabs v-model="tab" class="tabs" @tab-change="onTabChange">
      <el-tab-pane label="提交队列" name="submissions" />
      <el-tab-pane label="待复核" name="review" />
      <el-tab-pane label="复核记录" name="history" />
    </el-tabs>

    <div class="toolbar">
      <el-select
        v-model="classId"
        placeholder="全部班级"
        clearable
        style="width: 180px"
        @change="reload"
      >
        <el-option
          v-for="c in classes"
          :key="c.id"
          :label="c.className"
          :value="c.id"
        />
      </el-select>
      <el-input
        v-model="keyword"
        class="search"
        placeholder="学生姓名 / 任务 / 文件名"
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
      :empty-text="emptyText"
      @retry="load"
      @page-change="(p: number) => ((page = p), load())"
    >
      <el-table :data="rows" stripe>
        <el-table-column label="任务" min-width="150">
          <template #default="{ row }">{{ row.taskTitle ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="班级" width="140">
          <template #default="{ row }">{{ row.className ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="学生" width="120">
          <template #default="{ row }">{{ row.studentName ?? row.studentUsername ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="提交文件" min-width="170">
          <template #default="{ row }">
            <span class="file-name">{{ row.fileName }}</span>
            <small class="version">v{{ row.versionNo }}</small>
          </template>
        </el-table-column>
        <el-table-column label="评价状态" width="110">
          <template #default="{ row }">
            <StatusTag v-if="row.evaluationStatus" :status="row.evaluationStatus" />
            <span v-else class="dim">未评价</span>
          </template>
        </el-table-column>
        <el-table-column label="评价方" width="150">
          <template #default="{ row }">{{ row.lastEvaluator || '—' }}</template>
        </el-table-column>
        <el-table-column label="总分" width="80">
          <template #default="{ row }">{{ row.totalScore ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="申诉" width="130">
          <template #default="{ row }">
            <template v-if="row.appealStatus === 'PENDING'">
              <el-tag size="small" type="warning">待复评</el-tag>
              <small v-if="row.assignedTeacherName" class="appeal-teacher">{{ row.assignedTeacherName }}</small>
            </template>
            <span v-else-if="row.appealStatus === 'RESOLVED'" class="dim">已处理</span>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.submitTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="tab === 'submissions' && row.canTriggerAi"
              size="small"
              type="primary"
              plain
              @click="triggerAi(row)"
            >
              AI先评
            </el-button>
            <span
              v-else-if="tab === 'submissions' && !row.evaluationId && row.parseStatus !== 'SUCCESS'"
              class="dim"
            >
              {{ row.parseStatus === 'FAILED' ? '文件解析失败' : '文件处理中' }}
            </span>
            <el-button
              v-if="tab === 'review' && row.evaluationId"
              size="small"
              type="primary"
              plain
              @click="openReview(row)"
            >
              复评
            </el-button>
            <el-button
              v-if="tab === 'review' && row.evaluationStatus === 'AI_DONE'"
              size="small"
              type="success"
              plain
              @click="approve(row)"
            >
              一键通过
            </el-button>
            <el-button
              v-if="tab === 'history' && row.evaluationId"
              size="small"
              type="primary"
              plain
              @click="openHistory(row)"
            >
              查看记录
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>

    <section v-if="selectedRow" class="workbench">
      <div class="workbench-head">
        <h3>
          {{ selectedRow.taskTitle || '评价详情' }}
          <span class="sub">· {{ selectedRow.studentName || '学生' }}</span>
        </h3>
        <el-button link type="primary" size="small" @click="selectedRow = null">收起</el-button>
      </div>
      <Evaluate
        :key="`${selectedRow.evaluationId}-${readonlyWorkbench}`"
        :evaluation-id="selectedRow.evaluationId ?? undefined"
        embedded
        :readonly="readonlyWorkbench"
      />
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { adminApi, type AdminClass } from '@/api/admin'
import {
  teacherWorkflowApi,
  type TeacherWorkflowRow
} from '@/api/teacherWorkflow'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import Evaluate from '@/views/Evaluate.vue'

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const auth = useAuthStore()
const tab = ref<'submissions' | 'review' | 'history'>('submissions')
const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<TeacherWorkflowRow[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const classId = ref<number | undefined>(undefined)
const keyword = ref('')
const classes = ref<AdminClass[]>([])
const selectedRow = ref<TeacherWorkflowRow | null>(null)
const readonlyWorkbench = ref(false)

const emptyText = computed(() => {
  if (tab.value === 'review') return '暂无待复核记录——AI 评价完成后会出现在这里'
  if (tab.value === 'history') return '暂无复核记录——通过复核的记录会归档到这里'
  return '暂无提交记录'
})

async function loadClasses() {
  try {
    const result = await adminApi.classes(1, 100)
    classes.value = result.records.filter((c) => c.teacherId === auth.me?.id)
  } catch {
    classes.value = []
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await teacherWorkflowApi.list({
      page: page.value,
      size,
      classId: classId.value,
      keyword: keyword.value.trim() || undefined,
      stage: tab.value === 'submissions' ? 'all' : tab.value
    })
    rows.value = result.records
    total.value = result.total
    if (selectedRow.value && !rows.value.some((r) => r.evaluationId === selectedRow.value?.evaluationId)) {
      selectedRow.value = null
    }
  } catch (e) {
    error.value = (e as { message?: string })?.message || '评价工作流加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function onTabChange() {
  selectedRow.value = null
  reload()
}

async function triggerAi(row: TeacherWorkflowRow) {
  try {
    await ElMessageBox.confirm(
      `将对「${row.fileName}」发起 AI 评价，完成后进入待复核列表。确认发起？`,
      'AI 先评',
      { type: 'warning', confirmButtonText: '确认发起', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await teacherWorkflowApi.triggerAi(row.submissionId)
    toast.success('已发起 AI 评价，完成后进入待复核')
    await load()
  } catch (e) {
    toast.error((e as { message?: string })?.message || '发起 AI 评价失败', '请稍后重试')
  }
}

async function approve(row: TeacherWorkflowRow) {
  if (!row.evaluationId) return
  try {
    await ElMessageBox.confirm(
      `将按照 AI 评价结果一键通过并归档「${row.taskTitle ?? row.fileName}」吗？`,
      '一键通过',
      { type: 'warning', confirmButtonText: '通过并归档', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await teacherWorkflowApi.approve(row.evaluationId)
    toast.success('已通过 AI 结果并归档到复核记录')
    if (selectedRow.value?.evaluationId === row.evaluationId) selectedRow.value = null
    await load()
  } catch (e) {
    toast.error((e as { message?: string })?.message || '一键通过失败', '请稍后重试')
  }
}

function openReview(row: TeacherWorkflowRow) {
  readonlyWorkbench.value = false
  selectedRow.value = row
}

function openHistory(row: TeacherWorkflowRow) {
  readonlyWorkbench.value = true
  selectedRow.value = row
}

onMounted(async () => {
  await loadClasses()
  await load()
})
</script>

<style scoped>
.panel {
  min-width: 0;
}
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--aies-ink);
}
.hint {
  margin: 5px 0 10px;
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
.tabs {
  margin-bottom: 4px;
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
.file-name {
  display: inline-block;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.version {
  margin-left: 6px;
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.appeal-teacher {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--aies-ink3);
}
.workbench {
  margin-top: var(--aies-gap);
  padding: var(--aies-gap);
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
}
.workbench-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.workbench-head h3 {
  margin: 0;
  font-size: 14px;
  color: var(--aies-ink);
}
.sub {
  font-weight: 400;
  color: var(--aies-ink3);
}
</style>
