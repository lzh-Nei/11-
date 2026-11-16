<template>
  <PageShell>
    <PageHeader title="任务管理" subtitle="教师视角：创建 / 发布 / 全状态跟踪（T-01/T-03，复核域 W8）" />

    <ScrollArea max-height="calc(100vh - 220px)" padded>
      <el-alert
        title="发布不可逆：DRAFT → PUBLISHED 后指标集与截止时间锁定，学生立即可见"
        type="warning"
        show-icon
        :closable="false"
        class="lock-tip"
      />

      <!-- 创建表单（TaskCreateForm）：Σweight=100 前端校验（后端 40001 兜底） -->
      <section class="form-card">
        <h3>创建新任务</h3>
        <el-form label-position="top" class="create-form">
          <div class="form-grid">
            <el-form-item label="任务标题" required>
              <el-input
                v-model="draft.title"
                placeholder="如：实训作业1 · Word 文档格式检查"
                maxlength="64"
              />
            </el-form-item>
            <el-form-item label="班级 ID" required>
              <el-input-number v-model="draft.classId" :min="1" controls-position="right" />
            </el-form-item>
          </div>
          <el-form-item label="任务说明">
            <el-input v-model="draft.description" type="textarea" :rows="2" maxlength="500" />
          </el-form-item>
          <el-form-item label="截止时间" required>
            <el-date-picker
              v-model="draft.deadline"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="选择截止时间"
            />
          </el-form-item>
          <el-form-item label="评分指标（权重合计必须 = 100）">
            <div class="metric-editor">
              <ScrollArea max-height="240px" padded>
                <div class="metric-rows">
                  <div v-for="(m, i) in draft.metrics" :key="i" class="metric-row">
                    <el-input v-model="m.metricName" placeholder="指标名（如：代码质量）" style="flex: 1" />
                    <el-input-number
                      v-model="m.weight"
                      :min="1"
                      :max="100"
                      controls-position="right"
                      style="width: 110px"
                    />
                    <span class="mono">%（第 {{ i + 1 }} 项）</span>
                    <el-button
                      text
                      type="danger"
                      :disabled="draft.metrics.length <= 1"
                      @click="draft.metrics.splice(i, 1)"
                      >删</el-button
                    >
                  </div>
                </div>
              </ScrollArea>
              <div class="metric-foot">
                <el-button
                  size="small"
                  plain
                  @click="
                    draft.metrics.push({ metricName: '', weight: 20, sortOrder: draft.metrics.length + 1 })
                  "
                >
                  + 添加指标
                </el-button>
                <span class="weight-sum" :class="{ bad: weightSum !== 100 }">Σ = {{ weightSum }}%</span>
              </div>
            </div>
          </el-form-item>
          <ConfirmAction
            title="创建任务"
            message="任务将以草稿状态创建（不发布，学生不可见）。确认创建？"
            confirm-text="创建草稿"
            @execute="onCreate"
          >
            <template #default="{ trigger, loading }">
              <el-button type="primary" :loading="loading" :disabled="!canCreate" @click="trigger"
                >创建任务（DRAFT）</el-button
              >
            </template>
          </ConfirmAction>
        </el-form>
      </section>

      <!-- 全状态列表 + 发布操作（PublishButton 语义：ConfirmAction 不可逆确认） -->
      <section class="list-block">
        <TaskFilterBar v-model="filter" @update:model-value="reload" />
        <AppSkeleton v-if="loading" :rows="2" :row-height="110" class="mt" />
        <ErrorState v-else-if="error" :message="error" @retry="load" />
        <EmptyState v-else-if="!tasks.length" description="暂无任务" />
        <ScrollArea v-else max-height="44vh" padded>
          <div class="card-list">
            <TaskCard v-for="t in tasks" :key="t.id" :task="t">
              <el-button v-if="t.status === 'DRAFT'" type="primary" size="small" @click="confirmPublish(t)">
                发布任务
              </el-button>
              <span v-else class="dim">{{ t.status === 'PUBLISHED' ? '已发布（指标锁定）' : '已关闭' }}</span>
            </TaskCard>
          </div>
        </ScrollArea>
        <div v-if="total > size" class="pager">
          <el-pagination
            v-model:current-page="page"
            :page-size="size"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="load"
          />
        </div>
      </section>
    </ScrollArea>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { taskApi, type TaskInfo, type TaskCreatePayload } from '@/api/task'
