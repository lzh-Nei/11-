<template>
  <!-- 删除任务（独立页面）：仅草稿且无学生提交可删除 -->
  <section class="panel">
    <h3>删除任务</h3>
    <p class="hint">仅草稿状态且没有学生提交的任务可以删除；已发布任务请保留用于评价与复核追溯。</p>

    <AppSkeleton v-if="loading" :rows="2" :row-height="70" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <EmptyState v-else-if="!tasks.length" description="暂无可删除的任务" />

    <template v-else>
      <el-form label-position="top" class="selector">
        <el-form-item label="选择任务">
          <el-select v-model="selectedId" placeholder="选择任务" style="width: 100%; max-width: 520px">
            <el-option
              v-for="t in tasks"
              :key="t.id"
              :label="`${t.title}（${statusText(t.status)}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <div v-if="selectedTask" class="summary">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="任务">{{ selectedTask.title }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(selectedTask.status) }}</el-descriptions-item>
          <el-descriptions-item label="截止时间">{{ formatDateTime(selectedTask.deadline) }}</el-descriptions-item>
        </el-descriptions>
        <div class="actions">
          <el-button
            type="danger"
            :disabled="selectedTask.status !== 'DRAFT' || deleting"
            :loading="deleting"
            @click="remove"
          >
            删除任务
          </el-button>
          <span v-if="selectedTask.status !== 'DRAFT'" class="warn">
            已发布/已关闭任务不能删除，避免评价与复核记录失去归属。
          </span>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { taskApi, type TaskInfo } from '@/api/task'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(false)
const deleting = ref(false)
const error = ref<string | null>(null)
const tasks = ref<TaskInfo[]>([])
const selectedId = ref<number | null>(null)

const selectedTask = computed(() => tasks.value.find((t) => t.id === selectedId.value) ?? null)

function statusText(status: string): string {
  if (status === 'DRAFT') return '草稿'
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'CLOSED') return '已关闭'
  return status
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await taskApi.list(1, 100)
    tasks.value = result.records
    if (!tasks.value.some((t) => t.id === selectedId.value)) {
      selectedId.value = tasks.value[0]?.id ?? null
    }
  } catch (e) {
    error.value = (e as { message?: string })?.message || '任务加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function remove() {
  if (!selectedTask.value || selectedTask.value.status !== 'DRAFT') return
  try {
    await ElMessageBox.confirm(
      `确定删除「${selectedTask.value.title}」吗？删除后任务与指标配置将无法恢复。`,
      '删除任务',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deleting.value = true
  try {
    await taskApi.remove(selectedTask.value.id)
    toast.success('任务已删除')
    selectedId.value = null
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    deleting.value = false
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
  max-width: 520px;
  margin-bottom: var(--aies-gap);
}
.summary {
  max-width: 760px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: var(--aies-gap);
}
.warn {
  font-size: 12px;
  color: #b91c1c;
}
</style>
