<template>
  <div class="page">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-head">
          <span>提交作业（UC-16 / S-01）</span>
          <el-button size="small" @click="router.push('/tasks')">返回任务列表</el-button>
        </div>
      </template>

      <el-descriptions :column="2" border class="task-meta">
        <el-descriptions-item label="任务">{{ task.title }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ fmt(task.deadline) }}</el-descriptions-item>
        <el-descriptions-item label="指标集" :span="2">
          <el-tag v-for="m in task.metrics" :key="m.id" size="small" type="info" effect="plain" class="tag-gap">
            {{ m.metricName }} {{ m.weight }}%
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="latestVersion > 0"
        :title="`当前最新版本 v${latestVersion}：再次上传将生成 v${latestVersion + 1}（旧版本保留可追溯，UC-16 A1）`"
        type="info"
        show-icon
        :closable="false"
        class="ver-tip"
      />

      <!-- UC-16 第2步 前端预校验：扩展名 docx/pdf + ≤20MB（后端 S-07 二次校验兜底） -->
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        :on-change="onFileChange"
        :on-remove="() => (file = null)"
        accept=".docx,.pdf"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽文件或 <em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .docx / .pdf，≤ 20MB（超出将被拒绝：30001/30003）</div>
        </template>
      </el-upload>

      <div class="actions">
        <el-button type="primary" :loading="submitting" :disabled="!file" @click="onSubmit">
          {{ submitting ? '上传中…' : '提交作业' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { taskApi, type TaskInfo } from '@/api/task'
import { submissionApi } from '@/api/submission'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)

const loading = ref(false)
const submitting = ref(false)
const task = ref<TaskInfo>({ id: 0, title: '加载中', description: '', teacherId: 0, classId: 0, deadline: '', status: '', createTime: '', metrics: [] })
const file = ref<File | null>(null)
const latestVersion = ref(0)

const MAX_BYTES = 20 * 1024 * 1024
const ALLOWED = ['docx', 'pdf']

function fmt(s: string) {
  return s ? s.replace('T', ' ').slice(0, 16) : '—'
}

/** UC-16 第2步：前端预校验（扩展名+大小），不合法直接拦在浏览器 */
function onFileChange(uploadFile: { raw?: File }) {
  const raw = uploadFile.raw
  if (!raw) return
  const ext = raw.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ALLOWED.includes(ext)) {
    ElMessage.error('格式不支持（仅 docx/pdf），请转标准格式后重试')
    file.value = null
    return
  }
  if (raw.size > MAX_BYTES) {
    ElMessage.error('文件超过 20MB 上限')
    file.value = null
    return
  }
  file.value = raw
}

async function onSubmit() {
  if (!file.value) return
  submitting.value = true
  try {
    const vo = await submissionApi.submit(taskId, file.value)
    ElMessage.success(`提交成功：版本 v${vo.versionNo}，解析状态 ${vo.parseStatus}`)
    latestVersion.value = vo.versionNo
    file.value = null
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    task.value = await taskApi.detail(taskId)
    // 已有提交记录时提示版本（S-02 同任务最新版本）
    const mine = await submissionApi.mine(1, 1, taskId)
    if (mine.records.length > 0) {
      latestVersion.value = mine.records[0].versionNo
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  max-width: 860px;
  margin: 16px auto;
  padding: 0 12px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.task-meta {
  margin-bottom: 16px;
}
.ver-tip {
  margin-bottom: 16px;
}
.actions {
  margin-top: 16px;
}
.tag-gap {
  margin-right: 4px;
}
</style>
