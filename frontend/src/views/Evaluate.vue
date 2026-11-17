<template>
  <PageShell :bare="embedded">
    <PageHeader v-if="!embedded" title="复核工作台" subtitle="查看评分、调整分数与历史版本">
      <BackButton />
    </PageHeader>

    <ScrollArea
      :passthrough="embedded"
      :max-height="embedded ? 'none' : 'calc(100vh - 220px)'"
      padded
    >
      <AppSkeleton v-if="loading && !evaluation" :rows="3" :row-height="80" />
      <ErrorState v-else-if="error" :message="error" @retry="load" />

      <template v-else-if="evaluation">
        <el-alert
          v-if="evaluation.status === 'PENDING'"
          title="AI 正在评价，页面会自动刷新，也可以手动刷新查看最新结果"
          type="info"
          show-icon
          :closable="false"
          class="tip"
        />

        <section class="band">
          <div class="band-left">
            <ScoreDisplay :score="evaluation.totalScore" />
            <StatusTag :status="evaluation.status" />
            <span class="dim"
              >当前生效 v{{ evaluation.currentVersionNo }} · {{ formatDateTime(evaluation.updateTime) }}</span
            >
            <span v-if="evaluation.reviewerName" class="dim">
              复核教师：{{ evaluation.reviewerName }}
            </span>
            <span v-else-if="evaluation.aiModel" class="dim">AI 模型：{{ evaluation.aiModel }}</span>
          </div>
          <div class="band-actions">
            <el-button size="small" @click="load">刷新</el-button>
            <!-- 重评：已复核/失败状态可重新发起 AI 评价 -->
            <ConfirmAction
              v-if="!readonly && (evaluation.status === 'REVIEWED' || evaluation.status === 'FAILED')"
              title="重新评价"
              message="当前评分将进入重新评价流程，旧版本保留可追溯。确认重新评价？"
              confirm-text="确认重新评价"
              @execute="onReEvaluate"
            >
              <template #default="{ trigger, loading }">
                <el-button size="small" type="warning" plain :loading="loading" @click="trigger"
                  >重新评价</el-button
                >
              </template>
            </ConfirmAction>
          </div>
        </section>

        <template
          v-if="
            evaluation.status === 'AI_DONE' ||
            evaluation.status === 'REVIEWED' ||
            evaluation.status === 'FAILED'
          "
        >
          <section class="review-grid" :class="{ readonly }">
            <!-- 左：AI 原判（只读参照） -->
            <div class="panel">
              <h3>AI 评价参照</h3>
              <DimensionRadar :scores="metricRecord" :size="250" />
              <SuggestionList :comment="evaluation.aiComment" />
            </div>

            <!-- 右：教师复核表单（只读模式隐藏） -->
            <div v-if="!readonly" class="panel">
              <h3>{{ isManual ? '手动评分' : '教师复核' }}</h3>
              <el-form label-position="top">
                <el-alert
                  v-if="scoreExceeded"
                  type="error"
                  show-icon
                  :closable="false"
                  class="score-alert"
                  :title="`总分超限：当前指标分合计 ${metricTotalText} 分，不能超过 100 分`"
                />
                <el-form-item label="总分（由指标分自动汇总，不可修改）">
                  <div class="total-readonly" :class="{ over: scoreExceeded }">
                    {{ metricTotalText }}
                  </div>
                </el-form-item>
                <el-form-item v-for="(_, k) in form.metricScores" :key="k" :label="`${k}（0-100）`">
                  <el-input-number
                    v-model="form.metricScores[k]"
                    :min="0"
                    :max="100"
                    :step="0.5"
                    controls-position="right"
                    style="width: 140px"
                    size="small"
                    @change="onMetricChange"
                  />
                </el-form-item>
                <el-form-item label="复核意见（不少于10字，会留入历史记录）" required>
                  <el-input
                    v-model="form.reviewComment"
                    type="textarea"
                    :rows="3"
                    maxlength="300"
                    show-word-limit
                    placeholder="说明改分或确认理由：依据、偏差、处理方式……"
                  />
                </el-form-item>
                <ConfirmAction
                  :title="isManual ? '提交手动评分' : '提交复核'"
                  message="评分将保存为新版本，旧版本保留可追溯。确认提交？"
                  confirm-text="确认提交"
                  @execute="onSubmitReview"
                >
                  <template #default="{ trigger, loading }">
                    <el-button type="primary" :loading="loading" :disabled="!canSubmit" @click="trigger">
                      {{ isManual ? '提交手动评分' : '提交复核' }}
                    </el-button>
                  </template>
                </ConfirmAction>
                <span v-if="form.reviewComment && form.reviewComment.length < 10" class="dim"
                  >意见不足 10 字</span
                >
              </el-form>
            </div>
          </section>
        </template>

        <!-- 历史版本（只读） -->
        <EvaluationHistoryPanel ref="historyPanel" :evaluation-id="id" />
      </template>
    </ScrollArea>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { evaluationApi, type EvaluationVO } from '@/api/evaluation'
import { taskApi } from '@/api/task'
import { formatDateTime } from '@/utils/format'
import { onRealtimeEvent } from '@/utils/realtime'
import { toast } from '@/utils/toast'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import BackButton from '@/components/ui/BackButton.vue'
import ScoreDisplay from '@/components/ui/ScoreDisplay.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'
import ConfirmAction from '@/components/ui/ConfirmAction.vue'
import EvaluationHistoryPanel from '@/components/business/EvaluationHistoryPanel.vue'

const props = withDefaults(
  defineProps<{ evaluationId?: number; embedded?: boolean; readonly?: boolean }>(),
  { evaluationId: undefined, embedded: false, readonly: false }
)

