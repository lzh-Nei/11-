<template>
  <PageShell :width="860">
    <PageHeader title="评价报告" :subtitle="`UC-21 · 评价 #${route.params.id}（E-01 当前值）`">
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <ScrollArea max-height="calc(100vh - 220px)" padded>
      <AppSkeleton v-if="loading && !evaluation" :rows="3" :row-height="80" />
      <ErrorState v-else-if="error" :message="error" @retry="load" />

      <template v-else-if="evaluation">
        <!-- PENDING：评价进行中（10s 轮询，LLD §4.3 步骤7 学生端刷新口径） -->
        <EmptyState
          v-if="evaluation.status === 'PENDING'"
          description="AI 评价进行中，页面每 10 秒自动刷新（评价管道：解析 → qwen2.5:7b 评分 → 校验落库）"
        />

        <!-- FAILED：40003 重试耗尽（UC-24 教师人工评分旁路 E-06） -->
        <ErrorState
          v-else-if="evaluation.status === 'FAILED'"
          title="AI 评价失败（40003）"
          message="模型输出多次未通过格式校验，已登记留痕；教师可在复核工作台手动评分（UC-24 / E-06）。"
          :retryable="false"
        />

        <!-- AI_DONE / REVIEWED：报告主体 -->
        <template v-else>
          <section class="score-band">
            <div class="total-block">
              <ScoreDisplay :score="evaluation.totalScore" />
              <span class="total-label">总分（100分制）</span>
            </div>
            <div class="radar-block">
              <DimensionRadar :scores="metricRecord" :size="280" />
              <span class="radar-label">维度雷达（指标均分 / 100）</span>
            </div>
          </section>

          <section class="section">
            <h3>AI 评语</h3>
            <SuggestionList :comment="evaluation.aiComment" />
          </section>

          <el-descriptions :column="3" border class="meta">
            <el-descriptions-item label="评价状态"
              ><StatusTag :status="evaluation.status"
            /></el-descriptions-item>
            <el-descriptions-item label="生效版本">v{{ evaluation.currentVersionNo }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{
              formatDateTime(evaluation.updateTime)
            }}</el-descriptions-item>
            <el-descriptions-item v-if="evaluation.traceId" label="TraceID（教师可见）" :span="3">
              <span class="mono">{{ maskId(evaluation.traceId) }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="foot-actions">
            <ReportDownloadButton :submission-id="evaluation.submissionId" />
            <span class="dim">复核流转：教师确认/改分走 UC-22（E-02），历史版本 UC-25（E-04/E-05）</span>
          </div>

          <!-- 历史版本链（E-04/E-05）：权限矩阵授予 evaluation:history:view 的角色可见 -->
          <EvaluationHistoryPanel v-if="canViewHistory" :evaluation-id="Number(route.params.id)" />
        </template>
      </template>
    </ScrollArea>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { evaluationApi, type EvaluationVO } from '@/api/evaluation'
import { formatDateTime, maskId } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import ScoreDisplay from '@/components/ui/ScoreDisplay.vue'
import DimensionRadar from '@/components/ui/DimensionRadar.vue'
import SuggestionList from '@/components/ui/SuggestionList.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'
import ReportDownloadButton from '@/components/business/ReportDownloadButton.vue'
import EvaluationHistoryPanel from '@/components/business/EvaluationHistoryPanel.vue'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const error = ref<string | null>(null)
const evaluation = ref<EvaluationVO | null>(null)
let timer: number | undefined

/* 历史版本区按权限矩阵显隐（学生角色未授予 evaluation:history:view，见 D-36） */
const canViewHistory = computed(() => (auth.me?.permissions ?? []).includes('evaluation:history:view'))

/* 指标分字典 → 雷达（空值过滤为 0 保持形状） */
const metricRecord = computed<Record<string, number>>(() => {
  const src = evaluation.value?.metricScores ?? {}
  const out: Record<string, number> = {}
  for (const [k, v] of Object.entries(src)) out[k] = Number(v) || 0
  return out
})

async function load() {
  loading.value = true
  error.value = null
  try {
    evaluation.value = await evaluationApi.detail(Number(route.params.id))
    // PENDING → 10s 轮询；AI_DONE/REVIEWED/FAILED 停止轮询
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
onMounted(load)
onBeforeUnmount(() => {
  if (timer !== undefined) window.clearInterval(timer)
})
</script>

<style scoped>
.score-band {
  display: flex;
  gap: var(--aies-gap-lg);
  align-items: center;
  justify-content: space-around;
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
  flex-wrap: wrap;
}
.total-block,
.radar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.total-label,
.radar-label {
  font-size: 12.5px;
  color: var(--aies-ink3);
}
.section {
  margin-top: var(--aies-gap-lg);
}
.section h3 {
  font-size: 15px;
  color: var(--aies-ink);
  margin: 0 0 10px;
}
.meta {
  margin-top: var(--aies-gap-lg);
  --el-card-border-radius: var(--aies-radius-lg);
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12.5px;
}
.foot-actions {
  margin-top: var(--aies-gap-lg);
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
}
</style>
