<template>
  <!-- 全局错误页（#02）：403/404/500 同一壳，码值/文案由路由 meta 注入
       直达入口：/403 /500 与 history 兜底 404；登录态无关，始终可访问 -->
  <div class="error-wrap">
    <div class="error-card">
      <div class="code">{{ code }}</div>
      <h1>{{ title }}</h1>
      <p>{{ desc }}</p>
      <div class="actions">
        <el-button type="primary" @click="router.push('/')">返回首页</el-button>
        <el-button @click="router.back()">返回上一页</el-button>
        <el-button v-if="!loggedIn" text @click="router.push('/login')">去登录</el-button>
      </div>
      <p v-if="path" class="path">
        请求路径：<code>{{ path }}</code>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const code = computed(() => Number(route.meta.code ?? 404))
const title = computed(() => String(route.meta.errorTitle ?? '页面不存在'))
const desc = computed(() => String(route.meta.errorDesc ?? '链接可能已失效，或地址输入有误。'))
const path = computed(() => (code.value === 404 ? route.fullPath : ''))
const loggedIn = computed(() => Boolean(auth.accessToken))
</script>

<style scoped>
.error-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--aies-paper);
  padding: 24px;
}
.error-card {
  width: 480px;
  max-width: 100%;
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: 40px 36px 28px;
  text-align: center;
}
.code {
  font: 700 56px/1 var(--aies-mono, monospace);
  color: var(--aies-brand);
  letter-spacing: 2px;
  margin-bottom: 10px;
}
h1 {
  font-size: 20px;
  font-weight: 600;
  color: var(--aies-ink);
  margin: 0 0 10px;
}
p {
  font-size: 13.5px;
  color: var(--aies-ink3);
  line-height: 1.8;
  margin: 0 0 18px;
}
.actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
.path {
  margin: 22px 0 0;
  padding-top: 14px;
  border-top: 1px dashed var(--aies-border);
  font-size: 12px;
}
.path code {
  font-family: var(--aies-mono, monospace);
  color: var(--aies-ink2);
  word-break: break-all;
}
</style>
