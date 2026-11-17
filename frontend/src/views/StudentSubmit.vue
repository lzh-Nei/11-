<template>
  <PageShell :width="860">
    <PageHeader
      title="提交作业"
      :subtitle="task.title ? `${task.title} · 上传并提交作业` : '上传并提交作业'"
    >
      <BackButton />
      <el-button @click="router.push('/tasks')">返回任务中心</el-button>
    </PageHeader>

    <ScrollArea max-height="calc(100vh - 220px)" padded>
      <AppSkeleton v-if="loading" :rows="2" :row-height="70" />
      <ErrorState v-else-if="error" :message="error" @retry="load" />
      <template v-else>
        <!-- 任务详情卡（TaskDetailCard 语义落位：S-05 真身渲染） -->
        <section class="detail-card">
          <div class="dc-head">
            <b>{{ task.title }}</b>
            <StatusTag :status="task.status" />
          </div>
          <p v-if="task.description" class="dc-desc">{{ task.description }}</p>
          <div class="dc-row">
            <span class="dc-label">截止</span>
            <DeadlineBadge :deadline="task.deadline" :status="task.status" />
          </div>
          <div class="dc-row">
            <span class="dc-label">评分指标（合计 100%）</span>
            <div class="dc-metrics">
              <span v-for="m in task.metrics" :key="m.id" class="metric-chip">
                {{ m.metricName }} <b>{{ m.weight }}%</b>
              </span>
            </div>
          </div>
        </section>

        <el-alert
          v-if="latestVersion > 0"
          :title="`当前最新版本 v${latestVersion}：再次上传将生成 v${latestVersion + 1}，旧版本会保留`"
          type="info"
          show-icon
          :closable="false"
          class="ver-tip"
        />

        <!-- 上传区图形交互（upload 铁律：进度/速度/取消/失败重试） -->
        <FileUploadZone
          accept=".docx,.pdf"
          idle-title="选择作业文件"
          :uploading="uploading"
          :failed="uploadFailed"
          :file-name="file?.name ?? ''"
          :percent="percent"
          :sent-bytes="sentBytes"
          :total-bytes="file?.size ?? 0"
          :bytes-per-second="speed"
          @pick="onPick"
          @cancel="onCancel"
          @retry-upload="onSubmit"
        >
        </FileUploadZone>

        <div class="actions">
          <ConfirmAction
            title="提交作业"
            message="提交后系统会自动检查并评分。本次提交会生成一个新版本；在完成 AI 评价前，可在提交档案中取消。确认提交？"
            confirm-text="确认提交"
            @execute="onConfirmSubmit"
          >
            <template #default="{ trigger, loading }">
              <el-button type="primary" :loading="loading" :disabled="!file || uploading" @click="trigger">
                提交作业
              </el-button>
            </template>
          </ConfirmAction>
          <span v-if="!file" class="dim">请先选择文件（仅 .docx / .pdf，≤ 20MB）</span>
        </div>

      </template>
    </ScrollArea>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { taskApi, type TaskInfo } from '@/api/task'
import { submissionApi } from '@/api/submission'
import { toast } from '@/utils/toast'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import FileUploadZone from '@/components/ui/FileUploadZone.vue'
import BackButton from '@/components/ui/BackButton.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import DeadlineBadge from '@/components/ui/DeadlineBadge.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import ConfirmAction from '@/components/ui/ConfirmAction.vue'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)

const loading = ref(false)
const error = ref<string | null>(null)
const task = ref<TaskInfo>({
  id: 0,
  title: '',
  description: '',
  teacherId: 0,
  classId: 0,
  deadline: '',
  status: '',
  publishTime: null,
  createTime: '',
  className: null,
  teacherName: null,
  metrics: []
})
const file = ref<File | null>(null)
const latestVersion = ref(0)

