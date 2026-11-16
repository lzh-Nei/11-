<template>
  <!-- 不可逆操作确认封装：确认弹窗（文案必含后果说明）→ loading → 成功/失败 toast -->
  <slot :trigger="trigger" :loading="loading" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'

const props = withDefaults(
  defineProps<{
    /** 确认弹窗标题（必填：动作语义，如"发布任务"） */
    title: string
    /** 后果说明（不可逆操作必须写清影响面） */
    message: string
    /** 确认按钮文案 */
    confirmText?: string
  }>(),
  { confirmText: '确认执行' }
)

const emit = defineEmits<{ execute: [done: () => void] }>()
const loading = ref(false)

function trigger() {
  ElMessageBox.confirm(props.message, props.title, {
    confirmButtonText: props.confirmText,
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--primary'
  })
    .then(() => {
      loading.value = true
      // 子组件通过 execute 事件执行动作，动作完成后必须调用 done() 收口
      emit('execute', () => {
        loading.value = false
      })
    })
    .catch(() => {
      /* 用户取消：静默，非错误 */
    })
}

defineExpose({ notifySuccess: (m: string) => ElMessage.success(m), notifyError: (m: string) => ElMessage.error(m) })
</script>
