<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-head">
          <span>我的提交记录（UC-17 / S-02，按任务分组）</span>
          <el-button size="small" @click="router.push('/')">返回首页</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="taskId" label="任务ID" width="80" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="版本" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">v{{ row.versionNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="解析状态" width="110">
          <template #default="{ row }">
            <el-tag :type="parseTag(row.parseStatus)" size="small">{{ row.parseStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评价状态" width="110">
          <template #default="{ row }">
            <el-tag :type="evalTag(row.evaluationStatus)" size="small">{{ row.evaluationStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
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
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="load"
      />
    </el-card>

    <!-- UC-18 提交详情抽屉：签名URL下载 + 提取文本预览 -->
    <el-drawer v-model="drawer" title="提交详情（S-03）" size="45%">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="文件名">{{ detail.fileName }}</el-descriptions-item>
        <el-descriptions-item label="版本">v{{ detail.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="大小">{{ (detail.fileSize / 1024).toFixed(1) }} KB</el-descriptions-item>
        <el-descriptions-item label="类型">{{ detail.fileType }}</el-descriptions-item>
        <el-descriptions-item label="解析状态">
          <el-tag :type="parseTag(detail.parseStatus)" size="small">{{ detail.parseStatus }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="评价状态">
          <el-tag :type="evalTag(detail.evaluationStatus)" size="small">{{ detail.evaluationStatus }}</el-tag>
        </el-descriptions-item>
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
        <!-- UC-21（W7-C2）：评价报告跳转（AI_DONE/REVIEWED/FAILED均可看：FAILED展示40003提示） -->
        <el-button
          type="success"
          size="small"
          :disabled="!detail?.evaluationId"
          @click="router.push(`/evaluations/${detail!.evaluationId}`)"
        >
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

const router = useRouter()
const loading = ref(false)
const rows = ref<SubmissionMine[]>([])
const page = ref(1)
const size = 20
const total = ref(0)
const drawer = ref(false)
const detail = ref<SubmissionDetail | null>(null)

function fmt(s: string) {
  return s ? s.replace('T', ' ').slice(0, 16) : '—'
}
function parseTag(s: string) {
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'QUEUED') return 'warning'
  return 'info'
}
function evalTag(s: string) {
  if (s === 'AI_DONE' || s === 'REVIEWED') return 'success'
  if (s === 'FAILED') return 'danger'
  return 'info'
}
function download(url: string) {
  // S-04：浏览器直接跳 302 签名URL（MinIO 私有桶直连）
  window.open(url, '_blank')
}

async function load() {
  loading.value = true
  try {
    const result = await submissionApi.mine(page.value, size)
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}
async function openDetail(id: number) {
  detail.value = await submissionApi.detail(id)
  drawer.value = true
}
onMounted(load)
</script>

<style scoped>
.page {
  max-width: 1080px;
  margin: 16px auto;
  padding: 0 12px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.drawer-actions {
  margin: 16px 0;
}
.preview-title {
  margin-bottom: 8px;
  font-weight: 600;
  color: #606266;
}
</style>
