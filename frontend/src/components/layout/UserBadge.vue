<template>
  <div class="user-badge">
    <div class="avatar">{{ initial }}</div>
    <div class="meta">
      <b>{{ auth.me?.realName || auth.me?.username || '用户' }}</b>
      <span>{{ roleLabel }} · {{ auth.me?.username }}</span>
    </div>
    <button type="button" class="logout" title="退出登录" @click="onLogout">退出</button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const roleLabel = computed(() => {
  if (auth.role === 'ADMIN') return '管理员'
  if (auth.role === 'TEACHER') return '教师'
  return '学生'
})
const initial = computed(() => (auth.me?.realName || auth.me?.username || 'U').slice(0, 1))

async function onLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.user-badge {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}
.avatar {
  width: 30px;
  height: 30px;
  flex: none;
  border-radius: 50%;
  background: var(--aies-brand-soft, #f7ede6);
  color: var(--aies-brand);
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 700;
}
.meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.meta b {
  font-size: 12.5px;
  color: var(--aies-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.meta span {
  font-size: 11px;
  color: var(--aies-ink3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.logout {
  border: 1px solid var(--aies-border);
  background: transparent;
  color: var(--aies-ink3);
  border-radius: 6px;
  font-size: 11.5px;
  padding: 4px 8px;
  cursor: pointer;
  font-family: inherit;
}
.logout:hover {
  border-color: var(--aies-brand);
  color: var(--aies-brand);
}
@media (max-width: 900px) {
  .meta {
    display: none;
  }
}
</style>
