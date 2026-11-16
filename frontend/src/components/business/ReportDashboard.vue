<template>
  <PageShell :bare="embedded">
    <PageHeader v-if="!embedded" title="报表驾驶舱" :subtitle="subtitle">
      <el-button v-if="canRefresh" plain :loading="refreshing" @click="refresh">
        刷新数据
      </el-button>
    </PageHeader>

    <div v-else class="embedded-head">
      <div>
        <h3>报表驾驶舱</h3>
        <p>{{ subtitle }}</p>
      </div>
      <el-button v-if="canRefresh" size="small" plain :loading="refreshing" @click="refresh">
        刷新数据
      </el-button>
    </div>

    <AppSkeleton v-if="loading" :rows="4" :row-height="90" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <EmptyState
      v-else-if="!taskRows.length"
      description="暂无报表数据——有任务和提交后这里会自动生成统计"
      :action-text="isTeacher ? '去任务列表' : '去任务中心'"
      @action="goTasks"
    />
    <template v-else>
      <div class="kpi-grid">
        <StatCard v-for="item in kpis" :key="item.label" :label="item.label" :value="item.value" />
      </div>

      <div class="chart-grid">
        <ChartPanel title="任务统计" hint="各任务提交数" :empty="!taskRows.length">
          <div class="bar-list">
            <div v-for="task in taskRows" :key="task.taskId" class="bar-row">
              <span class="bar-label" :title="task.title">{{ task.title }}</span>
              <div class="bar-track">
                <i :style="{ width: barWidth(task.submitted, maxSubmitted) }"></i>
              </div>
              <b class="bar-count">{{ task.submitted }}</b>
            </div>
          </div>
        </ChartPanel>

        <ChartPanel title="成绩分布" hint="按总分区间统计" :empty="!scoreDist.length">
          <div class="bar-list">
            <div v-for="item in scoreDist" :key="item.range" class="bar-row">
              <span class="bar-label">{{ item.range }}</span>
              <div class="bar-track">
                <i :style="{ width: barWidth(item.count, maxScoreDist) }"></i>
              </div>
              <b class="bar-count">{{ item.count }}</b>
            </div>
          </div>
        </ChartPanel>

        <ChartPanel
          title="指标雷达"
          hint="各指标平均分 / 100"
          :empty="!metricRadar.length"
          empty-text="暂无指标数据"
        >
          <div class="radar-wrap">
            <DimensionRadar :scores="radarScores" :size="280" />
          </div>
        </ChartPanel>

        <ChartPanel
          title="分差分布"
          hint="AI 与教师评分差值"
          :empty="!scoreGapDist.length"
          empty-text="暂无分差数据"
        >
          <div class="bar-list">
            <div v-for="item in scoreGapDist" :key="item.range" class="bar-row">
              <span class="bar-label">{{ item.range }}</span>
              <div class="bar-track">
                <i :style="{ width: barWidth(item.count, maxGapDist) }"></i>
              </div>
              <b class="bar-count">{{ item.count }}</b>
            </div>
          </div>
        </ChartPanel>
      </div>

      <ChartPanel title="任务明细" hint="提交、出分与平均分" :empty="!taskRows.length">
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
    </template>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { reportApi, type ReportOverview, type TaskStat } from '@/api/report'
import { toast } from '@/utils/toast'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatCard from '@/components/ui/StatCard.vue'
import ChartPanel from '@/components/ui/ChartPanel.vue'
import DimensionRadar from '@/components/ui/DimensionRadar.vue'

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const refreshing = ref(false)
const error = ref<string | null>(null)
const overview = ref<ReportOverview | null>(null)

const isTeacher = computed(() => auth.role === 'TEACHER')
const subtitle = computed(() =>
  isTeacher.value
    ? '汇总你所负责任务的提交、成绩分布、指标表现与评分差值'
    : '汇总你本人的提交、成绩分布、指标表现与评分差值'
)
const canRefresh = computed(() => (auth.me?.permissions ?? []).includes('report:refresh'))
const taskRows = computed(() => overview.value?.tasks ?? [])
const scoreDist = computed(() => overview.value?.scoreDist ?? [])
const metricRadar = computed(() => overview.value?.metricRadar ?? [])
const scoreGapDist = computed(() => overview.value?.scoreGapDist ?? [])
const radarScores = computed<Record<string, number>>(() =>
  Object.fromEntries(metricRadar.value.map((item) => [item.metricName, Number(item.avgScore) || 0]))
)
const kpis = computed(() => {
  const tasks = taskRows.value
  const submitted = tasks.reduce((sum, task) => sum + (Number(task.submitted) || 0), 0)
  const scored = tasks.reduce((sum, task) => sum + (Number(task.scored) || 0), 0)
  return [
    { label: isTeacher.value ? '任务总数' : '我的任务', value: tasks.length },
    { label: '提交总数', value: submitted },
    { label: '已出分', value: scored },
    { label: '待批阅', value: Math.max(submitted - scored, 0) }
  ]
})
const maxSubmitted = computed(() => Math.max(1, ...taskRows.value.map((task) => task.submitted)))
const maxScoreDist = computed(() => Math.max(1, ...scoreDist.value.map((item) => item.count)))
const maxGapDist = computed(() => Math.max(1, ...scoreGapDist.value.map((item) => item.count)))

function barWidth(value: number, max: number): string {
  return `${Math.max(4, Math.round((Number(value) / max) * 100))}%`
}

function scoreRate(row: TaskStat): string {
  const submitted = Number(row.submitted) || 0
  if (!submitted) return '—'
  return `${Math.round(((Number(row.scored) || 0) / submitted) * 100)}%`
}

function goTasks() {
  router.push(isTeacher.value ? '/modules?group=teaching&child=task-list' : '/modules?group=study&child=tasks')
}

async function load() {
  loading.value = true
  error.value = null
  try {
    overview.value = isTeacher.value ? await reportApi.overview() : await reportApi.overviewMe()
  } catch (e) {
    error.value = (e as { message?: string })?.message || '报表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function refresh() {
  refreshing.value = true
  try {
    await reportApi.refresh()
    await load()
    toast.success('报表已刷新')
  } catch (e) {
    toast.error((e as { message?: string })?.message || '刷新失败', '请稍后重试')
  } finally {
    refreshing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.embedded-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: var(--aies-gap);
  flex-wrap: wrap;
}
.embedded-head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--aies-ink);
}
.embedded-head p {
  margin: 5px 0 0;
  font-size: 12.5px;
  color: var(--aies-ink3);
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--aies-gap);
  margin-bottom: var(--aies-gap);
}
@media (max-width: 1024px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
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
  grid-template-columns: 110px 1fr 36px;
  align-items: center;
  gap: 10px;
}
.bar-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
.radar-wrap {
  display: flex;
  justify-content: center;
  padding: 4px 0;
}
</style>
