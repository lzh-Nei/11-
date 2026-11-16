<template>
  <PageShell :bare="embedded">
    <PageHeader v-if="!embedded" title="任务中心" subtitle="查看本班已发布的实训任务与截止时间">
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <TaskFilterBar v-model="filter" class="filter-row" @update:model-value="reload" />

    <AppSkeleton v-if="loading" :rows="3" :row-height="110" class="mt" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <EmptyState v-else-if="!tasks.length" description="暂无已发布任务——教师发布后这里会亮起来" />
    <ScrollArea v-else max-height="calc(100vh - 320px)" padded>
      <div class="card-list">
        <TaskCard v-for="t in tasks" :key="t.id" :task="t" @open="openTask(t)">
          <el-button
            v-if="canSubmit(t)"
            type="primary"
            size="small"
            @click="openSubmit(t.id)"
          >
            提交作业 ↗
          </el-button>
          <span v-else class="task-note">{{ taskNote(t) }}</span>
        </TaskCard>
      </div>
    </ScrollArea>

    <div v-if="total > size" class="pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { taskApi, type TaskInfo } from '@/api/task'
import { openWindow } from '@/utils/window'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import TaskCard from '@/components/ui/TaskCard.vue'
import TaskFilterBar from '@/components/ui/TaskFilterBar.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const loading = ref(false)
const error = ref<string | null>(null)
const tasks = ref<TaskInfo[]>([])
const page = ref(1)
const size = 10
const total = ref(0)
const filter = ref('PUBLISHED')

function isOverdue(t: TaskInfo) {
  return t.status === 'CLOSED' || new Date(t.deadline).getTime() < Date.now()
}

function canSubmit(t: TaskInfo) {
  return t.status === 'PUBLISHED' && !isOverdue(t)
}

function taskNote(t: TaskInfo) {
  if (t.status === 'DRAFT') return '教师尚未发布'
  if (t.status === 'CLOSED' || isOverdue(t)) return '任务已截止'
  return '暂不可提交'
}

function openTask(t: TaskInfo) {
  if (canSubmit(t)) openSubmit(t.id)
}

function reload() {
  page.value = 1
  load()
}

/** 提交作业为下钻页：新窗口打开，保持左右模块布局 */
function openSubmit(taskId: number) {
  openWindow(`/tasks/${taskId}/submit`)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const status = filter.value || undefined
    const result = await taskApi.list(page.value, size, status)
    tasks.value = result.records
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
.filter-row {
  margin-bottom: var(--aies-gap);
}
.mt {
  margin-top: var(--aies-gap);
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--aies-gap);
}
.task-note {
  font-size: 12.5px;
  color: var(--aies-ink3);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--aies-gap);
}
</style>
