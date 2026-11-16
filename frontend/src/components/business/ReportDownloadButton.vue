<template>
  <!-- 报告关联原件下载（ReportDownloadButton）：submissionId → S-03 签名URL（5min）→ 302 MinIO 直连 -->
  <el-button type="primary" plain size="small" :loading="loading" :disabled="!submissionId" @click="download(submissionId)">
    下载提交原件（签名URL 5分钟有效）
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { submissionApi } from '@/api/submission'
import { toast } from '@/utils/toast'

defineProps<{ submissionId: number | null | undefined }>()
const loading = ref(false)

async function download(submissionId: number | null | undefined) {
  if (!submissionId) return
  loading.value = true
  try {
    const detail = await submissionApi.detail(submissionId)
    window.open(detail.downloadUrl, '_blank')
  } catch (e) {
    toast.error((e as { message?: string })?.message || '下载失败', '请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>
