<template>
  <!-- 我的评价（学生模块）：以提交为索引汇总评价状态，报告在新窗口打开（同左右布局） -->
  <PageShell :bare="embedded">
    <PageHeader
      v-if="!embedded"
      title="我的评价"
      subtitle="本人每次提交对应的评价状态与报告入口"
    >
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="tip"
      title="评价报告在新窗口打开；报告展示当前生效版本（AI 初评/教师复核后的最新分）。"
    />

    <DataTableShell
      :loading="loading"
      :error="error"
      :rows="rows"
      :total="total"
      :page="page"
      :page-size="size"
      empty-text="暂无评价记录——提交作业并由 AI 完成评分后，这里会出现报告入口"
      empty-action-text="去任务中心"
      @retry="load"
      @page-change="(p: number) => ((page = p), load())"
      @empty-action="router.push('/tasks')"
    >
      <el-table :data="rows" stripe>
        <el-table-column label="任务" min-width="220">
          <template #default="{ row }">
            <b class="task-title">{{ taskTitle(row.taskId) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="提交文件" min-width="180">
          <template #default="{ row }">
            <FileLink :name="row.fileName" :size="row.fileSize" :downloadable="false" />
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }"
            ><span class="mono">v{{ row.versionNo }}</span></template
          >
        </el-table-column>
        <el-table-column label="评价状态" width="120">
          <template #default="{ row }"><StatusTag :status="row.evaluationStatus" /></template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              plain
              :disabled="!row.evaluationId"
              @click="openReport(row.evaluationId)"
            >
              打开报告 ↗
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { submissionApi, type SubmissionMine } from '@/api/submission'
import { taskApi, type TaskInfo } from '@/api/task'
import { formatDateTime } from '@/utils/format'
import { openWindow } from '@/utils/window'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import FileLink from '@/components/ui/FileLink.vue'

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const router = useRouter()
const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<SubmissionMine[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const tasks = ref<TaskInfo[]>([])

const taskMap = computed<Record<number, string>>(() => {
  const map: Record<number, string> = {}
  for (const t of tasks.value) map[t.id] = t.title
  return map
})

function taskTitle(taskId: number): string {
  return taskMap.value[taskId] || '未知任务'
}

function openReport(evaluationId: number | null) {
  if (!evaluationId) return
  openWindow(`/evaluations/${evaluationId}`)
}

async function loadTasks() {
  try {
    const result = await taskApi.list(1, 100)
    tasks.value = result.records
  } catch {
    tasks.value = []
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const [result] = await Promise.all([submissionApi.mine(page.value, size), loadTasks()])
    rows.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '网络或服务暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin-bottom: var(--aies-gap);
}
.task-title {
  font-weight: 500;
  color: var(--aies-ink);
}
.mono {
  font-family: var(--aies-mono, monospace);
}
</style>
