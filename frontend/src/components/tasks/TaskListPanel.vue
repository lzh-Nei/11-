<template>
  <!-- 任务列表：查看 / 筛选 / 发布草稿（管理员只读） -->
  <section class="panel">
    <div class="head">
      <h3>任务列表</h3>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>
    <p v-if="readonly" class="hint">管理员可查看全部教师任务；新建、编辑、删除由授课教师操作。</p>

    <TaskFilterBar v-model="filter" class="filter" @update:model-value="reload" />

    <AppSkeleton v-if="loading" :rows="3" :row-height="110" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <EmptyState
      v-else-if="!tasks.length"
      :description="readonly ? '当前没有任务数据' : '还没有任务，请到“新建任务”创建第一份任务'"
    />
    <div v-else class="card-list">
      <TaskCard v-for="t in tasks" :key="t.id" :task="t">
        <el-button
          v-if="!readonly && t.status === 'DRAFT'"
          type="primary"
          size="small"
          @click="publish(t)"
        >
          发布任务
        </el-button>
        <span v-else-if="!readonly" class="lock-note">已发布，指标与截止时间已锁定</span>
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
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { taskApi, type TaskInfo } from '@/api/task'
import { toast } from '@/utils/toast'
import TaskCard from '@/components/ui/TaskCard.vue'
import TaskFilterBar from '@/components/ui/TaskFilterBar.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

withDefaults(defineProps<{ readonly?: boolean }>(), { readonly: false })

const loading = ref(false)
const error = ref<string | null>(null)
const tasks = ref<TaskInfo[]>([])
const page = ref(1)
const size = 6
const total = ref(0)
const filter = ref('')

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await taskApi.list(page.value, size, filter.value || undefined)
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

async function publish(task: TaskInfo) {
  try {
    await ElMessageBox.confirm(
      `发布「${task.title}」后，学生立即可见，任务指标与截止时间将不能再修改。`,
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

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--aies-ink);
}
.hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--aies-ink3);
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
