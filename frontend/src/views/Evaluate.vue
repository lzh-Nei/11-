<template>
  <div class="page">
    <PageHeader title="复核工作台" :subtitle="`教师视角 · 评价 #${route.params.id}（UC-22/23/24/25）`">
      <el-button @click="router.push('/')">返回首页</el-button>
    </PageHeader>

    <AppSkeleton v-if="loading && !evaluation" :rows="3" :row-height="80" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />

    <template v-else-if="evaluation">
      <el-alert
        v-if="evaluation.status === 'PENDING'"
        title="评价进行中（AI 管道排队/评分中）——出分后本页 10 秒内自动刷新，可手动点刷新"
        type="info"
        show-icon
        :closable="false"
        class="tip"
      />

      <section class="band">
        <div class="band-left">
          <ScoreDisplay :score="evaluation.totalScore" />
          <StatusTag :status="evaluation.status" />
          <span class="dim">当前生效 v{{ evaluation.currentVersionNo }} · {{ formatDateTime(evaluation.updateTime) }}</span>
          <span v-if="evaluation.traceId" class="mono dim">trace {{ maskId(evaluation.traceId) }}</span>
        </div>
        <div class="band-actions">
          <el-button size="small" @click="load">刷新</el-button>
          <!-- E-03 重评：REVIEWED/FAILED→PENDING 走 MQ 重走管道（不可逆确认） -->
          <ConfirmAction
            v-if="evaluation.status === 'REVIEWED' || evaluation.status === 'FAILED'"
            title="触发重评"
            message="当前评分将被标记为 PENDING 并重新走 AI 评价管道（旧版本保留可追溯）。确认重评？"
            confirm-text="确认重评"
            @execute="onReEvaluate"
          >
            <template #default="{ trigger, loading }">
              <el-button size="small" type="warning" plain :loading="loading" @click="trigger">重评（E-03）</el-button>
            </template>
          </ConfirmAction>
        </div>
      </section>

      <template v-if="evaluation.status === 'AI_DONE' || evaluation.status === 'REVIEWED' || evaluation.status === 'FAILED'">
        <section class="review-grid">
          <!-- 左：AI 原判（只读参照） -->
          <div class="panel">
            <h3>AI 评价参照</h3>
            <DimensionRadar :scores="metricRecord" :size="250" />
            <SuggestionList :comment="evaluation.aiComment" />
          </div>

          <!-- 右：教师复核表单（ReviewWorkbench 语义落位） -->
          <div class="panel">
            <h3>{{ isManual ? '手动评分（E-06 / UC-24）' : '教师复核（E-02 / UC-22）' }}</h3>
            <el-form label-position="top">
              <el-form-item label="总分（0-100）" required>
                <el-input-number v-model="form.totalScore" :min="0" :max="100" :step="0.5" controls-position="right" style="width:140px" />
              </el-form-item>
              <el-form-item v-for="(v, k) in form.metricScores" :key="k" :label="`${k}（0-100）`">
                <el-input-number v-model="form.metricScores[k]" :min="0" :max="100" :step="0.5" controls-position="right" style="width:140px" size="small" />
              </el-form-item>
              <el-form-item label="复核意见（≥10 字，留痕进历史版本）" required>
                <el-input v-model="form.reviewComment" type="textarea" :rows="3" maxlength="300" show-word-limit
                  placeholder="说明改分/确认理由：依据、偏差、处理方式……" />
              </el-form-item>
              <ConfirmAction
                :title="isManual ? '提交手动评分' : '提交复核'"
                message="评分将写入新历史版本并触发报表缓存主动失效（D-36），旧版本保留可追溯。确认提交？"
                confirm-text="确认提交"
                @execute="onSubmitReview"
              >
                <template #default="{ trigger, loading }">
                  <el-button type="primary" :loading="loading" :disabled="!canSubmit" @click="trigger">
                    {{ isManual ? '提交手动评分' : '提交复核' }}
                  </el-button>
                </template>
              </ConfirmAction>
              <span v-if="form.reviewComment && form.reviewComment.length < 10" class="dim">意见不足 10 字（后端将拒绝）</span>
            </el-form>
          </div>
        </section>
      </template>

      <!-- 历史版本（E-04 分页 + E-05 单版本快照） -->
      <section class="history">
        <h3>评价历史（版本链可追溯）</h3>
        <el-table v-if="history.length" :data="history" stripe size="small">
          <el-table-column prop="versionNo" label="版本" width="70">
            <template #default="{ row }"><span class="mono">v{{ row.versionNo }}</span></template>
          </el-table-column>
          <el-table-column prop="source" label="来源" width="110" />
          <el-table-column label="总分" width="90">
            <template #default="{ row }">
              <span class="mono">{{ row.totalScore ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="changeReason" label="变更原因" min-width="180" show-overflow-tooltip />
          <el-table-column label="操作人" width="110">
            <template #default="{ row }">{{ row.operatorName ?? '系统' }}</template>
          </el-table-column>
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="快照" width="90">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="openVersion(row.versionNo)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState v-else description="暂无历史版本" />
        <div v-if="nextCursor" style="margin-top:10px">
          <el-button size="small" plain @click="loadHistory(nextCursor)">加载更早版本</el-button>
        </div>
      </section>
    </template>
  </div>

  <!-- E-05 版本快照抽屉 -->
  <el-drawer v-model="versionDrawer" :title="`版本快照 v${viewing?.currentVersionNo ?? ''}（E-05）`" size="40%">
    <el-descriptions v-if="viewing" :column="2" border>
      <el-descriptions-item label="状态"><StatusTag :status="viewing.status" /></el-descriptions-item>
      <el-descriptions-item label="总分"><span class="mono">{{ viewing.totalScore ?? '—' }}</span></el-descriptions-item>
    </el-descriptions>
    <DimensionRadar v-if="viewing?.metricScores" :scores="viewMetric" :size="220" style="margin-top:14px" />
    <SuggestionList v-if="viewing?.aiComment" :comment="viewing.aiComment" style="margin-top:14px" />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { evaluationApi, type EvaluationVO, type EvaluationHistoryItem } from '@/api/evaluation'
import { formatDateTime, maskId } from '@/utils/format'
import { toast } from '@/utils/toast'
import PageHeader from '@/components/ui/PageHeader.vue'
import ScoreDisplay from '@/components/ui/ScoreDisplay.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import DimensionRadar from '@/components/ui/DimensionRadar.vue'
import SuggestionList from '@/components/ui/SuggestionList.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import ConfirmAction from '@/components/ui/ConfirmAction.vue'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const loading = ref(false)
const error = ref<string | null>(null)
const evaluation = ref<EvaluationVO | null>(null)
const history = ref<EvaluationHistoryItem[]>([])
const nextCursor = ref<string | null>(null)
const versionDrawer = ref(false)
const viewing = ref<EvaluationVO | null>(null)
let timer: number | undefined

/* 复核表单：初始值镜像 AI 当前判（教师在此基础上改） */
const form = reactive<{ totalScore: number | null; metricScores: Record<string, number>; reviewComment: string }>({
  totalScore: null,
  metricScores: {},
  reviewComment: ''
})

const isManual = computed(() => evaluation.value?.status === 'FAILED')
const canSubmit = computed(
  () => form.totalScore != null && (form.reviewComment?.length ?? 0) >= 10
)

const metricRecord = computed<Record<string, number>>(() => {
  const src = evaluation.value?.metricScores ?? {}
  const out: Record<string, number> = {}
  for (const [k, v] of Object.entries(src)) out[k] = Number(v) || 0
  return out
})

const viewMetric = computed<Record<string, number>>(() => {
  const src = viewing.value?.metricScores ?? {}
  const out: Record<string, number> = {}
  for (const [k, v] of Object.entries(src)) out[k] = Number(v) || 0
  return out
})

function mirrorForm() {
  if (!evaluation.value) return
  form.totalScore = evaluation.value.totalScore
  const src = evaluation.value.metricScores ?? {}
  const rec: Record<string, number> = {}
  for (const [k, v] of Object.entries(src)) rec[k] = Number(v) || 0
  form.metricScores = rec
}

async function load() {
  loading.value = true
  error.value = null
  try {
    evaluation.value = await evaluationApi.detail(id)
    mirrorForm()
    if (evaluation.value.status === 'PENDING') {
      if (timer === undefined) timer = window.setInterval(load, 10000)
    } else if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
    await loadHistory()
  } catch (e) {
    error.value = (e as { message?: string })?.message || '网络或服务暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadHistory(cursor?: string) {
  const page = await evaluationApi.history(id, cursor, 20)
  history.value = cursor ? [...history.value, ...page.items] : page.items
  nextCursor.value = page.nextCursor
}

/** E-02 复核 / E-06 手动评分（ConfirmAction 闭环 → 表缓存主动失效 D-36） */
async function onSubmitReview(done: () => void) {
  if (!evaluation.value) return done()
  const payload = {
    totalScore: Number(form.totalScore),
    metricScores: Object.keys(form.metricScores).length ? form.metricScores : undefined,
    reviewComment: form.reviewComment
  }
  try {
    const vo = isManual.value ? await evaluationApi.manualScore(id, payload) : await evaluationApi.review(id, payload)
    evaluation.value = vo
    mirrorForm()
    toast.success(`已提交：生效 v${vo.currentVersionNo}，报表缓存已主动失效`)
    await loadHistory()
  } catch (e) {
    toast.error((e as { message?: string })?.message || '提交失败', '请检查意见长度与分数范围')
  } finally {
    done()
  }
}

/** E-03 重评（REVIEWED/FAILED→PENDING 走 MQ） */
async function onReEvaluate(done: () => void) {
  try {
    await evaluationApi.reEvaluate(id)
    toast.success('重评已触发：状态转 PENDING，AI 管道重新排队')
    await load()
  } catch (e) {
    toast.error((e as { message?: string })?.message || '重评触发失败', '请重试')
  } finally {
    done()
  }
}

/** E-05 单版本快照 */
async function openVersion(versionNo: number) {
  try {
    viewing.value = await evaluationApi.historyVersion(id, versionNo)
    versionDrawer.value = true
  } catch (e) {
    toast.error((e as { message?: string })?.message || '快照加载失败')
  }
}

onMounted(load)
onBeforeUnmount(() => {
  if (timer !== undefined) window.clearInterval(timer)
})
</script>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--aies-gap-lg) 16px 80px;
}
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
.history {
  margin-top: var(--aies-gap-lg);
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
.history h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--aies-ink);
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
