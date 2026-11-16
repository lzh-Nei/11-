<template>
  <section class="panel">
    <div class="head">
      <div>
        <h3>任务列表</h3>
        <p class="hint">
          {{
            readonly
              ? '任务列表只展示全部教师的已发布和已截止任务；草稿在草稿箱中查看。'
              : '任务列表只展示已发布和已截止任务；新建任务保存后进入草稿箱，确认后再发布。'
          }}
        </p>
      </div>
      <div class="head-actions">
        <el-button v-if="!readonly" type="primary" @click="openCreate">新建任务</el-button>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <TaskFilterBar v-model="stage" :options="stageOptions" class="filter" @update:model-value="reload" />

    <AppSkeleton v-if="loading" :rows="3" :row-height="110" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <EmptyState
      v-else-if="!tasks.length"
      :description="
        readonly
          ? '当前没有已发布或已截止任务'
          : '还没有已发布任务，点击“新建任务”保存草稿后再到草稿箱发布'
      "
    />
    <div v-else class="card-list">
      <TaskCard v-for="t in tasks" :key="t.id" :task="t">
        <template v-if="!readonly">
          <el-button
            v-if="t.status === 'PUBLISHED'"
            size="small"
            type="primary"
            plain
            @click="openEdit(t)"
          >
            编辑
          </el-button>
          <el-button v-if="canDelete(t)" size="small" type="danger" plain @click="remove(t)">
            删除
          </el-button>
          <span v-else-if="t.status === 'PUBLISHED'" class="lock-note">
            已有学生提交，不能删除
          </span>
          <span v-else class="lock-note">已关闭任务不可操作</span>
        </template>
      </TaskCard>
    </div>

    <div v-if="total > size" class="pager">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>

    <TaskFormDialog
      v-model="createOpen"
      title="新建任务"
      submit-text="保存为草稿"
      :loading="saving"
      @submit="create"
    />
    <TaskFormDialog
      v-model="editOpen"
      title="编辑任务"
      :initial="editingTask"
      submit-text="保存修改"
      :loading="saving"
      :lock-structure="editLocked"
      @submit="saveEdit"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { taskApi, type TaskCreatePayload, type TaskInfo } from '@/api/task'
import { toast } from '@/utils/toast'
import TaskCard from '@/components/ui/TaskCard.vue'
import TaskFilterBar from '@/components/ui/TaskFilterBar.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import TaskFormDialog from '@/components/tasks/TaskFormDialog.vue'

withDefaults(defineProps<{ readonly?: boolean }>(), { readonly: false })

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const tasks = ref<TaskInfo[]>([])
const page = ref(1)
const size = 6
const total = ref(0)
const stage = ref('all')
const createOpen = ref(false)
const editOpen = ref(false)
const editingTask = ref<TaskInfo | null>(null)

const stageOptions = [
  { value: 'all', label: '全部' },
  { value: 'published', label: '已发布' },
  { value: 'ended', label: '已截止' }
]

const editLocked = computed(
  () =>
    editingTask.value?.status === 'PUBLISHED' &&
    Number(editingTask.value?.submissionCount ?? 0) > 0
)

function canDelete(task: TaskInfo): boolean {
  return task.status === 'PUBLISHED' && Number(task.submissionCount ?? 0) === 0
}

function openCreate() {
  createOpen.value = true
}

function openEdit(task: TaskInfo) {
  editingTask.value = task
  editOpen.value = true
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await taskApi.list(page.value, size, undefined, stage.value)
    tasks.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '任务加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

async function create(payload: TaskCreatePayload) {
  saving.value = true
  try {
    await taskApi.create(payload)
    createOpen.value = false
    toast.success('任务已保存到草稿箱，可在草稿箱发布')
    await router.push('/modules?group=teaching&child=task-drafts')
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function saveEdit(payload: TaskCreatePayload) {
  if (!editingTask.value) return
  saving.value = true
  try {
    await taskApi.update(editingTask.value.id, payload)
    editOpen.value = false
    toast.success('任务修改已保存')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function remove(task: TaskInfo) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${task.title}」吗？删除后任务与指标配置将无法恢复。`,
      '删除任务',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await taskApi.remove(task.id)
    toast.success('任务已删除')
    await load()
  } catch {
    // 拦截器已提示
  }
}

onMounted(load)
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
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hint {
  margin: 5px 0 10px;
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
.filter {
  margin-bottom: var(--aies-gap);
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--aies-gap);
}
.lock-note {
  font-size: 12.5px;
  color: var(--aies-ink3);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--aies-gap);
}
</style>