import { toast } from '@/utils/toast'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import TaskCard from '@/components/ui/TaskCard.vue'
import TaskFilterBar from '@/components/ui/TaskFilterBar.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'
import ConfirmAction from '@/components/ui/ConfirmAction.vue'
import { ElMessageBox } from 'element-plus'

const loading = ref(false)
const error = ref<string | null>(null)
const tasks = ref<TaskInfo[]>([])
const page = ref(1)
const size = 10
const total = ref(0)
const filter = ref('')

const draft = reactive<TaskCreatePayload>({
  title: '',
  description: '',
  classId: 1,
  deadline: '',
  metrics: [
    { metricName: '代码质量', weight: 40, sortOrder: 1 },
    { metricName: '功能正确性', weight: 40, sortOrder: 2 },
    { metricName: '文档规范', weight: 20, sortOrder: 3 }
  ]
})

const weightSum = computed(() => draft.metrics.reduce((a, m) => a + (m.weight || 0), 0))
const canCreate = computed(
  () => draft.title && draft.deadline && draft.classId > 0 && weightSum.value === 100
)

async function onCreate(done: () => void) {
  try {
    draft.metrics.forEach((m, i) => (m.sortOrder = i + 1))
    const created = await taskApi.create({ ...draft })
    toast.success(`任务已创建（草稿）：${created.title} · ID ${created.id}`)
    draft.title = ''
    draft.description = ''
    draft.deadline = ''
    reload()
  } catch (e) {
    toast.error((e as { message?: string })?.message || '创建失败', '请检查表单（Σweight 必须=100）')
  } finally {
    done()
  }
}

async function confirmPublish(t: TaskInfo) {
  try {
    await ElMessageBox.confirm(
      `「${t.title}」发布后指标集与截止时间锁定，学生立即可见，此操作不可逆。确认发布？`,
      '发布任务',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' }
    )
    await taskApi.publish(t.id)
    toast.success(`任务已发布：${t.title}`)
    load()
  } catch (e) {
    if ((e as { message?: string })?.message && String(e).includes('cancel')) return
    toast.error((e as { message?: string })?.message || '发布失败', '请重试')
  }
}

function reload() {
  page.value = 1
  load()
}
async function load() {
  loading.value = true
  error.value = null
  try {
    const status = filter.value || undefined
    const result = await taskApi.list(page.value, size, status)
    tasks.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '网络或服务暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.lock-tip {
  margin-bottom: var(--aies-gap);
}
.form-card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
  margin-bottom: var(--aies-gap-lg);
}
.form-card h3 {
  margin: 0 0 14px;
  font-size: 15px;
  color: var(--aies-ink);
}
.form-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--aies-gap);
}
.metric-editor {
  width: 100%;
}
.metric-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.mono,
.metric-foot .mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12px;
  color: var(--aies-ink3);
}
.metric-foot {
  display: flex;
  align-items: center;
  gap: 14px;
}
.weight-sum {
  font: 600 13px var(--aies-mono, monospace);
  color: var(--aies-ok, #15803d);
}
.weight-sum.bad {
  color: #b91c1c;
}
.list-block {
  display: flex;
  flex-direction: column;
  gap: var(--aies-gap);
}
.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--aies-gap);
}
.mt {
  margin-top: var(--aies-gap);
}
.pager {
  display: flex;
  justify-content: flex-end;
}
.dim {
  font-size: 12.5px;
  color: var(--aies-ink3);
}
</style>
