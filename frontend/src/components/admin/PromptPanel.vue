<template>
  <!-- Prompt 模板（prompt:manage，UC-38）：版本列表/查看/新建版本/唯一启用版切换 -->
  <section class="panel">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="tip"
      title="模板必须包含 {metrics} 与 {parsed_text} 两个占位符；全局仅一个启用版（启用新版本会自动停用旧版）。新建版本默认不启用。"
    />

    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建模板版本</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <span class="dim">共 {{ list.length }} 个版本</span>
    </div>

    <AppSkeleton v-if="loading" :rows="4" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <div v-else class="table-card">
      <ScrollArea max-height="calc(100vh - 460px)">
        <table class="prompt-table">
          <thead>
            <tr>
              <th style="width: 90px">版本</th>
              <th style="width: 110px">状态</th>
              <th style="width: 110px">内容长度</th>
              <th>内容预览</th>
              <th style="width: 160px">创建时间</th>
              <th style="width: 160px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in list" :key="p.id">
              <td>
                <b class="mono">v{{ p.versionNo }}</b>
              </td>
              <td>
                <el-tag :type="p.enabled === 1 ? 'success' : 'info'" size="small">
                  {{ p.enabled === 1 ? '启用中' : '未启用' }}
                </el-tag>
              </td>
              <td class="dim">{{ p.contentLength }} 字</td>
              <td>
                <span class="preview">{{ p.contentPreview }}</span>
              </td>
              <td class="dim">{{ formatDateTime(p.createTime) }}</td>
              <td>
                <el-button size="small" text type="primary" @click="view(p.id)">查看</el-button>
                <el-button size="small" text type="success" :disabled="p.enabled === 1" @click="enable(p)">
                  启用
                </el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </ScrollArea>
      <EmptyState
        v-if="!list.length"
        description="还没有 Prompt 模板版本"
        action-text="新建模板版本"
        @action="openCreate"
      />
    </div>

    <el-drawer
      v-model="drawer"
      :title="detail ? `Prompt 模板 v${detail.versionNo}` : 'Prompt 模板'"
      size="55%"
    >
      <AppSkeleton v-if="detailLoading" :rows="6" />
      <template v-else-if="detail">
        <div class="drawer-meta">
          <el-tag :type="detail.enabled === 1 ? 'success' : 'info'" size="small">
            {{ detail.enabled === 1 ? '启用中' : '未启用' }}
          </el-tag>
          <span class="dim">{{ formatDateTime(detail.createTime) }}</span>
        </div>
        <pre class="content">{{ detail.content }}</pre>
      </template>
    </el-drawer>

    <el-dialog v-model="createDialog" title="新建 Prompt 模板版本" width="620px">
      <el-alert
        type="warning"
        show-icon
        :closable="false"
        title="必须包含 {metrics}（指标清单）与 {parsed_text}（作业提取文本）占位符；新版本默认不启用。"
        class="mb"
      />
      <el-input v-model="draftContent" type="textarea" :rows="14" placeholder="粘贴完整评分 Prompt 模板…" />
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">创建版本</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { adminApi, type AdminPrompt, type AdminPromptDetail } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(false)
const error = ref<string | null>(null)
const list = ref<AdminPrompt[]>([])
const drawer = ref(false)
const detail = ref<AdminPromptDetail | null>(null)
const detailLoading = ref(false)
const createDialog = ref(false)
const creating = ref(false)
const draftContent = ref('')

async function load() {
  loading.value = true
  error.value = null
  try {
    list.value = await adminApi.prompts()
  } catch (e) {
    error.value = (e as { message?: string })?.message || 'Prompt 模板加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function view(id: number) {
  drawer.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await adminApi.promptDetail(id)
  } catch {
    drawer.value = false
  } finally {
    detailLoading.value = false
  }
}

function openCreate() {
  draftContent.value = ''
  createDialog.value = true
}

async function create() {
  if (!draftContent.value.trim()) {
    toast.warning('请粘贴模板内容')
    return
  }
  creating.value = true
  try {
    const created = await adminApi.createPrompt(draftContent.value.trim())
    toast.success(`已创建 v${created.versionNo}（未启用）`)
    createDialog.value = false
    await load()
  } catch {
    // 拦截器已提示（占位符缺失等）
  } finally {
    creating.value = false
  }
}

async function enable(p: AdminPrompt) {
  try {
    await ElMessageBox.confirm(
      `启用 v${p.versionNo} 后，旧启用版立即停用；后续 AI 评价全部使用该模板。`,
      '启用 Prompt 版本',
      { type: 'warning', confirmButtonText: '确认启用', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await adminApi.enablePrompt(p.id)
    toast.success(`已启用 v${p.versionNo}`)
    await load()
  } catch {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin-bottom: var(--aies-gap);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: var(--aies-gap);
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
}
.table-card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  overflow: hidden;
}
.prompt-table {
  width: 100%;
  border-collapse: collapse;
}
.prompt-table th {
  position: sticky;
  top: 0;
  z-index: 2;
  text-align: left;
  font-size: 12px;
  font-weight: 600;
  color: var(--aies-ink3);
  padding: 10px 14px;
  background: var(--aies-paper);
  border-bottom: 1px solid var(--aies-border);
}
.prompt-table td {
  padding: 10px 14px;
  border-bottom: 1px solid var(--aies-border);
  font-size: 13px;
  color: var(--aies-ink2);
}
.prompt-table tbody tr:last-child td {
  border-bottom: none;
}
.preview {
  display: inline-block;
  max-width: 520px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--aies-ink3);
  font-size: 12px;
}
.mono {
  font-family: var(--aies-mono, monospace);
}
.drawer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.content {
  margin: 0;
  padding: 14px;
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  font: 12px/1.8 var(--aies-mono, monospace);
  color: var(--aies-ink2);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 62vh;
  overflow: auto;
}
.mb {
  margin-bottom: 12px;
}
</style>
