<template>
  <!-- 编辑任务（独立页面）：仅草稿可编辑 -->
  <section class="panel">
    <h3>编辑任务</h3>
    <p class="hint">仅草稿状态任务可编辑；已发布任务的指标集与截止时间已锁定。</p>

    <AppSkeleton v-if="loading" :rows="2" :row-height="70" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <EmptyState v-else-if="!drafts.length" description="暂无草稿任务可编辑" />

    <template v-else>
      <el-form label-position="top" class="selector">
        <el-form-item label="选择要编辑的草稿任务">
          <el-select v-model="selectedId" placeholder="选择任务" style="width: 100%; max-width: 460px">
            <el-option
              v-for="t in drafts"
              :key="t.id"
              :label="`${t.title}（截止 ${formatDateTime(t.deadline)}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <TaskForm
        v-if="selectedTask"
        :initial="selectedTask"
        submit-text="保存修改"
        :loading="saving"
        @submit="save"
      />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { taskApi, type TaskCreatePayload, type TaskInfo } from '@/api/task'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import TaskForm from '@/components/tasks/TaskForm.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const drafts = ref<TaskInfo[]>([])
const selectedId = ref<number | null>(null)

const selectedTask = computed(() => drafts.value.find((t) => t.id === selectedId.value) ?? null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await taskApi.list(1, 100, 'DRAFT')
    drafts.value = result.records
    if (!drafts.value.some((t) => t.id === selectedId.value)) {
      selectedId.value = drafts.value[0]?.id ?? null
    }
  } catch (e) {
    error.value = (e as { message?: string })?.message || '任务加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function save(payload: TaskCreatePayload) {
  if (!selectedId.value) return
  saving.value = true
  try {
    await taskApi.update(selectedId.value, payload)
    toast.success('任务修改已保存')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
h3 {
  margin: 0 0 6px;
  font-size: 15px;
  color: var(--aies-ink);
}
.hint {
  margin: 0 0 var(--aies-gap);
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
.selector {
  margin-bottom: var(--aies-gap);
  max-width: 460px;
}
</style>
