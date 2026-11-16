<template>
  <PageShell :bare="embedded">
    <PageHeader
      v-if="!embedded"
      title="提交档案"
      subtitle="学生视角：本人全部提交（S-02 服务端过滤 + S-03 详情）"
    >
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </PageHeader>

    <DataTableShell
      :loading="loading"
      :error="error"
      :rows="rows"
      :total="total"
      :page="page"
      :page-size="size"
      empty-text="还没有提交记录——去任务列表交第一份作业"
      empty-action-text="去我的任务"
      @retry="load"
      @page-change="(p) => ((page = p), load())"
      @empty-action="router.push('/tasks')"
    >
      <el-table :data="rows" stripe>
        <el-table-column label="文件" min-width="220">
          <template #default="{ row }">
            <FileLink :name="row.fileName" :size="row.fileSize" />
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }">
            <span class="mono">v{{ row.versionNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="解析状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.parseStatus" /></template>
        </el-table-column>
        <el-table-column label="评价状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.evaluationStatus" /></template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button
              size="small"
              text
              type="success"
              :disabled="!row.evaluationId"
              @click="openReport(row.evaluationId)"
            >
              评价报告 ↗
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>

    <!-- UC-18 提交详情抽屉：签名URL下载 + 提取文本预览 -->
    <el-drawer v-model="drawer" title="提交详情（S-03）" size="45%">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="文件名" :span="2">
          <FileLink
            :name="detail.fileName"
            :size="detail.fileSize"
            @download="download(detail.downloadUrl)"
          />
        </el-descriptions-item>
        <el-descriptions-item label="版本">v{{ detail.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.fileType }}</el-descriptions-item>
        <el-descriptions-item label="解析状态"
          ><StatusTag :status="detail.parseStatus"
        /></el-descriptions-item>
        <el-descriptions-item label="评价状态"
          ><StatusTag :status="detail.evaluationStatus"
        /></el-descriptions-item>
      </el-descriptions>

      <div class="drawer-actions">
        <el-button
          type="primary"
          size="small"
          :disabled="!detail?.downloadUrl"
          @click="download(detail!.downloadUrl)"
        >
          下载原件（签名URL 5分钟有效）
        </el-button>
        <!-- UC-21（W7-C2）：评价报告下钻页（新窗口，保留左右模块布局） -->
        <el-button
          type="success"
          size="small"
          :disabled="!detail?.evaluationId"
          @click="openReport(detail!.evaluationId)"
        >
          查看评价报告 ↗
        </el-button>
      </div>

      <!-- UC-18/21：评价结果内嵌（E-01 真实数据，完整报告另开页） -->
      <section v-if="detail?.evaluationId" class="eval-block">
        <div class="eval-head">
          <h4>评价结果（E-01 · 当前生效）</h4>
          <el-button link type="primary" @click="openReport(detail.evaluationId)"> 完整报告 ↗ </el-button>
        </div>
        <AppSkeleton v-if="evalLoading" :rows="2" :row-height="56" />
        <ErrorState v-else-if="evalError" title="评价加载失败" :message="evalError" :retryable="false" />
        <template v-else-if="evaluation">
          <div class="eval-band">
            <ScoreDisplay :score="evaluation.totalScore" />
            <StatusTag :status="evaluation.status" />
            <span class="dim"
              >版本 v{{ evaluation.currentVersionNo }} · {{ formatDateTime(evaluation.updateTime) }}</span
            >
          </div>
          <DimensionRadar
            v-if="evaluation.metricScores"
            :scores="metricRecord"
            :size="220"
            class="eval-radar"
          />
          <SuggestionList v-if="evaluation.aiComment" :comment="evaluation.aiComment" class="eval-comment" />
        </template>
      </section>

      <template v-if="detail?.textPreview">
        <div class="preview-title">提取文本预览（{{ detail.textChars }} 字符，展示前2000）</div>
        <el-input type="textarea" :model-value="detail.textPreview" :rows="14" readonly />
      </template>
      <el-empty v-else-if="detail" description="暂无提取文本（解析未完成或失败）" :image-size="80" />
    </el-drawer>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { submissionApi, type SubmissionMine, type SubmissionDetail } from '@/api/submission'
import { evaluationApi, type EvaluationVO } from '@/api/evaluation'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import { openWindow } from '@/utils/window'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import FileLink from '@/components/ui/FileLink.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import ScoreDisplay from '@/components/ui/ScoreDisplay.vue'
import DimensionRadar from '@/components/ui/DimensionRadar.vue'
import SuggestionList from '@/components/ui/SuggestionList.vue'

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const router = useRouter()
const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<SubmissionMine[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const drawer = ref(false)
const detail = ref<SubmissionDetail | null>(null)
const evaluation = ref<EvaluationVO | null>(null)
const evalLoading = ref(false)
const evalError = ref<string | null>(null)

/* 指标分字典 → 雷达（空值过滤为 0 保持形状） */
const metricRecord = computed<Record<string, number>>(() => {
  const src = evaluation.value?.metricScores ?? {}
  const out: Record<string, number> = {}
  for (const [k, v] of Object.entries(src)) out[k] = Number(v) || 0
  return out
})

/** 评价内嵌区独立容错：E-01 失败只降级本区，不阻塞提交详情 */
async function loadEvaluation(evaluationId: number | null) {
  evaluation.value = null
  evalError.value = null
  if (!evaluationId) return
  evalLoading.value = true
  try {
    evaluation.value = await evaluationApi.detail(evaluationId)
  } catch (e) {
    evalError.value = (e as { message?: string })?.message || '评价结果加载失败，请稍后重试'
  } finally {
    evalLoading.value = false
  }
}

function download(url: string) {
  // S-04：浏览器直接跳 302 签名URL（MinIO 私有桶直连）
  window.open(url, '_blank')
}

/** 评价报告为下钻页：新窗口打开（同一左右布局外壳） */
function openReport(evaluationId: number | null) {
  if (!evaluationId) return
  openWindow(`/evaluations/${evaluationId}`)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await submissionApi.mine(page.value, size)
    rows.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '网络或服务暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}
async function openDetail(id: number) {
  try {
    detail.value = await submissionApi.detail(id)
    drawer.value = true
    await loadEvaluation(detail.value.evaluationId)
  } catch (e) {
    toast.error((e as { message?: string })?.message || '详情加载失败', '请重试')
  }
}
onMounted(load)
</script>

<style scoped>
.drawer-actions {
  margin: var(--aies-gap) 0;
}
.eval-block {
  margin: var(--aies-gap) 0;
  padding: var(--aies-gap);
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
}
.eval-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.eval-head h4 {
  margin: 0;
  font-size: 14px;
  color: var(--aies-ink);
}
.eval-band {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.eval-radar {
  margin: 10px auto 0;
}
.eval-comment {
  margin-top: 10px;
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
}
.preview-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--aies-ink2);
}
.mono {
  font-family: var(--aies-mono, monospace);
}
</style>
