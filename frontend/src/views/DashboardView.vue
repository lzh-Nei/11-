<template>
  <!-- 工作台（模块首页）：角色总览统计 + 功能模块导览 -->
  <PageShell :bare="embedded">
    <PageHeader
      v-if="!embedded"
      :title="`欢迎，${auth.me?.realName || auth.me?.username || ''}`"
      :subtitle="headerSubtitle"
    >
      <template v-if="isTeacherData">
        <el-button v-if="canRefreshReport" plain :loading="refreshing" @click="refreshReport">
          刷新报表缓存
        </el-button>
        <el-button type="primary" @click="openWindow('/report.html')">报表驾驶舱</el-button>
      </template>
      <el-button v-if="isAdmin" @click="router.push('/modules')">功能模块</el-button>
    </PageHeader>

    <el-alert
      v-if="auth.firstLogin && !embedded"
      type="warning"
      show-icon
      :closable="false"
      class="first-login"
    >
      <template #title>
        首次登录：请尽快修改初始密码（UC-07）
        <el-button link type="primary" size="small" @click="router.push('/change-password')">
          去修改 →
        </el-button>
      </template>
    </el-alert>

    <!-- 管理员：C1 裁决管理员暂不开放全局聚合报表，不请求教学统计端点 -->
    <el-alert v-if="isAdmin" type="info" show-icon :closable="false" class="admin-note">
      <template #title>
        管理工作台：管理员不参与教学链路统计（报表聚合端点仅对教师开放，C1 范围收敛）。
        可用的管理能力见「系统管理」与左侧教学/数据模块。
      </template>
    </el-alert>

    <template v-else>
      <AppSkeleton v-if="loading" :rows="2" :row-height="86" />
      <ErrorState v-else-if="loadError" title="报表加载失败" :message="loadError" @retry="loadOverview" />
      <EmptyState
        v-else-if="!stats.length"
        description="暂无任务数据（等教师发布任务后这里会亮起来）"
        :action-text="isTeacher ? '去任务管理' : '去任务中心'"
        @action="router.push(isTeacher ? '/tasks/manage' : '/tasks')"
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
    </template>

    <section v-if="!embedded" class="section">
      <div class="section-head">
        <h2>功能模块</h2>
        <span class="section-hint">点击进入模块；带 ↗ 的模块在新窗口打开</span>
      </div>
      <div class="quick-grid">
        <button v-for="c in cards" :key="c.title" type="button" class="quick-card" @click="openCard(c)">
          <span class="quick-icon"><NavIcon :name="c.icon" /></span>
          <span class="quick-text">
            <b>{{ c.title }}<i v-if="c.newWindow">↗</i></b>
            <small>{{ c.desc }}</small>
          </span>
          <span class="quick-arrow">→</span>
        </button>
      </div>
    </section>

    <section v-if="!embedded" class="account-line">
      <span>
        当前角色 <b class="role-name">{{ roleLabel }}</b> · 已授予权限
        {{ auth.me?.permissions?.length ?? 0 }} 项
      </span>
      <el-button link type="primary" @click="router.push('/account')">查看个人中心 →</el-button>
    </section>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import NavIcon from '@/components/ui/NavIcon.vue'
import { reportApi, type ReportOverview } from '@/api/report'
import { toast } from '@/utils/toast'
import { openWindow } from '@/utils/window'

interface QuickCard {
  title: string
  desc: string
  icon: string
  to: string
  newWindow?: boolean
}

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const loadError = ref<string | null>(null)
const overview = ref<ReportOverview | null>(null)
const refreshing = ref(false)

