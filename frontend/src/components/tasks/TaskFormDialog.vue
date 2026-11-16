<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="860px"
    destroy-on-close
    @update:model-value="onVisibleChange"
  >
    <TaskForm
      v-if="modelValue"
      :initial="initial"
      :submit-text="submitText"
      :loading="loading"
      :lock-structure="lockStructure"
      @submit="$emit('submit', $event)"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import type { TaskCreatePayload, TaskInfo } from '@/api/task'
import TaskForm from '@/components/tasks/TaskForm.vue'

withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    initial?: TaskInfo | null
    submitText?: string
    loading?: boolean
    lockStructure?: boolean
  }>(),
  { initial: null, submitText: '保存', loading: false, lockStructure: false }
)
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: TaskCreatePayload]
}>()

function onVisibleChange(value: boolean) {
  emit('update:modelValue', value)
}
</script>
