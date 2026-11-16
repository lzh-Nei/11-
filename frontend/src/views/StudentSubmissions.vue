<template>
  <div class="page">
    <PageHeader title="提交记录" subtitle="学生视角：本人全部提交（S-02 服务端过滤 + S-03 详情）">
      <el-button @click="router.push('/')">返回首页</el-button>
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
            <el-button size="small" text type="success" :disabled="!row.evaluationId"
              @click="router.push(`/evaluations/${row.evaluationId}`)">
              评价报告
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>

    <!-- UC-18 提交详情抽屉：签名URL下载 + 提取文本预览 -->
    <el-drawer v-model="drawer" title="提交详情（S-03）" size="45%">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="文件名" :span="2">
          <FileLink :name="detail.fileName" :size="detail.fileSize" @download="download(detail.downloadUrl)" />
        </el-descriptions-item>
        <el-descriptions-item label="版本">v{{ detail.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.fileType }}</el-descriptions-item>
        <el-descriptions-item label="解析状态"><StatusTag :status="detail.parseStatus" /></el-descriptions-item>
        <el-descriptions-item label="评价状态"><StatusTag :status="detail.evaluationStatus" /></el-descriptions-item>
      </el-descriptions>

      <div class="drawer-actions">
        <el-button type="primary" size="small" :disabled="!detail?.downloadUrl" @click="download(detail!.downloadUrl)">
          下载原件（签名URL 5分钟有效）
        </el-button>
        <!-- UC-21（W7-C2）：评价报告跳转（FAILED 展示 40003 提示） -->
        <el-button type="success" size="small" :disabled="!detail?.evaluationId"
          @click="router.push(`/evaluations/${detail!.evaluationId}`)">
          查看评价报告
        </el-button>
      </div>

      <template v-if="detail?.textPreview">
        <div class="preview-title">提取文本预览（{{ detail.textChars }} 字符，展示前2000）</div>
        <el-input type="textarea" :model-value="detail.textPreview" :rows="14" readonly />
      </template>
      <el-empty v-else-if="detail" description="暂无提取文本（解析未完成或失败）" :image-size="80" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { submissionApi, type SubmissionMine, type SubmissionDetail } from '@/api/submission'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import PageHeader from '@/components/ui/PageHeader.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import FileLink from '@/components/ui/FileLink.vue'

const router = useRouter()
const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<SubmissionMine[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const drawer = ref(false)
const detail = ref<SubmissionDetail | null>(null)

function download(url: string) {
  // S-04：浏览器直接跳 302 签名URL（MinIO 私有桶直连）
  window.open(url, '_blank')
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
onMounted(load)
</script>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
  padding: var(--aies-gap-lg) 16px 80px;
}
.drawer-actions {
  margin: var(--aies-gap) 0;
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
