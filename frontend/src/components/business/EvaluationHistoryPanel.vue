<template>
  <!-- 评价历史版本（E-04 游标分页 + E-05 单版本快照）
       独立容错：403/40301（E-04 归属校验或权限矩阵未授予）降级为提示，不影响主面板 -->
  <section class="history">
    <h3>{{ title }}</h3>
    <AppSkeleton v-if="loading && !items.length" :rows="3" />
    <el-alert
      v-else-if="denied"
      title="当前账号无权查看该评价的历史版本"
      type="info"
      show-icon
      :closable="false"
    />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <ScrollArea v-else-if="items.length" max-height="360px">
      <el-table :data="items" stripe size="small">
        <el-table-column prop="versionNo" label="版本" width="70">
          <template #default="{ row }"
            ><span class="mono">v{{ row.versionNo }}</span></template
          >
        </el-table-column>
        <el-table-column label="来源" width="120">
          <template #default="{ row }"><StatusTag :status="row.source" /></template>
        </el-table-column>        <el-table-column label="总分" width="90">
          <template #default="{ row }">
            <span class="mono">{{ row.totalScore ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="110">
          <template #default="{ row }">{{ row.reviewerName ?? '系统' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="详情" width="90">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openVersion(row.versionNo)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ScrollArea>
    <EmptyState v-else description="暂无历史记录（首次评价尚未生成）" />
    <div v-if="nextCursor" class="more">
      <el-button size="small" plain :loading="loadingMore" @click="loadMore">加载更早记录</el-button>
    </div>
  </section>

  <!-- E-05 版本快照抽屉 -->
  <el-drawer v-model="versionDrawer" :title="`版本详情 v${viewing?.versionNo ?? ''}`" size="40%">
    <AppSkeleton v-if="snapshotLoading" :rows="4" />
    <template v-else-if="viewing">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="版本来源">
          <StatusTag :status="viewing.source" />
        </el-descriptions-item>
        <el-descriptions-item label="总分">
          <span class="mono">{{ viewing.totalScore ?? '—' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="版本时间">{{ formatDateTime(viewing.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ viewing.reviewerName ?? '系统' }}</el-descriptions-item>
      </el-descriptions>
      <el-alert
        v-if="viewing.teacherComment"
        type="success"
        :closable="false"
        :title="`复核理由：${viewing.teacherComment}`"
        class="comment"
      />
      <DimensionRadar v-if="viewing.metricScores" :scores="viewMetric" :size="220" class="radar" />
      <SuggestionList v-if="viewing.aiComment" :comment="viewing.aiComment" class="comment" />
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { evaluationApi, type EvaluationHistoryItem } from '@/api/evaluation'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'
import DimensionRadar from '@/components/ui/DimensionRadar.vue'
import SuggestionList from '@/components/ui/SuggestionList.vue'

const props = withDefaults(defineProps<{ evaluationId: number; title?: string }>(), {
  title: '评价历史记录'
})

const loading = ref(false)
const loadingMore = ref(false)
const items = ref<EvaluationHistoryItem[]>([])
const nextCursor = ref<string | null>(null)
const denied = ref(false)
const error = ref<string | null>(null)
const versionDrawer = ref(false)
const snapshotLoading = ref(false)
const viewing = ref<EvaluationHistoryItem | null>(null)

const viewMetric = computed<Record<string, number>>(() => {
  const src = viewing.value?.metricScores ?? {}
  const out: Record<string, number> = {}
  for (const [k, v] of Object.entries(src)) out[k] = Number(v) || 0
  return out
})

function isDenied(e: unknown): boolean {
  const err = e as { code?: number; response?: { status?: number } }
  return err?.code === 40301 || err?.response?.status === 403
}

/** 首次装载：与主面板解耦，失败只降级本区 */
async function load() {
  loading.value = true
  denied.value = false
  error.value = null
  try {
    const page = await evaluationApi.history(props.evaluationId, undefined, 20)
    items.value = page.list ?? []
    nextCursor.value = page.nextCursor ?? null
  } catch (e) {
    items.value = []
    nextCursor.value = null
    if (isDenied(e)) denied.value = true
    else error.value = (e as { message?: string })?.message || '历史版本加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (!nextCursor.value) return
  loadingMore.value = true
  try {
    const page = await evaluationApi.history(props.evaluationId, nextCursor.value, 20)
    items.value = [...items.value, ...(page.list ?? [])]
    nextCursor.value = page.nextCursor ?? null
  } catch (e) {
    toast.error((e as { message?: string })?.message || '加载更早版本失败', '请重试')
  } finally {
    loadingMore.value = false
  }
}

/** E-05 单版本快照（只读） */
async function openVersion(versionNo: number) {
  versionDrawer.value = true
  snapshotLoading.value = true
  viewing.value = null
  try {
    viewing.value = await evaluationApi.historyVersion(props.evaluationId, versionNo)
  } catch (e) {
    versionDrawer.value = false
    toast.error((e as { message?: string })?.message || '历史详情加载失败', '请重试')
  } finally {
    snapshotLoading.value = false
  }
}

onMounted(load)
defineExpose({ reload: load })
</script>

<style scoped>
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
.more {
  margin-top: 10px;
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12.5px;
}
.radar {
  margin-top: 14px;
}
.comment {
  margin-top: 14px;
}
</style>
