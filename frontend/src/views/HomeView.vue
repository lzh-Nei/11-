<template>
  <div class="home">
    <el-header class="header">
      <div class="brand">AIES 教学AI检查评价系统</div>
      <div class="user-zone">
        <span class="hello">{{ auth.me?.realName }}（{{ auth.me?.username }}）</span>
        <el-tag :type="roleTagType" effect="dark">{{ auth.role }}</el-tag>
        <el-button size="small" @click="onLogout">登出</el-button>
      </div>
    </el-header>

    <el-main class="main">
      <el-alert
        v-if="auth.firstLogin"
        title="首次登录：请尽快修改初始密码（UC-07，改密引导开发中）"
        type="warning"
        show-icon
        :closable="false"
        class="first-login"
      />

      <PageHeader
        :title="`欢迎，${auth.me?.realName || auth.me?.username || ''}`"
        :subtitle="isTeacher ? '教师工作台 · 任务与评价总览' : '学习工作台 · 任务与提交总览'"
      >
        <a v-if="isTeacher" href="/report.html" target="_blank" rel="noopener">
          <el-button type="primary">报表驾驶舱</el-button>
        </a>
        <el-button v-if="canRefreshReport" plain :loading="refreshing" @click="refreshReport">
          刷新报表缓存
        </el-button>
        <template v-if="isTeacher">
          <el-button @click="router.push('/tasks/manage')">任务管理</el-button>
        </template>
        <template v-else>
          <el-button type="primary" @click="router.push('/tasks')">我的任务</el-button>
          <el-button @click="router.push('/submissions')">提交记录</el-button>
        </template>
        <el-button @click="router.push('/files')">文件中心</el-button>
      </PageHeader>

      <!-- 四态齐全：骨架 → 错误重试 → 空 → 数据（统计卡行接真接口 overview） -->
      <AppSkeleton v-if="loading" :rows="2" :row-height="86" />
      <ErrorState v-else-if="loadError" title="报表加载失败" :message="loadError" @retry="loadOverview" />
      <EmptyState
        v-else-if="!stats.length"
        description="暂无任务数据（等教师发布任务后这里会亮起来）"
        :action-text="isTeacher ? '去报表驾驶舱' : undefined"
        @action="noop"
      />
      <div v-else class="stat-grid">
        <StatCard
          v-for="s in stats"
          :key="s.label"
          :label="s.label"
          :value="s.value"
          :footnote="s.footnote"
        />
      </div>

      <el-card class="section-card">
        <template #header>当前用户信息（GET /api/auth/me）</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学号/工号">{{ auth.me?.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ auth.me?.realName }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag
              v-for="r in auth.me?.roles ?? []"
              :key="r"
              class="tag-gap"
              effect="dark"
              :type="roleTagType"
              >{{ r }}</el-tag
            >
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ auth.me?.email || '—' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="section-card">
        <template #header>
          <div class="perm-head">
            <span>权限清单（{{ auth.me?.permissions?.length ?? 0 }} 项 · 点击直达功能）</span>
          </div>
        </template>
        <div class="perm-groups">
          <div v-for="(perms, ns) in permGroups" :key="ns" class="perm-group">
            <div class="ns-title">{{ nsLabel(ns) }} · {{ perms.length }}</div>
            <div class="perm-list">
              <el-tooltip v-for="p in perms" :key="p" :content="permTooltip(p)" placement="top">
                <button
                  class="perm-chip"
                  :class="{
                    reserved: permEntry(p)?.kind === 'reserved',
                    action: permEntry(p)?.kind === 'action'
                  }"
                  type="button"
                  @click="onPermClick(p)"
                >
                  {{ permLabel(p) }}
                  <i v-if="permEntry(p)?.kind === 'route'" class="chip-arrow">→</i>
                  <i v-else-if="permEntry(p)?.kind === 'reserved'" class="chip-lock">预留</i>
                </button>
              </el-tooltip>
            </div>
          </div>
        </div>
      </el-card>
    </el-main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { reportApi, type ReportOverview } from '@/api/report'
import { permLabel, nsLabel, permEntry } from '@/constants/permissions'
import { toast } from '@/utils/toast'

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const loadError = ref<string | null>(null)
const overview = ref<ReportOverview | null>(null)
const refreshing = ref(false)

const isTeacher = computed(() => auth.role === 'TEACHER' || auth.role === 'ADMIN')
/* 刷新权限只发给教师（report:refresh）；学生看不到按钮，服务端仍会兜底 40301 */
const canRefreshReport = computed(() => (auth.me?.permissions ?? []).includes('report:refresh'))
const roleTagType = computed(() => {
  if (auth.role === 'ADMIN') return 'danger'
  if (auth.role === 'TEACHER') return 'warning'
  return 'success'
})

