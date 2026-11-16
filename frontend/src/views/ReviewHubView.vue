<template>
  <!-- 评价复核（教师模块）：复核工作台入口（按评价编号，新窗口）+ 本机最近打开记录
       后端暂无教师待复核列表端点（#14 发现项），如实说明，不伪造列表 -->
  <PageShell>
    <PageHeader title="评价复核" subtitle="复核工作台入口 · 改分 / 重评 / 版本链（UC-22/23/24/25）">
      <el-button @click="router.push('/tasks/manage')">去任务管理</el-button>
    </PageHeader>

    <el-alert type="info" show-icon :closable="false" class="tip">
      <template #title>
        复核工作台按「评价编号」进入并新窗口打开。教师端待复核列表接口尚未开放（#14 缺口已登记），
        本模块记录本机最近打开的评价编号，便于演示与回访。
      </template>
    </el-alert>

    <div class="entry-grid">
      <section class="card">
        <h3>进入复核工作台</h3>
        <p class="dim">评价编号来自报表驾驶舱 / 任务评价数据；输入后在新窗口打开（保留左右模块布局）。</p>
        <div class="entry-row">
          <el-input-number
            v-model="evalId"
            :min="1"
            :controls="false"
            placeholder="评价编号"
            style="width: 160px"
            @keyup.enter="enter"
          />
          <el-button type="primary" :disabled="!evalId" @click="enter">打开复核台 ↗</el-button>
        </div>
        <p class="hint">示例：评价 #1（演示数据）</p>
      </section>

      <section class="card">
        <div class="recent-head">
          <h3>最近打开</h3>
          <el-button v-if="recent.length" link type="danger" size="small" @click="clearRecent">
            清空
          </el-button>
        </div>
        <EmptyState v-if="!recent.length" description="还没有打开记录——从左侧输入评价编号进入复核台" />
        <ul v-else class="recent-list">
          <li v-for="r in recent" :key="r.id" class="recent-item">
            <button type="button" class="recent-main" @click="openReview(r.id)">
              <span class="mono">评价 #{{ r.id }}</span>
              <small>{{ formatDateTime(r.at) }}</small>
            </button>
            <el-button link type="primary" size="small" @click="openReview(r.id)">打开 ↗</el-button>
            <el-button link type="danger" size="small" @click="removeRecent(r.id)">移除</el-button>
          </li>
        </ul>
      </section>
    </div>

    <section class="card task-card">
      <div class="recent-head">
        <h3>我的任务（{{ tasks.length }}）</h3>
        <el-button link type="primary" size="small" @click="router.push('/tasks/manage')">
          进入任务管理 →
        </el-button>
      </div>
      <AppSkeleton v-if="tasksLoading" :rows="2" :row-height="48" />
      <table v-else-if="tasks.length" class="task-table">
        <thead>
          <tr>
            <th>任务</th>
            <th style="width: 120px">状态</th>
            <th style="width: 160px">截止时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks" :key="t.id">
            <td>{{ t.title }}</td>
            <td><StatusTag :status="t.status" /></td>
            <td class="dim">{{ formatDateTime(t.deadline) }}</td>
          </tr>
        </tbody>
      </table>
      <EmptyState v-else description="暂无任务——先在任务管理创建并发布任务" />
    </section>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { taskApi, type TaskInfo } from '@/api/task'
import { formatDateTime } from '@/utils/format'
import { openWindow } from '@/utils/window'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusTag from '@/components/ui/StatusTag.vue'

interface RecentReview {
  id: number
  at: string
}

const RECENT_KEY = 'aies_review_recent_v1'
const MAX_RECENT = 8

const router = useRouter()
const evalId = ref<number | null>(null)
const recent = ref<RecentReview[]>([])
const tasks = ref<TaskInfo[]>([])
const tasksLoading = ref(false)

function loadRecent() {
  try {
    const raw = localStorage.getItem(RECENT_KEY)
    recent.value = raw ? (JSON.parse(raw) as RecentReview[]) : []
  } catch {
    recent.value = []
  }
}

function saveRecent() {
  localStorage.setItem(RECENT_KEY, JSON.stringify(recent.value))
}

function remember(id: number) {
  recent.value = [{ id, at: new Date().toISOString() }, ...recent.value.filter((r) => r.id !== id)].slice(
    0,
    MAX_RECENT
  )
  saveRecent()
}

function openReview(id: number) {
  remember(id)
  openWindow(`/evaluations/${id}/review`)
}

function enter() {
  if (!evalId.value) return
  openReview(Number(evalId.value))
}

function removeRecent(id: number) {
  recent.value = recent.value.filter((r) => r.id !== id)
  saveRecent()
}

function clearRecent() {
  recent.value = []
  saveRecent()
}

async function loadTasks() {
  tasksLoading.value = true
  try {
    const result = await taskApi.list(1, 50)
    tasks.value = result.records
  } catch {
    tasks.value = []
  } finally {
    tasksLoading.value = false
  }
}

onMounted(() => {
  loadRecent()
  loadTasks()
})
</script>

<style scoped>
.tip {
  margin-bottom: var(--aies-gap);
}
.entry-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--aies-gap);
}
@media (max-width: 960px) {
  .entry-grid {
    grid-template-columns: 1fr;
  }
}
.card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
.card h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--aies-ink);
}
.dim {
  color: var(--aies-ink3);
}
p.dim {
  font-size: 12.5px;
  line-height: 1.7;
  margin: 0 0 14px;
}
.entry-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--aies-ink3);
}
.recent-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.recent-head h3 {
  margin-bottom: 0;
}
.recent-list {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.recent-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  background: var(--aies-paper);
}
.recent-main {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 10px;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
}
.recent-main small {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.task-card {
  margin-top: var(--aies-gap);
}
.task-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 10px;
}
.task-table th {
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: var(--aies-ink3);
  padding: 8px 10px;
  background: var(--aies-paper);
  border-bottom: 1px solid var(--aies-border);
}
.task-table td {
  padding: 9px 10px;
  border-bottom: 1px solid var(--aies-border);
  font-size: 13px;
  color: var(--aies-ink2);
}
.task-table tbody tr:last-child td {
  border-bottom: none;
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 13px;
}
</style>
