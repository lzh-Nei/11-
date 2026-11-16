<template>
  <!-- 认证页卡片壳：暖纸全屏居中 + 400px 白卡 + 标题（注册/找回共用口径） -->
  <div class="auth-wrap">
    <el-card class="auth-card">
      <template #header>
        <div class="title">{{ title }}</div>
      </template>
      <slot />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<style scoped>
.auth-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--aies-paper);
  padding: 24px;
}
.auth-card {
  width: 400px;
  max-width: 100%;
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  border: 1px solid var(--aies-border);
}
.title {
  text-align: center;
  font-size: 18px;
  font-weight: 600;
}
</style>
