<template>
  <PageShell :bare="embedded">
    <PageHeader
      v-if="!embedded"
      title="提交档案"
      subtitle="查看本人提交记录与评价结果"
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
        <el-table-column label="文件处理" width="110">
          <template #default="{ row }"><StatusTag :status="row.parseStatus" /></template>
        </el-table-column>
        <el-table-column label="评分状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.evaluationStatus" /></template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button
              size="small"
              text
              type="success"
              :disabled="!row.evaluationId"
              @click="openEvaluation(row)"
            >
              查看评价
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>

    <!-- UC-18 提交详情抽屉：签名URL下载 + 提取文本预览 -->
    <el-drawer v-model="drawer" title="提交详情" size="45%">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="文件名" :span="2">
          <FileLink
            :name="detail.fileName"
            :size="detail.fileSize"
            @download="download(detail.downloadUrl)"
          />
        </el-descriptions-item>
        <el-descriptions-item label="版本">v{{ detail.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ fileKind(detail.fileName) }}</el-descriptions-item>
        <el-descriptions-item label="文件处理"
          ><StatusTag :status="detail.parseStatus"
        /></el-descriptions-item>
        <el-descriptions-item label="评分状态"
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
          下载原件（链接 5 分钟内有效）
        </el-button>
      </div>

      <template v-if="detail?.textPreview">
        <div class="preview-title">作业文本预览（共 {{ detail.textChars }} 字，最多展示前 2000 字）</div>
        <el-input type="textarea" :model-value="detail.textPreview" :rows="14" readonly />
      </template>
      <el-empty v-else-if="detail" description="暂无可预览的文本（文件仍在处理或处理失败）" :image-size="80" />
    </el-drawer>

    <el-dialog
      v-model="evalDialog"
      :title="`评价结果 · ${evalTitle}`"
      width="760px"
      destroy-on-close
      class="eval-dialog"
    >
      <AppSkeleton v-if="evalLoading" :rows="3" :row-height="64" />
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
          :size="240"
          class="eval-radar"
        />
        <SuggestionList v-if="evaluation.aiComment" :comment="evaluation.aiComment" class="eval-comment" />
      </template>
      <el-empty v-else description="暂无评价结果" />
      <template #footer>
        <el-button
          v-if="evaluation"
          type="success"
          plain
          @click="openReport(evaluation.id)"
        >
          在新窗口查看完整报告 ↗
        </el-button>
        <el-button @click="evalDialog = false">关闭</el-button>
      </template>
    </el-dialog>
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
const evalDialog = ref(false)
const evalTitle = ref('')
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

function fileKind(name: string): string {
  const ext = name.split('.').pop()?.toUpperCase()
  return ext && ext !== name.toUpperCase() ? ext : '—'
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
  } catch (e) {
    toast.error((e as { message?: string })?.message || '详情加载失败', '请重试')
  }
}

async function openEvaluation(row: SubmissionMine) {
  if (!row.evaluationId) return
  evalTitle.value = row.fileName
  evalDialog.value = true
  await loadEvaluation(row.evaluationId)
}
onMounted(load)
</script>

<style scoped>
.drawer-actions {
  margin: var(--aies-gap) 0;
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
