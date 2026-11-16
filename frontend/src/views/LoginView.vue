<template>
  <!-- 登录页（WP8-B0 试点改造）：暖纸底 + 白卡 + 品牌琥珀；逻辑收口在 LoginForm -->
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand-mark">A</div>
      <div class="title">AIES 教学AI检查评价系统</div>
      <p class="subtitle">登录后进入你的教学 / 学习工作台</p>
      <LoginForm @success="goHome" />
    </div>
    <p class="foot-hint">
      登录需要图形验证码 ·
      <span class="health" :class="healthClass" :title="healthTitle" @click="probe">
        <i class="dot"></i>{{ healthText }}
      </span>
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import LoginForm from '@/components/business/LoginForm.vue'
import { systemApi } from '@/api/system'

const router = useRouter()

function goHome() {
  router.push('/')
}

/* 服务状态提示：点击可重新检查连接 */
const health = ref<boolean | null>(null)
const healthText = computed(() => {
  if (health.value === null) return '正在连接…'
  return health.value ? '连接正常' : '连接暂不可用'
})
const healthClass = computed(() => ({
  ok: health.value === true,
  bad: health.value === false
}))
const healthTitle = computed(() =>
  health.value === null
    ? '正在检查连接状态'
    : health.value
      ? '系统连接正常，点击重新检查'
      : '系统暂时无法连接，点击重试'
)

async function probe() {
  health.value = await systemApi.health()
}

onMounted(probe)
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
.health {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  user-select: none;
}
.health .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--aies-ink3);
}
.health.ok .dot {
  background: #15803d;
}
.health.bad .dot {
  background: #b91c1c;
}
.health.ok {
  color: #15803d;
}
.health.bad {
  color: #b91c1c;
}
</style>