const route = useRoute()
const id = computed(() => props.evaluationId ?? Number(route.params.id))

const loading = ref(false)
const error = ref<string | null>(null)
const evaluation = ref<EvaluationVO | null>(null)
const historyPanel = ref<{ reload: () => Promise<void> } | null>(null)
let timer: number | undefined

/* 复核表单：指标分是唯一输入，总分由指标分自动汇总 */
const form = reactive<{
  metricScores: Record<string, number>
  reviewComment: string
}>({
  metricScores: {},
  reviewComment: ''
})

const isManual = computed(() => evaluation.value?.status === 'FAILED')
const metricTotal = computed(() =>
  Object.values(form.metricScores).reduce((sum, value) => sum + (Number(value) || 0), 0)
)
const metricTotalText = computed(() => String(Number(metricTotal.value.toFixed(2))))
const scoreExceeded = computed(() => metricTotal.value > 100)
const hasMetrics = computed(() => Object.keys(form.metricScores).length > 0)
const canSubmit = computed(
  () =>
    hasMetrics.value &&
    !scoreExceeded.value &&
    (form.reviewComment?.length ?? 0) >= 10
)

const metricRecord = computed<Record<string, number>>(() => {
  const src = evaluation.value?.metricScores ?? {}
  const out: Record<string, number> = {}
  for (const [k, v] of Object.entries(src)) out[k] = Number(v) || 0
  return out
})

function mirrorForm() {
  if (!evaluation.value) return
  const src = evaluation.value.metricScores ?? {}
  const rec: Record<string, number> = {}
  for (const [k, v] of Object.entries(src)) rec[k] = Number(v) || 0
  form.metricScores = rec
}

/** FAILED 评价可能没有指标快照：从任务指标集补齐 0 分输入项 */
async function ensureMetricTemplate() {
  if (hasMetrics.value || !evaluation.value?.taskId) return
  try {
    const task = await taskApi.detail(evaluation.value.taskId)
    const rec: Record<string, number> = {}
    for (const metric of task.metrics) rec[metric.metricName] = 0
    form.metricScores = rec
  } catch {
    // 指标加载失败时保持空态，提交按钮不可用
  }
}

function onMetricChange() {
  if (scoreExceeded.value) {
    toast.warning(`总分超限：当前指标分合计 ${metricTotalText.value} 分，不能超过100分，请调低指标分后再提交`)
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    evaluation.value = await evaluationApi.detail(id.value)
    mirrorForm()
    await ensureMetricTemplate()
    if (evaluation.value.status === 'PENDING') {
      if (timer === undefined) timer = window.setInterval(load, 10000)
    } else if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
  } catch (e) {
    error.value = (e as { message?: string })?.message || '网络或服务暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** E-02 复核 / E-06 手动评分（ConfirmAction 闭环 → 表缓存主动失效 D-36） */
async function onSubmitReview(done: () => void) {
  if (!evaluation.value) return done()
  if (scoreExceeded.value) {
    toast.warning(`总分超限：当前指标分合计 ${metricTotalText.value} 分，不能超过100分`)
    return done()
  }
  if (!hasMetrics.value) {
    toast.warning('暂无可评分的指标项，请刷新后重试')
    return done()
  }
  const payload = {
    totalScore: Number(metricTotal.value),
    metricScores: { ...form.metricScores },
    reviewComment: form.reviewComment
  }
  try {
    const vo = isManual.value
      ? await evaluationApi.manualScore(id.value, payload)
      : await evaluationApi.review(id.value, payload)
    evaluation.value = vo
    mirrorForm()
    toast.success(`已提交，当前生效版本 v${vo.currentVersionNo}，总分 ${vo.totalScore}`)
    await historyPanel.value?.reload()
    await load()
  } catch (e) {
    toast.error((e as { message?: string })?.message || '提交失败', '请检查意见长度与指标分')
  } finally {
    done()
  }
}

/** E-03 重评（REVIEWED/FAILED→PENDING 走 MQ） */
async function onReEvaluate(done: () => void) {
  try {
    await evaluationApi.reEvaluate(id.value)
    toast.success('已重新发起评价，请稍后刷新查看结果')
    await load()
  } catch (e) {
    toast.error((e as { message?: string })?.message || '重评触发失败', '请重试')
  } finally {
    done()
  }
}

const unsubscribeRealtime = onRealtimeEvent((event) => {
  if (event.type !== 'evaluation.updated') return
  const data = (event.data ?? {}) as { evaluationId?: number }
  if (data.evaluationId === id.value) {
    void load()
  }
})

onMounted(load)
onBeforeUnmount(() => {
  unsubscribeRealtime()
  if (timer !== undefined) window.clearInterval(timer)
})
</script>

<style scoped>
.tip {
  margin-bottom: var(--aies-gap);
}
.band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--aies-gap);
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: 14px var(--aies-gap-lg);
  flex-wrap: wrap;
}
.band-left {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.band-actions {
  display: flex;
  gap: 10px;
}
.review-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--aies-gap);
  margin-top: var(--aies-gap);
}
.review-grid.readonly {
  grid-template-columns: 1fr;
}
@media (max-width: 900px) {
  .review-grid {
    grid-template-columns: 1fr;
  }
}
.panel {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
.panel h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--aies-ink);
}
.score-alert {
  margin-bottom: var(--aies-gap);
}
.total-readonly {
  display: inline-flex;
  align-items: center;
  min-width: 140px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  background: var(--aies-paper);
  color: var(--aies-ink);
  font: 600 16px var(--aies-mono, monospace);
}
.total-readonly.over {
  border-color: #b91c1c;
  color: #b91c1c;
  background: #fef2f2;
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12.5px;
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
}
</style>
