<template>
  <!-- 错误态（四态铁律③）：错误卡 + 重试，禁裸 alert / 裸错误码 -->
  <div class="error-state" role="alert">
    <div class="err-icon">!</div>
    <div class="err-body">
      <b>{{ title }}</b>
      <p>{{ message }}</p>
    </div>
    <el-button v-if="retryable" plain size="small" @click="emit('retry')">重试</el-button>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title?: string; message?: string; retryable?: boolean }>(), {
  title: '加载失败',
  message: '网络或服务暂时不可用，请稍后重试',
  retryable: true
})
const emit = defineEmits<{ retry: [] }>()
</script>

<style scoped>
.error-state {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: var(--aies-radius-lg);
  padding: 16px var(--aies-gap-lg);
}
.err-icon {
  flex: none;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: #dc2626;
  color: #fff;
  font: 700 15px/28px var(--aies-sans, sans-serif);
  text-align: center;
}
.err-body {
  flex: 1;
  min-width: 0;
}
.err-body b {
  color: #7f1d1d;
  font-size: 13.5px;
}
.err-body p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: #991b1b;
}
</style>