const isTeacher = computed(() => auth.role === 'TEACHER' || auth.role === 'ADMIN')
const isTeacherData = computed(() => auth.role === 'TEACHER')
const isAdmin = computed(() => auth.role === 'ADMIN')
const roleLabel = computed(() => {
  if (auth.role === 'ADMIN') return '管理员'
  if (auth.role === 'TEACHER') return '教师'
  return '学生'
})
const headerSubtitle = computed(() => {
  if (isAdmin.value) return '管理工作台 · 模块导览与系统管理'
  if (isTeacher.value) return '教师工作台 · 任务与评价总览'
  return '学习工作台 · 任务与提交总览'
})
const canRefreshReport = computed(() => (auth.me?.permissions ?? []).includes('report:refresh'))

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

/** 模块导览：与左侧导航同源的角色/权限口径 */
const cards = computed<QuickCard[]>(() => {
  if (!isTeacher.value) {
    return [
      { title: '任务中心', desc: '查看已发布任务、截止时间与指标集', icon: 'tasks', to: '/tasks' },
      { title: '提交档案', desc: '本人提交版本、解析状态与评价结果', icon: 'archive', to: '/submissions' },
      { title: '我的评价', desc: '已出分/已复核的评价报告入口', icon: 'evaluation', to: '/my-evaluations' },
      { title: '文件中心', desc: '分片上传产物清单与流式下载', icon: 'files', to: '/files' }
    ]
  }
  const list: QuickCard[] = [
    { title: '任务管理', desc: '创建任务、配置指标权重、发布与跟踪', icon: 'manage', to: '/tasks/manage' },
    { title: '评价复核', desc: '进入复核工作台：改分/重评/版本链', icon: 'review', to: '/review' }
  ]
  if (isTeacherData.value) {
    list.push({
      title: '报表驾驶舱',
      desc: '四图聚合、导出与缓存重建（新窗口）',
      icon: 'chart',
      to: '/report.html',
      newWindow: true
    })
  }
  list.push({ title: '文件中心', desc: '分片上传产物清单与流式下载', icon: 'files', to: '/files' })
  if (isAdmin.value) {
    list.push({
      title: '功能模块',
      desc: '用户/权限/班级/AI/审计（二级+三级目录）',
      icon: 'system',
      to: '/modules'
    })
  }
  return list
})

async function loadOverview() {
  if (isAdmin.value) return
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

function openCard(c: QuickCard) {
  if (c.newWindow) {
    openWindow(c.to)
    return
  }
  router.push(c.to)
}

onMounted(loadOverview)
</script>

<style scoped>
.first-login {
  margin-bottom: var(--aies-gap);
}
.admin-note {
  margin-bottom: var(--aies-gap);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--aies-gap);
}
@media (max-width: 1024px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.section {
  margin-top: var(--aies-gap-lg);
}
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.section-head h2 {
  margin: 0;
  font-size: 15px;
  color: var(--aies-ink);
}
.section-hint {
  font-size: 12px;
  color: var(--aies-ink3);
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 900px) {
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
.quick-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition:
    box-shadow var(--aies-t),
    transform var(--aies-t),
    border-color var(--aies-t);
}
.quick-card:hover {
  box-shadow: var(--aies-shadow-hover);
  transform: var(--aies-lift);
  border-color: var(--aies-brand);
}
.quick-icon {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 10px;
  background: var(--aies-brand-soft);
  color: var(--aies-brand);
  display: grid;
  place-items: center;
}
.quick-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.quick-text b {
  font-size: 14px;
  color: var(--aies-ink);
}
.quick-text b i {
  font-style: normal;
  font-size: 11px;
  color: var(--aies-ink3);
  margin-left: 4px;
}
.quick-text small {
  font-size: 12px;
  color: var(--aies-ink3);
}
.quick-arrow {
  color: var(--aies-ink3);
  font-size: 14px;
}
.account-line {
  margin-top: var(--aies-gap-lg);
  padding-top: var(--aies-gap);
  border-top: 1px dashed var(--aies-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12.5px;
  color: var(--aies-ink3);
  flex-wrap: wrap;
}
.role-name {
  color: var(--aies-ink);
  font-weight: 600;
}
</style>
