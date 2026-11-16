<template>
  <PageShell :width="860">
    <PageHeader title="提交作业" :subtitle="`UC-16 · 任务 #${taskId}（S-01 multipart 直传）`">
      <el-button @click="router.push('/tasks')">返回任务列表</el-button>
    </PageHeader>

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
          <span class="dc-label">指标集（AI 按 Σweight=100 评分）</span>
          <div class="dc-metrics">
            <span v-for="m in task.metrics" :key="m.id" class="metric-chip">
              {{ m.metricName }} <b>{{ m.weight }}%</b>
            </span>
          </div>
        </div>
      </section>

      <el-alert
        v-if="latestVersion > 0"
        :title="`当前最新版本 v${latestVersion}：再次上传将生成 v${latestVersion + 1}（旧版本保留可追溯，UC-16 A1）`"
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
          message="提交后立即触发 AI 解析与评价（此动作产生新版本记录，不可撤回）。确认提交？"
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

      <!-- 分片上传演示区（upload×5 真实承载：S-06 链路可视化——产物 fileId 不入提交表，桥接端点待补，见发现项台账） -->
      <el-collapse class="shard-demo">
        <el-collapse-item
          title="分片上传演示（S-06 upload×5 链路：init → chunk → corrupt → 重传 → complete）"
        >
          <div class="shard-actions">
            <el-button size="small" @click="demoBatch" :loading="batching"
              >U-04 小文件批量上传（batch）</el-button
            >
            <el-button size="small" @click="demoShard" :loading="sharding" :disabled="!file">
              走分片链上传当前选中文件（init/chunk/complete）
            </el-button>
            <el-button v-if="shardId && sharded" size="small" type="warning" plain @click="demoCorrupt">
              U-05 注入坏块（corrupt-chunk）
            </el-button>
          </div>
          <pre v-if="shardLog" class="shard-log">{{ shardLog }}</pre>
          <p class="dim" style="margin-top: 8px">
            说明：分片链真实上传至 MinIO（2MB/片、断点续传、MD5 坏块检测与定向重传闭环）。当前后端无
            fileId→提交记录 桥接端点（发现项 F-08 已登记），产物仅为文件存储，不产生提交版本。
          </p>
        </el-collapse-item>
      </el-collapse>
    </template>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { taskApi, type TaskInfo } from '@/api/task'
import { submissionApi } from '@/api/submission'
import { uploadApi } from '@/api/upload'
import { toast } from '@/utils/toast'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import FileUploadZone from '@/components/ui/FileUploadZone.vue'
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
  createTime: '',
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

/* 分片演示态 */
const sharding = ref(false)
const batching = ref(false)
const sharded = ref(false)
const shardId = ref('')
const shardLog = ref('')

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
  lastLoaded = 0
  speedTimer = window.setInterval(() => {
    speed.value = Math.max(0, sentBytes.value - lastLoaded)
    lastLoaded = sentBytes.value
  }, 1000)
  try {
    const vo = await submissionApi.submit(taskId, file.value, trackSpeed)
    toast.success(`提交成功：版本 v${vo.versionNo}，解析状态「${vo.parseStatus}」已进入 AI 评价管道`)
    latestVersion.value = vo.versionNo
    file.value = null
    percent.value = 100
  } catch (e) {
    uploadFailed.value = true
    toast.error((e as { message?: string })?.message || '提交失败', '请重试')
  } finally {
    uploading.value = false
    window.clearInterval(speedTimer)
  }
}

function onCancel() {
  file.value = null
  uploading.value = false
  percent.value = 0
  toast.warning('已取消本次选择')
}

/** ConfirmAction 闭环：execute(done) → 真实提交 → done 收口 */
async function onConfirmSubmit(done: () => void) {
  await onSubmit()
  done()
}

/* ── 分片链演示（真实上传 MinIO）── */
async function demoShard() {
  if (!file.value) return
  sharding.value = true
  sharded.value = false
  shardId.value = ''
  const f = file.value
  const log: string[] = []
  try {
    const init = await uploadApi.init(f.name, f.size, f.lastModified)
    shardId.value = init.uploadId
    log.push(`U-01 init → uploadId=${init.uploadId}，共 ${init.totalChunks} 片（2MB/片）`)
    for (let i = 0; i < init.totalChunks; i++) {
      const start = i * 2 * 1024 * 1024
      const blob = f.slice(start, Math.min(start + 2 * 1024 * 1024, f.size))
      const r = await uploadApi.chunk(init.uploadId, i, blob)
      log.push(`U-02 chunk#${i} → ok=${r.ok} md5=${r.md5 ?? '—'}`)
    }
    const done = await uploadApi.complete(init.uploadId)
    log.push(
      `U-03 complete → success=${done.success} fileId=${done.fileId ?? '—'} badChunks=[${done.badChunks.join(',')}]`
    )
    if (done.badChunks.length > 0) {
      for (const idx of done.badChunks) {
        const blob = f.slice(idx * 2 * 1024 * 1024, Math.min((idx + 1) * 2 * 1024 * 1024, f.size))
        await uploadApi.chunk(init.uploadId, idx, blob)
        log.push(`定向重传 chunk#${idx} → 完成`)
      }
      const retry = await uploadApi.complete(init.uploadId)
      log.push(`重新合并 → success=${retry.success} fileId=${retry.fileId ?? '—'}`)
      sharded.value = retry.success
    } else {
      sharded.value = done.success
    }
    shardLog.value = log.join('\n')
    toast.success('分片链演示完成（产物仅入 MinIO，不入提交表）')
  } catch (e) {
    log.push(`异常：${(e as { message?: string })?.message ?? '未知'}`)
    shardLog.value = log.join('\n')
    toast.error('分片演示失败', '详见日志')
  } finally {
    sharding.value = false
  }
}

async function demoCorrupt() {
  if (!shardId.value) return
  try {
    const r = await uploadApi.corruptChunk(shardId.value)
    shardLog.value = `${shardLog.value}\nU-05 corrupt-chunk → 已破坏分片（下次 complete 将报 badChunks=[${r.chunkIndex}]，再次"走分片链"可观察定向重传闭环）`
    toast.warning('坏块已注入——重新执行"走分片链上传"可观察检测与重传闭环')
  } catch (e) {
    toast.error((e as { message?: string })?.message || '注入失败')
  }
}

async function demoBatch() {
  batching.value = true
  try {
    const input = document.createElement('input')
    input.type = 'file'
    input.multiple = true
    input.onchange = async () => {
      const files = Array.from(input.files ?? [])
      if (!files.length) return
      const results = await uploadApi.batch(files)
      shardLog.value = `U-04 batch →\n${results.map((r) => `${r.fileName}: ok=${r.ok} fileId=${r.fileId ?? '—'}`).join('\n')}`
      toast.success(`批量上传完成：${results.filter((r) => r.ok).length}/${results.length} 成功`)
    }
    input.click()
  } finally {
    batching.value = false
  }
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
.shard-demo {
  margin-top: var(--aies-gap-lg);
}
.shard-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.shard-log {
  background: #171412;
  color: #e7e5e4;
  padding: 12px;
  border-radius: var(--aies-radius);
  font: 12px/1.6 var(--aies-mono, monospace);
  max-height: 220px;
  overflow: auto;
  margin-top: 10px;
  white-space: pre-wrap;
}
</style>
