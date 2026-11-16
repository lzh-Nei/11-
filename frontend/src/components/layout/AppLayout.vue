<template>
  <!-- 应用外壳（WP8-C1）：左侧功能模块导航 + 右侧模块页面
       模块按角色/权限生成；下钻页（提交/报告/复核台）在同一外壳内以新窗口打开 -->
  <div class="shell">
    <aside class="side">
      <div class="brand">
        <div class="mark">A</div>
        <div class="brand-text">
          <b>AIES</b>
          <small>教学AI检查评价系统</small>
        </div>
      </div>

      <nav class="nav">
        <div v-for="g in groups" :key="g.label" class="group">
          <p class="group-label">{{ g.label }}</p>
          <router-link
            v-for="item in g.items"
            :key="item.key"
            :to="item.to"
            class="nav-item"
            :class="{ active: isActive(item) }"
            :title="item.label"
          >
            <NavIcon :name="item.icon" />
            <span class="nav-text">{{ item.label }}</span>
            <span v-if="item.newWindow" class="nav-ext" aria-hidden="true">↗</span>
          </router-link>
        </div>
      </nav>

      <div class="side-foot">
        <div class="me">
          <div class="avatar">{{ initial }}</div>
          <div class="me-meta">
            <b>{{ auth.me?.realName || auth.me?.username }}</b>
            <span>{{ roleLabel }} · {{ auth.me?.username }}</span>
          </div>
        </div>
        <button type="button" class="logout" title="退出登录" @click="onLogout">退出</button>
      </div>
    </aside>

    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import NavIcon from '@/components/ui/NavIcon.vue'

interface ModuleItem {
  key: string
  label: string
  icon: string
  to: string
  newWindow?: boolean
  match: (path: string) => boolean
}
interface ModuleGroup {
  label: string
  items: ModuleItem[]
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const roleLabel = computed(() => {
  if (auth.role === 'ADMIN') return '管理员'
  if (auth.role === 'TEACHER') return '教师'
  return '学生'
})
const initial = computed(() => (auth.me?.realName || auth.me?.username || 'U').slice(0, 1))

/** 三角色统一左侧模块栏：功能模块（二级/三级目录） / 文件中心 / 个人中心 */
const groups = computed<ModuleGroup[]>(() => [
  {
    label: '工作区',
    items: [
      {
        key: 'modules',
        label: '功能模块',
        icon: 'dashboard',
        to: '/modules',
        match: (p) =>
          p === '/modules' ||
          p === '/tasks' ||
          p === '/tasks/manage' ||
          p === '/submissions' ||
          p === '/my-evaluations' ||
          p === '/review' ||
          /^\/tasks\/\d+\/submit$/.test(p) ||
          /^\/evaluations\/\d+(\/review)?$/.test(p)
      },
      {
        key: 'files',
        label: '文件中心',
        icon: 'files',
        to: '/files',
        match: (p) => p === '/files'
      },
      {
        key: 'account',
        label: '个人中心',
        icon: 'user',
        to: '/account',
        match: (p) => p === '/account' || p === '/change-password'
      }
    ]
  }
])

function isActive(item: ModuleItem): boolean {
  return item.match(route.path)
}

async function onLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
  background: var(--aies-paper);
}
.side {
  width: 236px;
  flex: none;
  display: flex;
  flex-direction: column;
  background: var(--aies-surface);
  border-right: 1px solid var(--aies-border);
  position: sticky;
  top: 0;
  height: 100vh;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px 16px;
}
.mark {
  width: 36px;
  height: 36px;
  flex: none;
  border-radius: 10px;
  background: var(--aies-brand);
  color: #fff;
  display: grid;
  place-items: center;
  font: 700 17px var(--aies-mono, monospace);
}
.brand-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.brand-text b {
  font-size: 15px;
  color: var(--aies-ink);
  letter-spacing: 0.02em;
}
.brand-text small {
  font-size: 11px;
  color: var(--aies-ink3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.nav {
  flex: 1;
  overflow-y: auto;
  padding: 4px 10px 16px;
}
.group + .group {
  margin-top: 14px;
}
.group-label {
  margin: 0 0 6px;
  padding: 0 10px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--aies-ink3);
}
.nav-item {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--aies-ink2);
  font-size: 13.5px;
  font-family: inherit;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition:
    background var(--aies-t),
    color var(--aies-t);
}
.nav-item + .nav-item {
  margin-top: 2px;
}
.nav-item:hover {
  background: var(--aies-paper);
  color: var(--aies-ink);
}
.nav-item.active {
  background: var(--aies-brand-soft, #f7ede6);
  color: var(--aies-brand);
  font-weight: 600;
}
.nav-item.active::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--aies-brand);
}
.nav-text {
  flex: 1;
  white-space: nowrap;
}
.nav-ext {
  font-size: 11px;
  color: var(--aies-ink3);
}
.side-foot {
  border-top: 1px solid var(--aies-border);
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.me {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
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
.me-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.me-meta b {
  font-size: 12.5px;
  color: var(--aies-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.me-meta span {
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
.content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  height: 100vh;
}

@media (max-width: 900px) {
  .side {
    width: 72px;
  }
  .brand {
    justify-content: center;
    padding: 16px 8px 12px;
  }
  .brand-text,
  .group-label,
  .nav-text,
  .nav-ext,
  .me-meta {
    display: none;
  }
  .nav {
    padding: 4px 8px 12px;
  }
  .nav-item {
    justify-content: center;
    padding: 10px 0;
  }
  .side-foot {
    flex-direction: column;
    padding: 10px 6px;
  }
  .me {
    justify-content: center;
  }
  .logout {
    width: 100%;
  }
}
</style>
