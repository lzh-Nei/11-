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
        <el-button type="primary" @click="router.push('/modules?group=analysis&child=report')">
          报表驾驶舱
        </el-button>
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
        首次登录：请尽快修改初始密码
        <el-button link type="primary" size="small" @click="router.push('/change-password')">
          去修改 →
        </el-button>
      </template>
    </el-alert>

    <!-- 管理员：C1 裁决管理员暂不开放全局聚合报表，不请求教学统计端点 -->
    <el-alert v-if="isAdmin" type="info" show-icon :closable="false" class="admin-note">
      <template #title>
        管理工作台：集中处理用户、权限、班级与系统运行配置。
      </template>
    </el-alert>

    <template v-else>
      <AppSkeleton v-if="loading" :rows="2" :row-height="86" />
      <ErrorState v-else-if="loadError" title="报表加载失败" :message="loadError" @retry="loadOverview" />
      <EmptyState
        v-else-if="!stats.length"
        description="暂无任务数据（等教师发布任务后这里会亮起来）"
        :action-text="isTeacher ? '去任务列表' : '去任务中心'"
        @action="router.push(isTeacher ? '/modules?group=teaching&child=task-list' : '/tasks')"
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

      <section v-if="overview" class="analysis-block">
        <p class="analysis-desc">{{ analysisDesc }}</p>
        <div class="chart-grid">
          <ChartPanel
            title="成绩分布"
            hint="按总分区间统计"
            :empty="!scoreDist.length"
            empty-text="还没有成绩分布数据"
          >
            <div class="bar-list">
              <div v-for="item in scoreDist" :key="item.range" class="bar-row">
                <span class="bar-label">{{ item.range }}</span>
                <div class="bar-track">
                  <i :style="{ width: barWidth(item.count) }"></i>
                </div>
                <b class="bar-count">{{ item.count }}</b>
              </div>
            </div>
          </ChartPanel>

          <ChartPanel
            title="各指标平均分"
            hint="满分 100"
            :empty="!metricRadar.length"
            empty-text="还没有指标得分数据"
          >
            <DimensionRadar :scores="radarScores" :size="270" />
          </ChartPanel>
        </div>

        <ChartPanel
          title="任务表现"
          hint="提交、出分与平均分"
          :empty="!taskRows.length"
          empty-text="还没有任务表现数据"
        >
          <el-table :data="taskRows" stripe size="small">
            <el-table-column prop="title" label="任务" min-width="180" />
            <el-table-column label="提交" width="80">
              <template #default="{ row }">{{ row.submitted }}</template>
            </el-table-column>
            <el-table-column label="已出分" width="90">
              <template #default="{ row }">{{ row.scored }}</template>
            </el-table-column>
            <el-table-column label="平均分" width="90">
              <template #default="{ row }">
                {{ row.avgScore == null ? '—' : Number(row.avgScore).toFixed(1) }}
              </template>
            </el-table-column>
            <el-table-column label="出分率" width="100">
              <template #default="{ row }">{{ scoreRate(row) }}</template>
            </el-table-column>
          </el-table>
        </ChartPanel>
      </section>
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
            <b>{{ c.title }}</b>
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
import ChartPanel from '@/components/ui/ChartPanel.vue'
import DimensionRadar from '@/components/ui/DimensionRadar.vue'
import { reportApi, type ReportOverview } from '@/api/report'
import { toast } from '@/utils/toast'

interface QuickCard {
  title: string
  desc: string
  icon: string
  to: string
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

/* 统计卡派生（ReportOverviewDTO 无顶层标量，由 tasks 数组聚合） */
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

const scoreDist = computed(() => overview.value?.scoreDist ?? [])
const taskRows = computed(() => overview.value?.tasks ?? [])
const metricRadar = computed(() => overview.value?.metricRadar ?? [])
const radarScores = computed<Record<string, number>>(() =>
  Object.fromEntries(metricRadar.value.map((item) => [item.metricName, Number(item.avgScore) || 0]))
)
const scoreMax = computed(() => Math.max(1, ...scoreDist.value.map((item) => item.count)))
const analysisDesc = computed(() => {
  if (isTeacher.value) {
    return '以下数据汇总你所负责任务的提交、出分、成绩分布与指标表现，可结合任务表现表定位需要关注的学生与指标。'
  }
  return '以下数据来自你本人在各任务中的提交与评价记录，展示成绩分布、各指标平均分与任务完成情况；切换任务后会自动刷新。'
})

function barWidth(count: number): string {
  return `${Math.max(4, Math.round((count / scoreMax.value) * 100))}%`
}

function scoreRate(row: { submitted: number; scored: number }): string {
  const submitted = Number(row.submitted) || 0
  if (!submitted) return '—'
  return `${Math.round((Number(row.scored) || 0) / submitted * 100)}%`
}

/** 模块导览：与左侧导航同源的角色/权限口径 */
const cards = computed<QuickCard[]>(() => {
  if (!isTeacher.value) {
    return [
      { title: '任务中心', desc: '查看已发布任务、截止时间与评分指标', icon: 'tasks', to: '/tasks' },
      {
        title: '提交档案',
        desc: '本人提交版本、文件处理状态与评价结果',
        icon: 'archive',
        to: '/submissions'
      }
    ]
  }
  const list: QuickCard[] = [
    {
      title: '任务列表',
      desc: '查看、筛选与发布实训任务',
      icon: 'manage',
      to: '/modules?group=teaching&child=task-list'
    },
    { title: '评价与复核', desc: '查看评分、调整分数与历史版本', icon: 'review', to: '/review' }
  ]
  if (isTeacherData.value) {
    list.push({
      title: '报表驾驶舱',
      desc: '查看教学统计、四类图表与任务表现',
      icon: 'chart',
      to: '/modules?group=analysis&child=report'
    })
  }
  list.push({ title: '文件中心', desc: '查看已上传文件并下载', icon: 'files', to: '/files' })
  if (isAdmin.value) {
    list.push({
      title: '功能模块',
      desc: '用户、权限、班级、AI 与审计等管理功能',
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
.analysis-block {
  margin-top: var(--aies-gap-lg);
}
.analysis-desc {
  margin: 0 0 var(--aies-gap);
  font-size: 13px;
  line-height: 1.8;
  color: var(--aies-ink2);
}
.chart-grid {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: var(--aies-gap);
  margin-bottom: var(--aies-gap);
}
@media (max-width: 960px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
.bar-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}
.bar-row {
  display: grid;
  grid-template-columns: 72px 1fr 36px;
  align-items: center;
  gap: 10px;
}
.bar-label {
  font-size: 12.5px;
  color: var(--aies-ink3);
}
.bar-track {
  height: 10px;
  border-radius: 999px;
  background: var(--aies-paper);
  overflow: hidden;
}
.bar-track i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--aies-brand);
  transition: width var(--aies-t);
}
.bar-count {
  font: 600 12px var(--aies-mono, monospace);
  color: var(--aies-ink2);
  text-align: right;
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
