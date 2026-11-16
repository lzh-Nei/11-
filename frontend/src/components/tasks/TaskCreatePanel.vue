<template>
  <!-- 新建任务（独立页面）：填写任务与指标，保存为草稿 -->
  <section class="panel">
    <h3>新建任务</h3>
    <p class="hint">新任务先保存为草稿，确认无误后再到“任务列表”发布；发布后指标与截止时间不可修改。</p>
    <TaskForm ref="formRef" :key="formKey" submit-text="创建任务" :loading="saving" @submit="create" />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { taskApi, type TaskCreatePayload } from '@/api/task'
import { toast } from '@/utils/toast'
import TaskForm from '@/components/tasks/TaskForm.vue'

const saving = ref(false)
const formKey = ref(0)

async function create(payload: TaskCreatePayload) {
  saving.value = true
  try {
    await taskApi.create(payload)
    toast.success('任务已创建为草稿，可在任务列表中发布')
    formKey.value++
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}
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
</style>
