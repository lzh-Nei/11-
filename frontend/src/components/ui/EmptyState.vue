<template>
  <!-- 空态（四态铁律②）：设计过的空态 = 说明 + 引导按钮，禁白板 -->
  <div class="empty-state">
    <el-empty :description="description" :image-size="96">
      <el-button v-if="actionText" type="primary" @click="emit('action')">{{ actionText }}</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ description?: string; actionText?: string }>(), {
  description: '暂无数据'
})
const emit = defineEmits<{ action: [] }>()
</script>

<style scoped>
.empty-state {
  background: var(--aies-surface);
  border: 1px dashed var(--aies-border);
  border-radius: var(--aies-radius-lg);
  padding: var(--aies-gap-lg);
}
</style>