/* 统计卡派生（fromTasks 同 report.html v3 口径：真实 ReportOverviewDTO 无顶层标量） */
const stats = computed(() => {
  const tasks = overview.value?.tasks
  if (!Array.isArray(tasks)) return []
  const sum = (k: 'submitted' | 'scored') => tasks.reduce((a, t) => a + (Number(t[k]) || 0), 0)
  const subs = sum('submitted')
  const scored = sum('scored')
  return [
    { label: isTeacher.value ? '任务总数' : '我的任务', value: tasks.length, footnote: '' },
    { label: '提交总数', value: subs, footnote: '' },
    { label: '已出分', value: scored, footnote: '' },
    { label: '待批阅', value: Math.max(subs - scored, 0), footnote: '' }
  ]
})

/* 权限清单：中文化 + 命名空间分组（禁裸英文权限点） */
const permGroups = computed<Record<string, string[]>>(() => {
  const groups: Record<string, string[]> = {}
  for (const p of auth.me?.permissions ?? []) {
    const ns = String(p).split(':')[0]
    ;(groups[ns] = groups[ns] || []).push(String(p))
  }
  return groups
})

async function loadOverview() {
  loading.value = true
  loadError.value = null
  try {
    overview.value = isTeacher.value ? await reportApi.overview() : await reportApi.overviewMe()
  } catch (e) {
    const msg = (e as { message?: string })?.message
    loadError.value = msg || '网络或服务暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}

function noop() {}

/** UC-30 报表缓存重建（report:refresh）：重建后立即重载统计卡，避免 10min 陈旧窗口 */
async function refreshReport() {
  refreshing.value = true
  try {
    await reportApi.refresh()
    await loadOverview()
    toast.success('报表缓存已重建，统计数据已刷新')
  } catch (e) {
    toast.error((e as { message?: string })?.message || '缓存重建失败', '请稍后重试')
  } finally {
    refreshing.value = false
  }
}

/* 权限即导航（WP8-B4）：route 直达 / action 执行 / reserved 如实告知预留状态 */
function permTooltip(code: string): string {
  const entry = permEntry(code)
  if (!entry) return String(code)
  return `${entry.hint}（${code}）`
}

function onPermClick(code: string) {
  const entry = permEntry(code)
  if (!entry) return
  if (entry.kind === 'route') {
    router.push(entry.to)
    return
  }
  if (entry.kind === 'reserved') {
    toast.warning(`预留权限：${entry.hint}`)
    return
  }
  switch (entry.act) {
    case 'logout':
      auth.logout()
      router.push('/login')
      break
    case 'profile':
      document.querySelector('.section-card')?.scrollIntoView({ behavior: 'smooth' })
      break
    case 'dashboard':
      window.open('/report.html', '_blank', 'noopener')
      break
  }
}

async function onLogout() {
  await auth.logout()
  router.push('/login')
}

onMounted(loadOverview)
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: var(--aies-paper);
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--aies-surface);
  border-bottom: 1px solid var(--aies-border);
}
.brand {
  font-size: 17px;
  font-weight: 600;
  color: var(--aies-ink);
}
.user-zone {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hello {
  font-size: 13px;
  color: var(--aies-ink2);
}
.main {
  max-width: 1080px;
  margin: 0 auto;
  padding-top: var(--aies-gap-lg);
}
.first-login {
  margin-bottom: var(--aies-gap);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--aies-gap);
}
.section-card {
  margin-top: var(--aies-gap-lg);
  --el-card-border-radius: var(--aies-radius-lg);
}
.tag-gap {
  margin-right: 4px;
}
.perm-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ns-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--aies-ink3);
  margin-bottom: 6px;
  font-family: var(--aies-mono, monospace);
}
.perm-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.perm-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: var(--aies-radius-pill);
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  font-size: 12.5px;
  color: var(--aies-ink2);
  cursor: pointer;
  font-family: inherit;
  transition:
    box-shadow var(--aies-t),
    transform var(--aies-t),
    border-color var(--aies-t),
    color var(--aies-t);
}
.perm-chip:hover {
  border-color: var(--aies-brand);
  color: var(--aies-brand-ink, #78350f);
  box-shadow: var(--aies-shadow-hover);
  transform: var(--aies-lift);
}
.perm-chip.reserved {
  border-style: dashed;
  opacity: 0.75;
}
.perm-chip.action {
  background: #f7ede6;
}
.chip-arrow {
  font-style: normal;
  color: var(--aies-brand);
  font-weight: 600;
}
.chip-lock {
  font-style: normal;
  font-size: 10px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  color: var(--aies-ink3);
}
</style>
