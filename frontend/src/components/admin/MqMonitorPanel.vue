<template>
  <!-- 消息队列监控（system:config）：队列水位 / 流水线 / 最近异步处理与失败重放 -->
  <section class="panel">
    <div class="head">
      <div>
        <h3>消息队列</h3>
        <p class="hint">
          观察文件解析与 AI 评价两条异步链路的队列水位、消费者与最近处理结果；监控为只读，失败记录可在表格中重新投递。
        </p>
      </div>
      <div class="tools">
        <el-switch v-model="autoRefresh" size="small" active-text="自动刷新" />
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <AppSkeleton v-if="loading && !data" :rows="4" :row-height="88" />
    <ErrorState v-else-if="error && !data" :message="error" @retry="load" />

    <template v-else-if="data">
      <el-alert
        v-if="alertMessages.length"
        :type="alertType"
        show-icon
        :closable="false"
        class="alert"
        :title="`队列告警：${alertMessages.join('；')}。请检查消费者与死信，并在下方按提交重放或排查。`"
      />

      <div class="hero" :class="data.rabbitConnected ? 'ok' : 'bad'">
        <span class="pulse" aria-hidden="true"></span>
        <div class="hero-main">
          <b>{{ data.rabbitConnected ? '消息服务运行中' : '消息服务不可用' }}</b>
          <span class="hero-sub">
            {{ data.broker }} · 采样于 {{ formatDateTime(data.sampledAt) }}
          </span>
        </div>
        <div class="hero-metrics">
          <div>
            <b>{{ totalReady }}</b>
            <span>待消费</span>
          </div>
          <div>
            <b>{{ totalConsumers }}</b>
            <span>消费者</span>
          </div>
          <div>
            <b :class="{ danger: deadLetters > 0 }">{{ deadLetters }}</b>
            <span>死信</span>
          </div>
          <div>
            <b>{{ stats.parseSuccess + stats.evaluationAiDone }}</b>
            <span>已完成</span>
          </div>
        </div>
      </div>

      <div class="queue-grid">
        <article v-for="q in data.queues" :key="q.key" class="queue-card">
          <header>
            <span class="dot" :class="queueTone(q)"></span>
            <b>{{ q.label }}</b>
            <el-tag size="small" :type="queueTagType(q)" effect="plain">{{ queueText(q) }}</el-tag>
          </header>
          <div class="queue-nums">
            <div>
              <b>{{ q.readyMessages ?? '—' }}</b>
              <span>{{ isDlq(q) ? '死信积压' : '待消费' }}</span>
            </div>
            <div>
              <b>{{ q.consumers ?? '—' }}</b>
              <span>消费者</span>
            </div>
          </div>
          <p class="mono queue-name">{{ q.queueName }}</p>
          <p class="route">
            <span class="mono">{{ q.exchange }}</span>
            <span class="arrow">→</span>
            <span class="mono">{{ q.routingKey }}</span>
          </p>
          <p v-if="!q.available" class="queue-error">{{ q.error || '队列不可用' }}</p>
          <p v-else-if="isDlq(q) && (q.readyMessages ?? 0) > 0" class="queue-error">
            存在死信，请在下方“最近异步处理”中按提交重放或排查
          </p>
        </article>
      </div>

      <section class="flow">
        <header class="section-head">
          <h4>异步处理链路</h4>
          <span>上传成功后自动解析，解析成功后自动触发 AI 评分</span>
        </header>
        <div class="flow-line">
          <div class="flow-node">
            <span class="node-ico submit"></span>
            <div>
              <b>学生提交文件</b>
              <small>事务提交后投递解析消息</small>
            </div>
          </div>
          <span class="flow-arrow">→</span>
          <div class="flow-node">
            <span class="node-ico parse"></span>
            <div>
              <b>文件解析队列</b>
              <small>待消费 {{ parseQueue?.readyMessages ?? '—' }} · 消费者 {{ parseQueue?.consumers ?? '—' }}</small>
            </div>
          </div>
          <span class="flow-arrow">→</span>
          <div class="flow-node">
            <span class="node-ico eval"></span>
            <div>
              <b>AI 评价队列</b>
              <small>待消费 {{ evalQueue?.readyMessages ?? '—' }} · 消费者 {{ evalQueue?.consumers ?? '—' }}</small>
            </div>
          </div>
          <span class="flow-arrow">→</span>
          <div class="flow-node">
            <span class="node-ico done"></span>
            <div>
              <b>评分与版本归档</b>
              <small>指标分、评语与模型留痕</small>
            </div>
          </div>
        </div>
      </section>

      <div class="stat-grid">
        <div v-for="s in statCards" :key="s.label" class="stat" :class="s.tone">
          <b>{{ s.value }}</b>
          <span>{{ s.label }}</span>
        </div>
      </div>

      <section class="events">
        <header class="section-head">
          <h4>最近异步处理</h4>
          <span>按最近提交时间展示；仅解析失败/评价失败可重放，已出分或已复核请走教师端重评</span>
        </header>
        <el-table :data="data.recentEvents" size="small" stripe empty-text="暂无提交记录">
          <el-table-column prop="fileName" label="文件" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.fileName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="studentName" label="学生" width="100">
            <template #default="{ row }">{{ row.studentName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="taskTitle" label="任务" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.taskTitle || '—' }}</template>
          </el-table-column>
          <el-table-column label="解析" width="92">
            <template #default="{ row }">
              <StatusTag v-if="row.parseStatus" :status="row.parseStatus" />
              <span v-else class="dim">—</span>
            </template>
          </el-table-column>
          <el-table-column label="评价" width="92">
            <template #default="{ row }">
              <StatusTag v-if="row.evaluationStatus" :status="row.evaluationStatus" />
              <span v-else class="dim">未进入</span>
            </template>
          </el-table-column>
          <el-table-column label="模型" width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="mono">{{ row.aiModel || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="总分" width="76" align="right">
            <template #default="{ row }">
              <span class="mono">{{ row.totalScore ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="118" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="canReplayParse(row)"
                size="small"
                text
                type="primary"
                :loading="replayingKey === `parse-${row.submissionId}`"
                @click.stop="replay('parse', row)"
              >
                重新解析
              </el-button>
              <el-button
                v-else-if="canReplayEvaluation(row)"
                size="small"
                text
                type="primary"
                :loading="replayingKey === `eval-${row.submissionId}`"
                @click.stop="replay('eval', row)"
              >
                {{ row.evaluationStatus ? '重新评价' : '发起评价' }}
              </el-button>
              <span v-else class="dim">—</span>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <p v-if="error && data" class="inline-error">{{ error }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type AdminMqEvent, type AdminMqMonitor, type AdminMqQueue } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import StatusTag from '@/components/ui/StatusTag.vue'

const REFRESH_MS = 5000

const loading = ref(false)
const error = ref<string | null>(null)
const data = ref<AdminMqMonitor | null>(null)
const autoRefresh = ref(false)
const replayingKey = ref('')
let timer: number | null = null

const stats = computed(() => data.value?.stats ?? {
  parseQueued: 0,
  parseSuccess: 0,
  parseFailed: 0,
  evaluationPending: 0,
  evaluationAiDone: 0,
  evaluationReviewed: 0,
  evaluationFailed: 0
})
const mainQueues = computed(() =>
  (data.value?.queues ?? []).filter((q) => q.key === 'parse' || q.key === 'eval')
)
const deadLetterQueues = computed(() =>
  (data.value?.queues ?? []).filter((q) => q.key.endsWith('Dlq'))
)
const totalReady = computed(() =>
  mainQueues.value.reduce((sum, q) => sum + (q.readyMessages ?? 0), 0)
)
const totalConsumers = computed(() =>
  mainQueues.value.reduce((sum, q) => sum + (q.consumers ?? 0), 0)
)
const deadLetters = computed(() =>
  deadLetterQueues.value.reduce((sum, q) => sum + (q.readyMessages ?? 0), 0)
)
const parseQueue = computed(() => data.value?.queues.find((q) => q.key === 'parse') ?? null)
const evalQueue = computed(() => data.value?.queues.find((q) => q.key === 'eval') ?? null)

const alertMessages = computed(() => data.value?.alert?.messages ?? [])
const alertType = computed<'warning' | 'error'>(() =>
  data.value?.alert?.level === 'ERROR' ? 'error' : 'warning'
)

function isDlq(q: AdminMqQueue): boolean {
  return q.key.endsWith('Dlq')
}

const statCards = computed(() => [
  { label: '解析排队', value: stats.value.parseQueued, tone: 'muted' },
  { label: '解析成功', value: stats.value.parseSuccess, tone: 'ok' },
  { label: '解析失败', value: stats.value.parseFailed, tone: 'err' },
  { label: '待 AI 评价', value: stats.value.evaluationPending, tone: 'warn' },
  { label: 'AI 已出分', value: stats.value.evaluationAiDone, tone: 'teal' },
  { label: '已复核归档', value: stats.value.evaluationReviewed, tone: 'ok' },
  { label: '评价失败', value: stats.value.evaluationFailed, tone: 'err' }
])

function queueText(q: AdminMqQueue): string {
  if (!q.available) return '不可用'
  if (isDlq(q)) return (q.readyMessages ?? 0) > 0 ? '需处理' : '无积压'
  if ((q.consumers ?? 0) === 0) return '无消费者'
  if ((q.readyMessages ?? 0) > 0) return '处理中'
  return '空闲'
}

function queueTone(q: AdminMqQueue): string {
  if (!q.available) return 'bad'
  if (isDlq(q)) return (q.readyMessages ?? 0) > 0 ? 'bad' : 'ok'
  if ((q.readyMessages ?? 0) > 0) return 'busy'
  return 'ok'
}

function queueTagType(q: AdminMqQueue): 'success' | 'warning' | 'danger' | 'info' {
  if (!q.available) return 'danger'
  if (isDlq(q)) return (q.readyMessages ?? 0) > 0 ? 'danger' : 'success'
  if ((q.consumers ?? 0) === 0) return 'warning'
  if ((q.readyMessages ?? 0) > 0) return 'warning'
  return 'success'
}

function canReplayParse(row: AdminMqEvent): boolean {
  return row.parseStatus === 'FAILED'
}

function canReplayEvaluation(row: AdminMqEvent): boolean {
  return row.parseStatus === 'SUCCESS'
    && (row.evaluationStatus === 'FAILED' || row.evaluationStatus == null)
}

async function replay(kind: 'parse' | 'eval', row: AdminMqEvent) {
  const actionName = kind === 'parse' ? '重新解析' : row.evaluationStatus ? '重新评价' : '发起评价'
  try {
    await ElMessageBox.confirm(
      `确认对「${row.fileName || '该文件'}」${actionName}？系统会重置对应状态并重新投递消息。`,
      '确认重放',
      { type: 'warning', confirmButtonText: actionName, cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  replayingKey.value = `${kind}-${row.submissionId}`
  try {
    if (kind === 'parse') {
      await adminApi.replayParse(row.submissionId)
    } else {
      await adminApi.replayEvaluation(row.submissionId)
    }
    ElMessage.success('已重新投递，稍后刷新查看处理结果')
    await load()
  } catch (e) {
    ElMessage.error((e as { message?: string })?.message || '重放失败，请稍后重试')
  } finally {
    replayingKey.value = ''
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    data.value = await adminApi.mqStatus()
  } catch (e) {
    error.value = (e as { message?: string })?.message || '消息队列状态加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function stopTimer() {
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
}

function syncTimer(on: boolean) {
  stopTimer()
  if (on) {
    timer = window.setInterval(load, REFRESH_MS)
  }
}

watch(autoRefresh, (on) => syncTimer(on))

onMounted(load)
onUnmounted(stopTimer)
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: var(--aies-gap);
}
.head h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--aies-ink);
}
.hint {
  margin: 0;
  max-width: 720px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--aies-ink3);
}
.tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}
.alert {
  margin-bottom: var(--aies-gap);
}
.hero {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  background: var(--aies-paper);
}
.hero.ok {
  border-color: #bbf7d0;
  background: linear-gradient(135deg, #f0fdf4, #f7f6f3 70%);
}
.hero.bad {
  border-color: #fecaca;
  background: linear-gradient(135deg, #fef2f2, #f7f6f3 70%);
}
.pulse {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: none;
  background: #16a34a;
  box-shadow: 0 0 0 4px rgba(22, 163, 74, 0.14);
}
.hero.bad .pulse {
  background: #dc2626;
  box-shadow: 0 0 0 4px rgba(220, 38, 38, 0.14);
}
.hero-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.hero-main b {
  font-size: 14.5px;
  color: var(--aies-ink);
}
.hero-sub {
  font-size: 12px;
  color: var(--aies-ink3);
  font-family: var(--aies-mono, monospace);
}
.hero-metrics {
  margin-left: auto;
  display: flex;
  gap: 26px;
  flex: none;
}
.hero-metrics div {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
}
.hero-metrics b {
  font: 600 20px var(--aies-mono, monospace);
  color: var(--aies-ink);
  line-height: 1.1;
}
.hero-metrics b.danger {
  color: #b91c1c;
}
.hero-metrics span {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.queue-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--aies-gap);
  margin-top: var(--aies-gap);
}
.queue-card {
  padding: 16px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  background: var(--aies-surface);
  box-shadow: var(--aies-shadow);
}
.queue-card header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.queue-card header b {
  font-size: 14px;
  color: var(--aies-ink);
}
.queue-card header .el-tag {
  margin-left: auto;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.dot.ok {
  background: #16a34a;
}
.dot.busy {
  background: #d97706;
}
.dot.bad {
  background: #dc2626;
}
.queue-nums {
  display: flex;
  gap: 28px;
}
.queue-nums div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.queue-nums b {
  font: 600 24px var(--aies-mono, monospace);
  color: var(--aies-ink);
  line-height: 1.1;
}
.queue-nums span {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.queue-name {
  margin: 12px 0 4px;
  font-size: 12px;
  color: var(--aies-ink2);
}
.route {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--aies-ink3);
  flex-wrap: wrap;
}
.arrow {
  color: #a8a29e;
}
.queue-error {
  margin: 8px 0 0;
  font-size: 12px;
  color: #b91c1c;
}
.flow,
.events {
  margin-top: var(--aies-gap);
  padding: 16px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  background: var(--aies-surface);
}
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.section-head h4 {
  margin: 0;
  font-size: 14px;
  color: var(--aies-ink);
}
.section-head span {
  font-size: 12px;
  color: var(--aies-ink3);
  text-align: right;
}
.flow-line {
  display: flex;
  align-items: stretch;
  gap: 10px;
}
.flow-node {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  background: var(--aies-paper);
}
.flow-node b {
  display: block;
  font-size: 13px;
  color: var(--aies-ink);
  margin-bottom: 3px;
}
.flow-node small {
  display: block;
  font-size: 11px;
  line-height: 1.5;
  color: var(--aies-ink3);
}
.node-ico {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex: none;
  margin-top: 3px;
}
.node-ico.submit {
  background: #78716c;
}
.node-ico.parse {
  background: #0f766e;
}
.node-ico.eval {
  background: #b45309;
}
.node-ico.done {
  background: #15803d;
}
.flow-arrow {
  align-self: center;
  color: #a8a29e;
  font-size: 16px;
  flex: none;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
  margin-top: var(--aies-gap);
}
.stat {
  padding: 12px 14px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  background: var(--aies-surface);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.stat b {
  font: 600 20px var(--aies-mono, monospace);
  color: var(--aies-ink);
  line-height: 1.1;
}
.stat span {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.stat.ok b {
  color: #15803d;
}
.stat.warn b {
  color: #b45309;
}
.stat.teal b {
  color: #0f766e;
}
.stat.err b {
  color: #b91c1c;
}
.stat.muted b {
  color: var(--aies-ink2);
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12px;
}
.dim {
  color: var(--aies-ink3);
  font-size: 12px;
}
.inline-error {
  margin-top: 10px;
  font-size: 12px;
  color: #b91c1c;
}
@media (max-width: 1180px) {
  .stat-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
  .flow-line {
    flex-wrap: wrap;
  }
  .flow-node {
    flex: 1 1 220px;
  }
  .flow-arrow {
    display: none;
  }
}
@media (max-width: 860px) {
  .queue-grid {
    grid-template-columns: 1fr;
  }
  .hero {
    flex-wrap: wrap;
  }
  .hero-metrics {
    margin-left: 0;
    width: 100%;
    justify-content: space-between;
  }
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
