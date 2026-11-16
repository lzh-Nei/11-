<template>
  <section class="panel">
    <div class="head">
      <div>
        <h3>草稿箱</h3>
        <p class="hint">
          {{
            readonly
              ? '这里列出教师新建任务时保存的草稿；编辑与发布由授课教师操作。'
              : '新建任务时选择“保存为草稿”的任务会保存在这里；确认内容后再发布到任务列表。'
          }}
        </p>
      </div>
      <div class="head-actions">
        <el-button v-if="!readonly" type="primary" @click="openCreate">新建任务</el-button>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <AppSkeleton v-if="loading" :rows="3" :row-height="110" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <EmptyState
      v-else-if="!drafts.length"
      :description="readonly ? '当前没有教师草稿' : '草稿箱是空的——从任务列表新建任务并保存为草稿后会出现在这里'"
      :action-text="readonly ? '' : '新建任务'"
      @action="openCreate"
    />
    <div v-else class="card-list">
      <TaskCard v-for="t in drafts" :key="t.id" :task="t">
        <template v-if="!readonly">
          <el-button size="small" type="primary" plain @click="openEdit(t)">继续编辑</el-button>
          <el-button size="small" type="success" plain @click="publish(t)">发布</el-button>
          <el-button size="small" type="danger" plain @click="remove(t)">删除</el-button>
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
      title="编辑草稿"
      :initial="editingDraft"
      submit-text="保存修改"
      :loading="saving"
      @submit="saveEdit"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { taskApi, type TaskCreatePayload, type TaskInfo } from '@/api/task'
import { toast } from '@/utils/toast'
import TaskCard from '@/components/ui/TaskCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import TaskFormDialog from '@/components/tasks/TaskFormDialog.vue'

withDefaults(defineProps<{ readonly?: boolean }>(), { readonly: false })

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const drafts = ref<TaskInfo[]>([])
const page = ref(1)
const size = 6
const total = ref(0)
const createOpen = ref(false)
const editOpen = ref(false)
const editingDraft = ref<TaskInfo | null>(null)

function openCreate() {
  createOpen.value = true
}

function openEdit(task: TaskInfo) {
  editingDraft.value = task
  editOpen.value = true
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await taskApi.list(page.value, size, 'DRAFT')
    drafts.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '草稿加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function create(payload: TaskCreatePayload) {
  saving.value = true
  try {
    await taskApi.create(payload)
    createOpen.value = false
    toast.success('任务已保存到草稿箱')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function saveEdit(payload: TaskCreatePayload) {
  if (!editingDraft.value) return
  saving.value = true
  try {
    await taskApi.update(editingDraft.value.id, payload)
    editOpen.value = false
    toast.success('草稿已保存')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function publish(task: TaskInfo) {
  try {
    await ElMessageBox.confirm(
      `发布「${task.title}」后，学生即可看到该任务。确认发布？`,
      '发布任务',
      { type: 'warning', confirmButtonText: '确认发布', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await taskApi.publish(task.id)
    toast.success('任务已发布')
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function remove(task: TaskInfo) {
  try {
    await ElMessageBox.confirm(
      `确定删除草稿「${task.title}」吗？删除后任务与指标配置将无法恢复。`,
      '删除草稿',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await taskApi.remove(task.id)
    toast.success('草稿已删除')
    if (page.value > 1 && drafts.value.length === 1) page.value -= 1
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
.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--aies-gap);
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--aies-gap);
}
</style>
