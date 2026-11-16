<template>
  <!-- 登录页（WP8-B0 试点改造）：暖纸底 + 白卡 + 品牌琥珀；逻辑收口在 LoginForm -->
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand-mark">A</div>
      <div class="title">AIES 教学AI检查评价系统</div>
      <p class="subtitle">登录后进入你的教学 / 学习工作台</p>
      <LoginForm @success="goHome" />
    </div>
    <p class="foot-hint">同源部署 · 验证码与主应用同一 /api/auth/captcha 端点（A-04）</p>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import LoginForm from '@/components/business/LoginForm.vue'

const router = useRouter()
function goHome() {
  router.push('/')
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: var(--aies-paper);
  padding: 24px;
}
.login-card {
  width: 400px;
  max-width: 100%;
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: 32px 32px 24px;
}
.brand-mark {
  width: 44px;
  height: 44px;
  margin: 0 auto 12px;
  border-radius: 12px;
  background: var(--aies-brand);
  color: #fff;
  display: grid;
  place-items: center;
  font: 700 20px var(--aies-mono, monospace);
}
.title {
  text-align: center;
  font-size: 17px;
  font-weight: 600;
  color: var(--aies-ink);
}
.subtitle {
  text-align: center;
  font-size: 12.5px;
  color: var(--aies-ink3);
  margin: 4px 0 20px;
}
.foot-hint {
  font-size: 12px;
  color: var(--aies-ink3);
}
</style>