/* 直传进度态（axios onUploadProgress 真实回调） */
const uploading = ref(false)
const uploadFailed = ref(false)
const percent = ref(0)
const sentBytes = ref(0)
const speed = ref(0)
let speedTimer: number | undefined
let lastLoaded = 0
let uploadController: AbortController | null = null
let uploadCanceled = false

const MAX_BYTES = 20 * 1024 * 1024
const ALLOWED = ['docx', 'pdf']

async function load() {
  loading.value = true
  error.value = null
  try {
    task.value = await taskApi.detail(taskId)
    const mine = await submissionApi.mine(1, 1, taskId)
    if (mine.records.length > 0) latestVersion.value = mine.records[0].versionNo
  } catch (e) {
    error.value = (e as { message?: string })?.message || '网络或服务暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** UC-16 第2步 前端预校验（扩展名+大小，后端 S-07 二次校验兜底） */
function onPick(raw: File) {
  const ext = raw.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED.includes(ext)) {
    toast.error('格式不支持（仅 docx/pdf），请转标准格式后重试')
    return
  }
  if (raw.size > MAX_BYTES) {
    toast.error('文件超过 20MB 上限')
    return
  }
  file.value = raw
  uploading.value = false
  uploadFailed.value = false
  percent.value = 0
  sentBytes.value = 0
}

function trackSpeed(e: { loaded: number; total?: number }) {
  sentBytes.value = e.loaded
  if (e.total) percent.value = Math.round((e.loaded / e.total) * 100)
}

async function onSubmit() {
  if (!file.value) return
  uploading.value = true
  uploadFailed.value = false
  uploadCanceled = false
  lastLoaded = 0
  uploadController = new AbortController()
  speedTimer = window.setInterval(() => {
    speed.value = Math.max(0, sentBytes.value - lastLoaded)
    lastLoaded = sentBytes.value
  }, 1000)
  try {
    const vo = await submissionApi.submit(taskId, file.value, trackSpeed, uploadController.signal)
    toast.success(`提交成功：已生成第 v${vo.versionNo} 版，系统正在检查评分`)
    latestVersion.value = vo.versionNo
    file.value = null
    percent.value = 100
  } catch (e) {
    if ((e as { code?: string })?.code === 'ERR_CANCELED' || uploadCanceled) {
      if (!uploadCanceled) toast.warning('已取消本次上传')
      return
    }
    uploadFailed.value = true
    toast.error((e as { message?: string })?.message || '提交失败', '请重试')
  } finally {
    uploading.value = false
    window.clearInterval(speedTimer)
    uploadController = null
  }
}

function onCancel() {
  uploadCanceled = true
  uploadController?.abort()
  file.value = null
  uploading.value = false
  uploadFailed.value = false
  percent.value = 0
  sentBytes.value = 0
  speed.value = 0
  if (speedTimer !== undefined) window.clearInterval(speedTimer)
  toast.warning('已取消本次上传')
}

/** ConfirmAction 闭环：execute(done) → 真实提交 → done 收口 */
async function onConfirmSubmit(done: () => void) {
  await onSubmit()
  done()
}

onMounted(load)
</script>

<style scoped>
.detail-card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
.dc-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  color: var(--aies-ink);
}
.dc-desc {
  margin: 8px 0;
  font-size: 13px;
  color: var(--aies-ink2);
}
.dc-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.dc-label {
  font-size: 12px;
  color: var(--aies-ink3);
  flex: none;
}
.dc-metrics,
.metric-chip {
  display: inline-flex;
}
.metric-chip {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: var(--aies-radius-pill);
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  color: var(--aies-ink2);
  margin-right: 8px;
}
.metric-chip b {
  color: var(--aies-brand);
  font-family: var(--aies-mono, monospace);
}
.ver-tip {
  margin: var(--aies-gap) 0;
}
.actions {
  margin: var(--aies-gap) 0;
  display: flex;
  align-items: center;
  gap: 12px;
}
.dim {
  font-size: 12.5px;
  color: var(--aies-ink3);
}
</style>
