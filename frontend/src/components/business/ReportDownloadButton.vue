<template>
  <!-- 报告关联原件下载（ReportDownloadButton）：submissionId → 同源 Blob 下载（服务端签名URL流式返回） -->
  <el-button
    type="primary"
    plain
    size="small"
    :loading="loading"
    :disabled="!submissionId"
    @click="download(submissionId)"
  >
    下载提交原件
  </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { submissionApi } from '@/api/submission'
import { saveBlob } from '@/utils/file'
import { toast } from '@/utils/toast'

defineProps<{ submissionId: number | null | undefined }>()
const loading = ref(false)

async function download(submissionId: number | null | undefined) {
  if (!submissionId) return
  loading.value = true
  try {
    const detail = await submissionApi.detail(submissionId)
    const blob = await submissionApi.download(submissionId)
    saveBlob(blob, detail.fileName)
  } catch (e) {
    toast.error((e as { message?: string })?.message || '下载失败', '请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>
